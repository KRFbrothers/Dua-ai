package com.dua.ai.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

interface GeneratedAudioOutput {
    fun play(bytes: ByteArray, onFinished: (Boolean) -> Unit)
    /** Release playback synchronously before returning. */
    fun stop()
}
data class GeneratedVoiceState(val activeId: String? = null, val phase: String = "idle", val status: String = "")
object GeneratedVoiceText {
    fun validate(text: String, completed: Boolean): String {
        require(completed) { "Only completed replies can use AI voice." }
        val selected = text.trim()
        require(selected.isNotBlank()) { "Choose a reply with text." }
        require(selected.length <= 2000) { "AI voice supports up to 2000 characters. Use device voice for this reply." }
        return selected
    }
}

/**
 * Main-dispatcher session: explicit taps only, with no automatic generation/retry/restart.
 * A reply is split into chunks by [plan] (older backends: one chunk of up to 2,000 characters). Chunk n + 1 is
 * fetched while chunk n plays, so long replies start quickly and play without waiting for the whole reply.
 */
class GeneratedVoiceSession(
    private val scope: CoroutineScope,
    private val generate: suspend (String) -> ByteArray,
    private val output: GeneratedAudioOutput,
    private val currentOwner: () -> String?,
    private val configured: () -> Boolean,
    private val plan: (String) -> List<String> = { listOf(GeneratedVoiceText.validate(it, true)) },
) {
    private val mutable = MutableStateFlow(GeneratedVoiceState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var playing: ByteArray? = null
    private var foreground = false
    private var online = false
    private var closed = false
    private var owner: String? = null
    private fun publish(id: String? = null, phase: String = "idle", status: String = "") { mutable.value = GeneratedVoiceState(id, phase, status) }
    fun setForeground(value: Boolean) { foreground = value; if (!value) stop() }
    fun setOnline(value: Boolean) { online = value; if (!value) stop(if (state.value.activeId != null) "Offline. Device voice remains available." else "") }
    fun accountChanged() { if (owner != null && owner != currentOwner()) stop("Sign-in changed. Start voice again when ready.") }
    fun stop(status: String = "") {
        generation++; job?.cancel(); job = null; owner = null
        try { output.stop() } finally { playing?.fill(0); playing = null; publish(status = status) }
    }
    fun close() { closed = true; stop() }
    private fun usable(ticket: Long, selectedOwner: String) = ticket == generation && !closed && foreground && online && currentOwner() == selectedOwner && configured()

    fun toggle(id: String, text: String, completed: Boolean) {
        if (closed) return
        if (state.value.activeId == id) { stop(); return }
        stop()
        val chunks = try {
            require(completed) { "Only completed replies can use AI voice." }
            plan(text.trim()).also { require(it.isNotEmpty()) { "Choose a reply with text." } }
        } catch (failure: IllegalArgumentException) { publish(phase = "error", status = failure.message.orEmpty()); return }
        val selectedOwner = currentOwner()
        if (!foreground || !online || selectedOwner == null || !configured()) { publish(phase = "error", status = "Open the app online, sign in and configure AI voice first. Device voice remains available."); return }
        owner = selectedOwner
        val ticket = generation
        publish(id, "generating", "Generating selected reply audio…")
        val pending = scope.launch(start = CoroutineStart.LAZY) {
            // Wipes audio that arrives after this playback was stopped or replaced.
            suspend fun fetch(chunk: String): ByteArray? {
                val received = generate(chunk)
                if (!usable(ticket, selectedOwner)) { received.fill(0); return null }
                return received
            }
            var next: Deferred<ByteArray?>? = null
            var received: ByteArray? = null
            try {
                for (index in chunks.indices) {
                    received = if (index == 0) fetch(chunks[0]) else next!!.await()
                    next = null
                    if (received == null) {
                        if (ticket == generation) stop("Voice availability changed. Start again when ready.")
                        return@launch
                    }
                    require(received.isNotEmpty() && received.size <= GeneratedAudioApi.MAX_BYTES) { "Invalid AI voice audio. Use device voice." }
                    if (index + 1 < chunks.size) next = async { fetch(chunks[index + 1]) }
                    playing = received
                    received = null
                    publish(id, "playing", if (chunks.size > 1) "Playing AI voice (${index + 1} of ${chunks.size})" else "Playing AI voice")
                    val successful = suspendCancellableCoroutine { continuation ->
                        output.play(playing!!) { done -> if (ticket == generation && continuation.isActive) continuation.resume(done) }
                    }
                    if (ticket != generation) return@launch
                    playing?.fill(0); playing = null
                    if (!successful) { next?.cancel(); stop("Voice playback interrupted. Try device voice."); return@launch }
                }
                if (ticket == generation) stop()
            } catch (cancelled: CancellationException) { received?.fill(0); next?.cancel(); throw cancelled }
            catch (_: Exception) {
                next?.cancel()
                if (ticket == generation) stop("AI voice is unavailable. Try device voice or retry explicitly.")
                received?.fill(0)
            } finally { if (ticket == generation) job = null }
        }
        job = pending; pending.start()
    }
}

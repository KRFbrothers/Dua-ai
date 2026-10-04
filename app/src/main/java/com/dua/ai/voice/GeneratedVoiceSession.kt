package com.dua.ai.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** Main-dispatcher session: explicit taps only, with no automatic generation/retry/restart. */
class GeneratedVoiceSession(
    private val scope: CoroutineScope,
    private val generate: suspend (String) -> ByteArray,
    private val output: GeneratedAudioOutput,
    private val currentOwner: () -> String?,
    private val configured: () -> Boolean
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
    fun toggle(id: String, text: String, completed: Boolean) {
        if (closed) return
        if (state.value.activeId == id) { stop(); return }
        stop()
        val selected = try { GeneratedVoiceText.validate(text, completed) } catch (failure: IllegalArgumentException) { publish(phase = "error", status = failure.message.orEmpty()); return }
        val selectedOwner = currentOwner()
        if (!foreground || !online || selectedOwner == null || !configured()) { publish(phase = "error", status = "Open the app online, sign in and configure AI voice first. Device voice remains available."); return }
        owner = selectedOwner
        val ticket = generation
        publish(id, "generating", "Generating selected reply audio…")
        val pending = scope.launch(start = CoroutineStart.LAZY) {
            var received: ByteArray? = null
            try {
                received = generate(selected)
                if (ticket != generation || closed || !foreground || !online || currentOwner() != selectedOwner || !configured()) {
                    received.fill(0)
                    if (ticket == generation) stop("Voice availability changed. Start again when ready.")
                    return@launch
                }
                require(received.isNotEmpty() && received.size <= GeneratedAudioApi.MAX_BYTES) { "Invalid AI voice audio. Use device voice." }
                playing = received
                publish(id, "playing", "Playing AI voice")
                output.play(received) { successful ->
                    if (ticket == generation) stop(if (successful) "" else "Voice playback interrupted. Try device voice.")
                }
            } catch (cancelled: CancellationException) { received?.fill(0); throw cancelled }
            catch (_: Exception) {
                if (ticket == generation) stop("AI voice is unavailable. Try device voice or retry explicitly.")
                received?.fill(0)
            } finally { if (ticket == generation) job = null }
        }
        job = pending; pending.start()
    }
}

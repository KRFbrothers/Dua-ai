package com.dua.ai.voice.offline

import android.os.Handler
import android.os.Looper
import com.dua.ai.voice.DictationEngine
import com.dua.ai.voice.VoiceDraft
import com.dua.ai.voice.VoiceInputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Offline recognizer seam (Vosk on phones, a fake in tests). Callbacks arrive on the main thread. */
interface OfflineRecognizer {
    interface Listener {
        fun onPartial(text: String)
        /** End of one utterance; listening continues. */
        fun onResult(text: String)
        /** After [stop]: the last words. */
        fun onFinal(text: String)
        fun onError(error: Exception)
    }
    fun start(listener: Listener)
    fun stop()
    fun cancel()
    fun close()
}

/**
 * Composer dictation with a downloaded Vosk model. Same [VoiceInputState] contract as the phone recognizer: the
 * transcript is only ever reviewed/merged into the draft by the composer; nothing is sent from here.
 */
class VoskVoiceInput(private val modelDir: File, private val factory: (File) -> OfflineRecognizer = ::VoskRecognizer,
    private val worker: Executor = Executors.newSingleThreadExecutor(), private val main: Handler = Handler(Looper.getMainLooper()),
    private val timeoutMs: Long = 60_000) : DictationEngine {
    private val mutableState = MutableStateFlow(VoiceInputState())
    override val state = mutableState.asStateFlow()
    private val mutableLevel = MutableStateFlow(0f)
    override val level = mutableLevel.asStateFlow()
    /** Vosk models are language-specific; the chosen model decides the language. */
    override var language: String = "auto"
    private var recognizer: OfflineRecognizer? = null
    private var generation = 0
    private var closed = false
    private var committed = ""
    private val timeout = Runnable { confirm() }

    override fun permissionDenied() {
        if (closed) return
        stop()
        mutableState.value = mutableState.value.copy(status = "Microphone permission denied. Allow it in app Settings or retry.", permissionDenied = true)
    }

    override fun start() {
        if (closed || mutableState.value.busy) return
        val token = ++generation
        committed = ""
        mutableState.value = VoiceInputState(busy = true, status = "Loading offline voice model…")
        worker.execute {
            val loaded = runCatching { recognizer ?: factory(modelDir) }
            main.post {
                val rec = loaded.getOrNull()
                if (rec != null) recognizer = rec
                if (closed) { closeRecognizer(); return@post }
                if (token != generation) return@post
                if (rec == null) { finish("Offline voice model could not load. Download it again in Settings > Voice."); return@post }
                mutableState.value = mutableState.value.copy(status = "Listening offline… Tap ✓ when done.")
                try { rec.start(listener(token)) }
                catch (_: SecurityException) { permissionDenied(); return@post }
                catch (_: Exception) { finish("Microphone unavailable. Tap mic to retry."); return@post }
                main.postDelayed(timeout, timeoutMs)
            }
        }
    }

    private fun words(last: String) = listOf(committed, last.trim()).filter { it.isNotBlank() }.joinToString(" ")

    private fun listener(token: Int) = object : OfflineRecognizer.Listener {
        override fun onPartial(text: String) {
            if (token != generation) return
            val partial = words(text)
            mutableLevel.value = if (partial != mutableState.value.partial) 0.7f else 0.15f
            mutableState.value = mutableState.value.copy(partial = partial)
        }
        override fun onResult(text: String) { if (token == generation) { committed = words(text); mutableState.value = mutableState.value.copy(partial = committed) } }
        override fun onFinal(text: String) {
            if (token != generation) return
            val all = words(text)
            try { finish(if (all.isBlank()) "No speech detected. Tap mic to retry." else "Review transcript, then use it in Chat.", VoiceDraft.merge("", all)) }
            catch (error: IllegalArgumentException) { finish(error.message ?: "Speech could not be read.") }
        }
        override fun onError(error: Exception) { if (token == generation) finish("Offline voice typing stopped. Tap mic to retry.") }
    }

    private fun finish(status: String, transcript: String = "") {
        generation++
        main.removeCallbacks(timeout)
        mutableLevel.value = 0f
        mutableState.value = VoiceInputState(transcript = transcript, status = status)
    }

    override fun confirm() {
        if (!mutableState.value.busy) return
        main.removeCallbacks(timeout)
        val rec = recognizer
        if (rec == null) { stop(); return }
        mutableState.value = mutableState.value.copy(status = "Processing speech…")
        runCatching { rec.stop() }.onFailure { finish("Review transcript, then use it in Chat.", mutableState.value.partial.trim()) }
    }

    override fun stop() {
        generation++
        main.removeCallbacks(timeout)
        runCatching { recognizer?.cancel() }
        mutableLevel.value = 0f
        mutableState.value = mutableState.value.copy(busy = false, partial = "",
            status = if (mutableState.value.transcript.isBlank()) "Stopped. Tap mic to retry." else "Review transcript, then use it in Chat.")
    }

    override fun close() { if (closed) return; closed = true; stop(); closeRecognizer() }

    private fun closeRecognizer() { val rec = recognizer ?: return; recognizer = null; worker.execute { runCatching { rec.close() } } }
}

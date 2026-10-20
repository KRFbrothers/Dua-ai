package com.dua.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceInputState(
    val busy: Boolean = false,
    /** True when the final transcript came from the recognizer's own end-of-speech (not a manual stop or confirm). */
    val endedBySilence: Boolean = false,
    val transcript: String = "",
    val partial: String = "",
    val status: String = "Tap mic to speak. Review before sending.",
    val permissionDenied: Boolean = false,
    /** SpeechRecognizer error code of the last session (null after a result or a manual stop). */
    val error: Int? = null,
)

/** Owns one foreground, user-started speech recognition session; never sends an AI request. */
class NativeVoiceInput(private val context: Context) : DictationEngine {
    private val mutableState = MutableStateFlow(VoiceInputState())
    override val state = mutableState.asStateFlow()
    private val mutableLevel = MutableStateFlow(0f)
    /** Microphone input level while listening, 0..1 (from onRmsChanged; drives the voice-mode glow). */
    override val level = mutableLevel.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var closed = false
    private var timeout: Runnable? = null
    private var confirming = false
    /** Recognition language from Voice settings: "auto" (device language) or a BCP-47 tag such as hi-IN or ur-PK. */
    override var language: String = "auto"

    override fun permissionDenied() {
        if (closed) return
        stop()
        mutableState.value = mutableState.value.copy(status = "Microphone permission denied. Allow it in app Settings or retry.", permissionDenied = true)
    }

    override fun start() {
        if (closed || mutableState.value.busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            mutableState.value = mutableState.value.copy(status = "Speech recognition unavailable on this device. Type in Chat instead.")
            return
        }
        release()
        confirming = false
        val token = ++generation
        mutableState.value = VoiceInputState(busy = true, status = "Starting microphone…")
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            fun update(value: VoiceInputState) { if (token == generation) mutableState.value = value }
            fun finish(status: String, transcript: String = "", denied: Boolean = false, error: Int? = null) {
                if (token != generation) return
                generation++
                release()
                mutableState.value = VoiceInputState(transcript = transcript, status = status, permissionDenied = denied, error = error)
            }
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { update(mutableState.value.copy(status = "Listening… Tap mic to stop.")) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { if (token == generation) mutableLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { update(mutableState.value.copy(status = "Processing speech…")) }
                override fun onError(error: Int) {
                    val status = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied. Allow it in app Settings or retry."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic to retry."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network error. Check connection and retry."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Tap mic to retry."
                        else -> "Voice input unavailable. Tap mic to retry or type in Chat."
                    }
                    val partial = mutableState.value.partial.trim()
                    if (confirming && partial.isNotEmpty()) finish("Review transcript, then use it in Chat.", partial)
                    else finish(status, denied = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, error = error)
                }
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val silence = !confirming
                    try { finish("Review transcript, then use it in Chat.", VoiceDraft.merge("", words.ifBlank { mutableState.value.partial })); if (token + 1 == generation) mutableState.value = mutableState.value.copy(endedBySilence = silence) }
                    catch (error: IllegalArgumentException) { finish(error.message ?: "Speech could not be read.") }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    update(mutableState.value.copy(partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()))
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            timeout = Runnable { if (token == generation) finish("Voice input timed out. Tap mic to retry.", error = SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }.also { handler.postDelayed(it, 30000) }
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechLanguages.locale(language).toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: SecurityException) { permissionDenied() }
        catch (_: Exception) { stop(); mutableState.value = mutableState.value.copy(status = "Voice input unavailable. Tap mic to retry or type in Chat.") }
    }

    /**
     * Stop listening but keep the words: asks the recognizer for its final result (delivered to [state] as the
     * transcript). Falls back to the last partial when the recognizer is gone.
     */
    override fun confirm() {
        val speech = recognizer
        if (!mutableState.value.busy || speech == null) return
        confirming = true
        val partial = mutableState.value.partial
        mutableState.value = mutableState.value.copy(status = "Processing speech…")
        runCatching { speech.stopListening() }.onFailure {
            generation++; release()
            mutableState.value = VoiceInputState(transcript = partial.trim(), status = "Review transcript, then use it in Chat.")
        }
    }

    override fun stop() {
        generation++
        release()
        mutableState.value = mutableState.value.copy(busy = false, partial = "", status = if (mutableState.value.transcript.isBlank()) "Stopped. Tap mic to retry." else "Review transcript, then use it in Chat.")
    }

    override fun close() { closed = true; stop() }

    private fun release() {
        mutableLevel.value = 0f
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val previous = recognizer
        recognizer = null
        runCatching { previous?.cancel() }
        runCatching { previous?.destroy() }
    }
}

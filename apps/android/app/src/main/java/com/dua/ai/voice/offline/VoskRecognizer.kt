package com.dua.ai.voice.offline

import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/** The only class that touches Vosk. Loading a model is slow (seconds): construct it off the main thread. */
class VoskRecognizer(dir: File) : OfflineRecognizer {
    private val model: Model
    private var service: SpeechService? = null
    private var recognizer: Recognizer? = null

    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        model = Model(dir.absolutePath)
    }

    override fun start(listener: OfflineRecognizer.Listener) {
        release()
        val rec = Recognizer(model, SAMPLE_RATE).also { recognizer = it }
        val speech = SpeechService(rec, SAMPLE_RATE).also { service = it }
        speech.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) = listener.onPartial(read(hypothesis, "partial"))
            override fun onResult(hypothesis: String?) = listener.onResult(read(hypothesis, "text"))
            override fun onFinalResult(hypothesis: String?) { listener.onFinal(read(hypothesis, "text")); release() }
            override fun onError(exception: Exception?) { listener.onError(exception ?: Exception("Vosk error")); release() }
            override fun onTimeout() { listener.onFinal(""); release() }
        })
    }

    override fun stop() { service?.stop() }
    override fun cancel() { service?.cancel(); release() }
    override fun close() { cancel(); model.close() }

    private fun release() {
        service?.let { runCatching { it.shutdown() } }; service = null
        recognizer?.let { runCatching { it.close() } }; recognizer = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000f
        fun read(json: String?, key: String): String = runCatching { JSONObject(json ?: "{}").optString(key) }.getOrDefault("")
    }
}

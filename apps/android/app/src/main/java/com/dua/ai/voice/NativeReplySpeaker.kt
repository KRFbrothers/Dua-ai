package com.dua.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class NativeReplySpeaker(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var closed = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focusHeld = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change ->
            if (change <= 0) session.stop("Playback interrupted. Start again.")
        }, handler).build()
    private var timeout: Runnable? = null
    private val mutableState = MutableStateFlow(ReadAloudState())
    val state = mutableState.asStateFlow()
    private val mutableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    /** Installed device voices for the selected language (empty until the engine is ready). */
    val voices = mutableVoices.asStateFlow()
    private var ready = false
    private var language = "auto"
    private var voiceName = ""
    private var speed = 1f
    private val session: ReadAloudSession = ReadAloudSession(object : SpeechOutput {
        override fun speak(text: String, utteranceId: String): Boolean {
            if (!focusHeld) {
                focusHeld = audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (!focusHeld) return false
            }
            timeout?.let(handler::removeCallbacks)
            timeout = Runnable { session.failed(utteranceId) }.also { handler.postDelayed(it, 45000) }
            return runCatching { engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS }.getOrDefault(false)
        }
        override fun stop() {
            timeout?.let(handler::removeCallbacks); timeout = null
            runCatching { engine?.stop() }
            if (focusHeld) { focusHeld = false; audioManager.abandonAudioFocusRequest(focus) }
        }
    }) { mutableState.value = it }

    init {
        try {
            engine = TextToSpeech(context.applicationContext) { result ->
                handler.post {
                    if (!closed) {
                        if (result != TextToSpeech.SUCCESS) session.availability(false, "Read aloud unavailable. Check device speech settings.")
                        else { ready = true; apply() }
                    }
                }
            }.apply {
                setAudioAttributes(attributes)
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { handler.post { if (!closed && utteranceId != null) session.completed(utteranceId) } }
                    @Deprecated("Platform callback")
                    override fun onError(utteranceId: String?) { handler.post { if (!closed && utteranceId != null) session.failed(utteranceId) } }
                    override fun onError(utteranceId: String?, errorCode: Int) { onError(utteranceId) }
                })
            }
        } catch (_: Exception) { session.availability(false, "Read aloud unavailable. Check device speech settings.") }
    }

    /** Device reply language ("auto" = device language), engine voice name ("" = engine default) and rate (0.5–2.0). */
    fun configure(language: String, voiceName: String, speed: Float) {
        if (closed) return
        val changed = language != this.language || voiceName != this.voiceName || speed != this.speed
        this.language = language; this.voiceName = voiceName; this.speed = speed.coerceIn(MIN_RATE, MAX_RATE)
        if (changed && ready) apply()
    }

    private fun apply() {
        val tts = engine ?: return
        val locale = SpeechLanguages.locale(language)
        var result = runCatching { tts.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        var status = ""
        if (result < 0 && language != "auto") {
            result = runCatching { tts.setLanguage(Locale.getDefault()) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            status = "${SpeechLanguages.name(language)} speech is not installed on this device; using the device language."
        }
        val installed = runCatching { tts.voices.orEmpty().filter { voice ->
            voice.locale.language == locale.language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
        }.sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name })) }.getOrDefault(emptyList())
        mutableVoices.value = installed.map { voice ->
            VoiceOption(voice.name, "${voice.locale.displayName} · ${voice.name.substringAfterLast("-x-").substringBefore('-').ifBlank { voice.name }}",
                listOfNotNull(if (voice.isNetworkConnectionRequired) "Uses network" else "On device", "quality ${voice.quality}").joinToString(" · "))
        }
        installed.firstOrNull { it.name == voiceName }?.let { voice -> runCatching { tts.voice = voice } }
        runCatching { tts.setSpeechRate(speed) }
        session.availability(result >= 0, if (result < 0) "Speech language/data unavailable. Check device speech settings." else status)
    }

    fun toggle(id: String, text: String) { if (!closed) session.toggle(id, text) }
    fun stop() { session.stop() }
    fun close() {
        closed = true
        session.stop()
        runCatching { engine?.shutdown() }
        engine = null
    }

    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
    }
}

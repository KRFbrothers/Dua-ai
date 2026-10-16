package com.dua.ai.voice

/** What the voice-mode loop is doing. */
enum class VoicePhase { Idle, Listening, Thinking, Speaking }

/** Why speech recognition stopped without a transcript. */
enum class RecognizerError { NoSpeech, Busy, Network, Permission, Unavailable }

data class VoiceModeState(
    val phase: VoicePhase = VoicePhase.Idle,
    val muted: Boolean = false,
    /** Short status for the user ("No connection…"); blank when there is nothing to say. */
    val notice: String = "",
    val closed: Boolean = false,
)

/**
 * The voice-mode turn loop: Idle → Listening → Thinking → Speaking → Listening …, plus mute, orb taps
 * (interrupt / start / pause), background pause and close. Pure Kotlin so it is unit-tested; the screen
 * wires [Actions] to SpeechRecognizer, the chat request and the reply voice.
 *
 * Every request and utterance carries a token; anything that arrives for an older token (a late reply after
 * an interrupt, a stale "done speaking") is ignored, so one turn can never start two.
 */
class VoiceModeSession(private val actions: Actions, private val silentRetries: Int = 2) {
    interface Actions {
        fun listen()
        fun stopListening()
        fun send(text: String, token: Int)
        fun cancelReply()
        fun speak(text: String, token: Int)
        fun stopSpeaking()
        fun render(state: VoiceModeState)
    }

    var state = VoiceModeState(); private set
    /** Token of the current request/utterance. */
    var token = 0; private set
    private var silent = 0

    private fun set(value: VoiceModeState) { state = value; actions.render(value) }
    private fun to(phase: VoicePhase, notice: String = "") = set(state.copy(phase = phase, notice = notice))
    private fun listenNow(notice: String = "") { to(VoicePhase.Listening, notice); actions.listen() }

    /** Start listening (on open, after the permission is granted, or when returning). */
    fun start() {
        if (state.closed || state.muted || state.phase != VoicePhase.Idle) return
        silent = 0
        listenNow()
    }

    /** Final recognizer text. Blank counts as no speech. */
    fun transcript(text: String) {
        if (state.phase != VoicePhase.Listening) return
        val words = text.trim()
        if (words.isEmpty()) { recognizerError(RecognizerError.NoSpeech); return }
        if (words.length > MAX_CHARS) { to(VoicePhase.Idle, "That was too long for one turn. Tap the orb and try a shorter message."); return }
        silent = 0
        token++
        to(VoicePhase.Thinking)
        actions.send(words, token)
    }

    /** The recognizer stopped without text. No speech (and a busy recognizer) re-listen quietly a couple of times. */
    fun recognizerError(error: RecognizerError) {
        if (state.phase != VoicePhase.Listening) return
        when (error) {
            RecognizerError.NoSpeech, RecognizerError.Busy -> {
                silent++
                if (silent > silentRetries) { silent = 0; to(VoicePhase.Idle, "Tap the orb when you're ready to talk.") }
                else listenNow()
            }
            RecognizerError.Network -> to(VoicePhase.Idle, "No connection to the speech service. Check your network, then tap the orb.")
            RecognizerError.Permission -> to(VoicePhase.Idle, "Dua needs microphone access to hear you.")
            RecognizerError.Unavailable -> to(VoicePhase.Idle, "Voice input isn't available right now. Tap the orb to try again.")
        }
    }

    fun reply(text: String, token: Int) {
        if (token != this.token || state.phase != VoicePhase.Thinking) return
        if (text.isBlank()) { to(VoicePhase.Idle, "This reply has no text to read. Open the chat to see it."); return }
        to(VoicePhase.Speaking)
        actions.speak(text, token)
    }

    fun replyFailed(reason: String, token: Int) {
        if (token != this.token || state.phase != VoicePhase.Thinking) return
        to(VoicePhase.Idle, reason)
    }

    /** The reply finished playing: listen again (or wait if muted). */
    fun spoken(token: Int) {
        if (token != this.token || state.phase != VoicePhase.Speaking) return
        if (state.muted) to(VoicePhase.Idle) else listenNow()
    }

    /** Playback failed; the reply stays in the captions and the chat. */
    fun speakFailed(reason: String, token: Int) {
        if (token != this.token || state.phase != VoicePhase.Speaking) return
        if (state.muted) to(VoicePhase.Idle, reason) else listenNow(reason)
    }

    /** Orb tap: interrupt speaking or thinking and listen, pause listening, or start listening (unmuting). */
    fun tapOrb() {
        if (state.closed) return
        when (state.phase) {
            VoicePhase.Idle -> { if (state.muted) set(state.copy(muted = false)); silent = 0; listenNow() }
            VoicePhase.Listening -> { actions.stopListening(); to(VoicePhase.Idle, "Paused. Tap the orb to talk.") }
            VoicePhase.Thinking -> { token++; actions.cancelReply(); afterInterrupt() }
            VoicePhase.Speaking -> { token++; actions.stopSpeaking(); afterInterrupt() }
        }
    }

    /** Ignore late detector callbacks from an older utterance, muted mic, or a paused screen. */
    fun bargeIn(utteranceToken: Int) {
        if (!state.closed && !state.muted && state.phase == VoicePhase.Speaking && utteranceToken == token) tapOrb()
    }

    private fun afterInterrupt() { silent = 0; if (state.muted) to(VoicePhase.Idle) else listenNow() }

    /** Mic mute: stops listening now; while muted Dua still finishes a reply but doesn't listen afterwards. */
    fun setMuted(muted: Boolean) {
        if (state.closed || muted == state.muted) return
        if (muted) {
            if (state.phase == VoicePhase.Listening) { actions.stopListening(); set(state.copy(phase = VoicePhase.Idle, muted = true, notice = "")) }
            else set(state.copy(muted = true))
        } else {
            set(state.copy(muted = false, notice = ""))
            if (state.phase == VoicePhase.Idle) { silent = 0; listenNow() }
        }
    }

    /** App went to the background (or the screen turned off): stop everything, keep the screen open. */
    fun pause(reason: String = "Paused. Tap the orb to continue.") {
        if (state.closed || state.phase == VoicePhase.Idle) return
        stopAll()
        to(VoicePhase.Idle, reason)
    }

    fun close() {
        if (state.closed) return
        stopAll()
        set(state.copy(phase = VoicePhase.Idle, notice = "", closed = true))
    }

    private fun stopAll() {
        token++
        when (state.phase) {
            VoicePhase.Listening -> actions.stopListening()
            VoicePhase.Thinking -> actions.cancelReply()
            VoicePhase.Speaking -> actions.stopSpeaking()
            VoicePhase.Idle -> Unit
        }
    }

    companion object { const val MAX_CHARS = 8000 }
}

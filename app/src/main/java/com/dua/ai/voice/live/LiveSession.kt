package com.dua.ai.voice.live

/** One line of the live conversation: the user's words or Dua's spoken reply. */
data class LiveCaption(val id: String, val user: Boolean, val text: String, val final: Boolean, val interrupted: Boolean = false)

enum class LivePhase { IDLE, CONNECTING, LISTENING, USER_SPEAKING, THINKING, SPEAKING, ENDED, FAILED }

data class LiveState(
    val phase: LivePhase = LivePhase.IDLE,
    val captions: List<LiveCaption> = emptyList(),
    val muted: Boolean = false,
    val elapsedSeconds: Int = 0,
    val maxSeconds: Int = 0,
    val status: String = "",
    val level: Float = 0f,
    val voice: String = "",
) {
    val active: Boolean get() = phase !in setOf(LivePhase.IDLE, LivePhase.ENDED, LivePhase.FAILED)
    val remainingSeconds: Int get() = (maxSeconds - elapsedSeconds).coerceAtLeast(0)
}

/** What the session should do after an input (executed by the controller, tested here as data). */
sealed interface LiveEffect {
    data class Send(val message: String) : LiveEffect
    data class Play(val pcm: ByteArray) : LiveEffect
    /** Drop queued assistant audio at once (barge-in). */
    data object FlushPlayback : LiveEffect
    data class Close(val reason: String) : LiveEffect
}

/**
 * Pure state machine for one live voice session. It keeps the captions in conversation order, turns server
 * events into UI state and effects, handles barge-in (the user talking over Dua drops queued audio), muting
 * and the client-side session timer. [transcript] is what gets saved to the chat when the session ends.
 */
class LiveSession(private val maxSeconds: Int, voice: String = "") {
    var state = LiveState(maxSeconds = maxSeconds, voice = voice)
        private set
    private var speakingResponse: String? = null
    /** Responses the user talked over; their late audio and text are ignored. */
    private val interrupted = mutableSetOf<String>()
    private var warned = false

    fun connecting(): List<LiveEffect> { state = state.copy(phase = LivePhase.CONNECTING, status = "Connecting…"); return emptyList() }

    /** WebSocket is open: configure the session (instructions, voice, VAD, audio formats). */
    fun opened(sessionUpdate: String): List<LiveEffect> {
        if (!state.active) return emptyList()
        state = state.copy(status = "Starting…")
        return listOf(LiveEffect.Send(sessionUpdate))
    }

    /** Microphone audio; sent unless muted or not yet listening. */
    fun microphone(pcm: ByteArray, length: Int): List<LiveEffect> {
        if (state.muted || state.phase !in setOf(LivePhase.LISTENING, LivePhase.USER_SPEAKING, LivePhase.THINKING, LivePhase.SPEAKING)) return emptyList()
        if (state.phase != LivePhase.SPEAKING) state = state.copy(level = RealtimeProtocol.level(pcm, length))
        return listOf(LiveEffect.Send(RealtimeProtocol.appendAudio(pcm, length)))
    }

    fun setMuted(muted: Boolean): List<LiveEffect> {
        if (!state.active) return emptyList()
        state = state.copy(muted = muted, level = 0f, status = if (muted) "Muted. Tap the mic to talk again." else listeningStatus())
        // Drop half-sent speech so a muted cough is not answered.
        return if (muted) listOf(LiveEffect.Send(RealtimeProtocol.clearInput())) else emptyList()
    }

    /** Called once a second with the time since the session connected. */
    fun tick(elapsedSeconds: Int): List<LiveEffect> {
        if (!state.active) return emptyList()
        state = state.copy(elapsedSeconds = elapsedSeconds)
        if (maxSeconds > 0 && elapsedSeconds >= maxSeconds) return end("Session time is up (${maxSeconds / 60} min). Start again if you have minutes left today.")
        if (maxSeconds > 90 && !warned && maxSeconds - elapsedSeconds <= 60) { warned = true; state = state.copy(status = "One minute left in this session.") }
        return emptyList()
    }

    fun end(reason: String = "Session ended."): List<LiveEffect> {
        if (!state.active) return emptyList()
        state = state.copy(phase = LivePhase.ENDED, status = reason, level = 0f, captions = state.captions.map { if (!it.user && !it.final) it.copy(final = true) else it })
        return listOf(LiveEffect.FlushPlayback, LiveEffect.Close(reason))
    }

    fun failed(message: String): List<LiveEffect> {
        if (!state.active) return emptyList()
        state = state.copy(phase = LivePhase.FAILED, status = message, level = 0f)
        return listOf(LiveEffect.FlushPlayback, LiveEffect.Close(message))
    }

    /** Assistant audio finished playing on the device. */
    fun playbackDrained() {
        if (state.phase == LivePhase.SPEAKING && speakingResponse == null) state = state.copy(phase = LivePhase.LISTENING, level = 0f, status = listeningStatus())
    }

    fun outputLevel(level: Float) { if (state.phase == LivePhase.SPEAKING) state = state.copy(level = level) }

    fun server(event: RealtimeEvent): List<LiveEffect> {
        if (!state.active) return emptyList()
        return when (event) {
            is RealtimeEvent.SessionUpdated -> {
                if (state.phase == LivePhase.CONNECTING) state = state.copy(phase = LivePhase.LISTENING, status = listeningStatus())
                emptyList()
            }
            is RealtimeEvent.SpeechStarted -> {
                val interrupting = state.phase == LivePhase.SPEAKING || speakingResponse != null
                speakingResponse?.let(interrupted::add)
                speakingResponse = null
                state = state.copy(phase = LivePhase.USER_SPEAKING, status = if (interrupting) "Go ahead, I'm listening." else "Listening…",
                    captions = (if (interrupting) state.captions.map { if (!it.user && !it.final) it.copy(final = true, interrupted = true) else it } else state.captions)
                        .let { upsert(it, LiveCaption(userId(event.itemId), true, "", false)) })
                if (interrupting) listOf(LiveEffect.FlushPlayback) else emptyList()
            }
            is RealtimeEvent.SpeechStopped -> { state = state.copy(phase = LivePhase.THINKING, status = "Thinking…", level = 0f); emptyList() }
            is RealtimeEvent.InputTranscript -> {
                val existing = state.captions.firstOrNull { it.id == userId(event.itemId) }
                if (existing?.final == true && !event.final) return emptyList()
                state = state.copy(captions = upsert(state.captions, LiveCaption(userId(event.itemId), true, event.text.trim(), event.final)))
                emptyList()
            }
            is RealtimeEvent.ResponseCreated -> {
                speakingResponse = event.responseId
                if (state.phase != LivePhase.USER_SPEAKING) state = state.copy(phase = LivePhase.THINKING, status = "Thinking…")
                emptyList()
            }
            is RealtimeEvent.OutputAudio -> {
                if (!accept(event.responseId)) return emptyList()
                state = state.copy(phase = LivePhase.SPEAKING, status = "Speaking… talk anytime to interrupt.")
                listOf(LiveEffect.Play(event.pcm))
            }
            is RealtimeEvent.OutputTranscriptDelta -> {
                if (!accept(event.responseId)) return emptyList()
                val id = replyId(event.responseId)
                val current = state.captions.firstOrNull { it.id == id }
                if (current?.final == true) return emptyList()
                state = state.copy(captions = upsert(state.captions, LiveCaption(id, false, (current?.text.orEmpty() + event.delta), false)))
                emptyList()
            }
            is RealtimeEvent.OutputTranscriptDone -> {
                if (event.responseId in interrupted) return emptyList()
                val id = replyId(event.responseId)
                val current = state.captions.firstOrNull { it.id == id }
                if (current?.interrupted == true) return emptyList()
                state = state.copy(captions = upsert(state.captions, LiveCaption(id, false, event.transcript.ifBlank { current?.text.orEmpty() }.trim(), true)))
                emptyList()
            }
            is RealtimeEvent.ResponseDone -> {
                if (event.responseId == speakingResponse || event.responseId.isEmpty()) speakingResponse = null
                state = state.copy(captions = state.captions.map { if (it.id == replyId(event.responseId) && !it.final) it.copy(final = true, interrupted = event.status == "cancelled") else it })
                if (state.phase == LivePhase.THINKING) state = state.copy(phase = LivePhase.LISTENING, status = listeningStatus())
                emptyList()
            }
            is RealtimeEvent.Error -> {
                // Configuration errors keep the connection open; only report them.
                state = state.copy(status = event.message)
                emptyList()
            }
            else -> emptyList()
        }
    }

    /** Finished turns in order, for saving into the chat. Empty and placeholder captions are dropped. */
    fun transcript(): List<LiveCaption> = state.captions.filter { it.text.isNotBlank() }

    /** Output belongs to the current reply (or starts one when response.created was not seen). */
    private fun accept(responseId: String): Boolean {
        if (responseId in interrupted || state.phase == LivePhase.USER_SPEAKING) return false
        if (speakingResponse == null) speakingResponse = responseId
        return responseId.isEmpty() || responseId == speakingResponse
    }
    private fun listeningStatus() = if (state.muted) "Muted. Tap the mic to talk again." else "Listening… just start talking."
    private fun userId(itemId: String) = "user:" + itemId.ifBlank { "turn-" + state.captions.size }
    private fun replyId(responseId: String) = "dua:" + responseId.ifBlank { "turn-" + state.captions.size }
    private fun upsert(list: List<LiveCaption>, caption: LiveCaption): List<LiveCaption> {
        val index = list.indexOfFirst { it.id == caption.id }
        val next = if (index < 0) list + caption else list.toMutableList().also { it[index] = caption.copy(interrupted = caption.interrupted || list[index].interrupted) }
        return next.takeLast(MAX_CAPTIONS)
    }

    companion object { const val MAX_CAPTIONS = 200 }
}

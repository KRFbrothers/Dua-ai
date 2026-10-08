package com.dua.ai.voice.live

import org.json.JSONObject
import java.util.Base64

/**
 * Events of the xAI Speech to Speech (realtime) WebSocket API that Dua uses, as documented on docs.x.ai
 * (October 2026). Audio is PCM16 little-endian, mono, 24 kHz, base64 in JSON ("json" transport).
 */
sealed interface RealtimeEvent {
    data class SessionCreated(val id: String) : RealtimeEvent
    data object SessionUpdated : RealtimeEvent
    data class ConversationCreated(val id: String) : RealtimeEvent
    /** Server VAD heard the user start talking: stop playing the assistant (barge-in). */
    data class SpeechStarted(val itemId: String) : RealtimeEvent
    data class SpeechStopped(val itemId: String) : RealtimeEvent
    /** User speech text. [final] = `…input_audio_transcription.completed`; otherwise the cumulative `…updated` text. */
    data class InputTranscript(val itemId: String, val text: String, val final: Boolean) : RealtimeEvent
    data class ResponseCreated(val responseId: String) : RealtimeEvent
    data class OutputAudio(val responseId: String, val pcm: ByteArray) : RealtimeEvent {
        override fun equals(other: Any?) = other is OutputAudio && other.responseId == responseId && other.pcm.contentEquals(pcm)
        override fun hashCode() = 31 * responseId.hashCode() + pcm.contentHashCode()
    }
    data class OutputTranscriptDelta(val responseId: String, val delta: String) : RealtimeEvent
    data class OutputTranscriptDone(val responseId: String, val transcript: String) : RealtimeEvent
    data class OutputAudioDone(val responseId: String) : RealtimeEvent
    data class ResponseDone(val responseId: String, val status: String) : RealtimeEvent
    data class Error(val message: String, val code: String) : RealtimeEvent
    /** Any other event (ignored). */
    data class Other(val type: String) : RealtimeEvent
}

object RealtimeProtocol {
    const val SAMPLE_RATE = 24_000
    /** Biggest single server message accepted (audio deltas are small; this bounds memory). */
    const val MAX_MESSAGE_CHARS = 4 * 1024 * 1024

    fun sessionUpdate(session: JSONObject): String = JSONObject().put("type", "session.update").put("session", session).toString()
    fun appendAudio(pcm: ByteArray, length: Int = pcm.size): String =
        JSONObject().put("type", "input_audio_buffer.append").put("audio", Base64.getEncoder().encodeToString(if (length == pcm.size) pcm else pcm.copyOf(length))).toString()
    fun clearInput(): String = JSONObject().put("type", "input_audio_buffer.clear").toString()
    fun cancelResponse(): String = JSONObject().put("type", "response.cancel").toString()

    fun decode(raw: String): RealtimeEvent {
        if (raw.length > MAX_MESSAGE_CHARS) return RealtimeEvent.Error("Live voice message was too large.", "too_large")
        val json = try { JSONObject(raw) } catch (_: Exception) { return RealtimeEvent.Other("invalid") }
        val type = json.optString("type")
        val responseId = json.optString("response_id")
        val itemId = json.optString("item_id")
        return when (type) {
            "session.created" -> RealtimeEvent.SessionCreated(json.optJSONObject("session")?.optString("id").orEmpty())
            "session.updated" -> RealtimeEvent.SessionUpdated
            "conversation.created" -> RealtimeEvent.ConversationCreated(json.optJSONObject("conversation")?.optString("id").orEmpty())
            "input_audio_buffer.speech_started" -> RealtimeEvent.SpeechStarted(itemId)
            "input_audio_buffer.speech_stopped" -> RealtimeEvent.SpeechStopped(itemId)
            "conversation.item.input_audio_transcription.updated" -> RealtimeEvent.InputTranscript(itemId, json.optString("transcript"), false)
            "conversation.item.input_audio_transcription.completed" -> RealtimeEvent.InputTranscript(itemId, json.optString("transcript"), true)
            "response.created" -> RealtimeEvent.ResponseCreated(json.optJSONObject("response")?.optString("id")?.ifBlank { null } ?: responseId)
            "response.output_audio.delta", "response.audio.delta" -> {
                val pcm = try { Base64.getDecoder().decode(json.optString("delta")) } catch (_: IllegalArgumentException) { return RealtimeEvent.Other("invalid_audio") }
                if (pcm.size % 2 != 0) RealtimeEvent.Other("invalid_audio") else RealtimeEvent.OutputAudio(responseId, pcm)
            }
            "response.output_audio_transcript.delta", "response.audio_transcript.delta" -> RealtimeEvent.OutputTranscriptDelta(responseId, json.optString("delta"))
            "response.output_audio_transcript.done", "response.audio_transcript.done" -> RealtimeEvent.OutputTranscriptDone(responseId, json.optString("transcript"))
            "response.output_audio.done", "response.audio.done" -> RealtimeEvent.OutputAudioDone(responseId)
            "response.done" -> json.optJSONObject("response").let { RealtimeEvent.ResponseDone(it?.optString("id")?.ifBlank { null } ?: responseId, it?.optString("status").orEmpty()) }
            "error" -> json.optJSONObject("error").let { error ->
                RealtimeEvent.Error(error?.optString("message")?.take(300)?.ifBlank { null } ?: "Live voice error.", error?.optString("code")?.take(80).orEmpty())
            }
            else -> RealtimeEvent.Other(type.take(80))
        }
    }

    /** Root-mean-square level of PCM16 audio, 0..1 (for the orb animation). */
    fun level(pcm: ByteArray, length: Int = pcm.size): Float {
        val samples = length / 2
        if (samples == 0) return 0f
        var sum = 0.0
        for (i in 0 until samples) {
            val sample = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xff)).toShort().toInt()
            sum += sample.toDouble() * sample
        }
        return (Math.sqrt(sum / samples) / 32768.0 * 4).coerceIn(0.0, 1.0).toFloat()
    }
}

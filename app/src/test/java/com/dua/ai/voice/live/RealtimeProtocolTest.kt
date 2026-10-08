package com.dua.ai.voice.live

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class RealtimeProtocolTest {
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    @Test fun clientEventsUseTheDocumentedShapes() {
        val session = JSONObject().put("voice", "eve").put("turn_detection", JSONObject().put("type", "server_vad"))
        val update = JSONObject(RealtimeProtocol.sessionUpdate(session))
        assertEquals("session.update", update.getString("type"))
        assertEquals("eve", update.getJSONObject("session").getString("voice"))
        val append = JSONObject(RealtimeProtocol.appendAudio(byteArrayOf(1, 2, 3, 4, 5, 6), 4))
        assertEquals("input_audio_buffer.append", append.getString("type"))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), Base64.getDecoder().decode(append.getString("audio")))
        assertEquals("input_audio_buffer.clear", JSONObject(RealtimeProtocol.clearInput()).getString("type"))
        assertEquals("response.cancel", JSONObject(RealtimeProtocol.cancelResponse()).getString("type"))
    }

    @Test fun serverEventsDecode() {
        val pcm = byteArrayOf(0, 1, 2, 3)
        val cases = mapOf(
            """{"type":"session.created","session":{"id":"s1"}}""" to RealtimeEvent.SessionCreated("s1"),
            """{"type":"session.updated","session":{}}""" to RealtimeEvent.SessionUpdated,
            """{"type":"conversation.created","conversation":{"id":"c1"}}""" to RealtimeEvent.ConversationCreated("c1"),
            """{"type":"input_audio_buffer.speech_started","item_id":"i1"}""" to RealtimeEvent.SpeechStarted("i1"),
            """{"type":"input_audio_buffer.speech_stopped","item_id":"i1"}""" to RealtimeEvent.SpeechStopped("i1"),
            """{"type":"conversation.item.input_audio_transcription.updated","item_id":"i1","transcript":"namaste"}""" to RealtimeEvent.InputTranscript("i1", "namaste", false),
            """{"type":"conversation.item.input_audio_transcription.completed","item_id":"i1","transcript":"namaste Dua"}""" to RealtimeEvent.InputTranscript("i1", "namaste Dua", true),
            """{"type":"response.created","response":{"id":"r1"}}""" to RealtimeEvent.ResponseCreated("r1"),
            """{"type":"response.output_audio.delta","response_id":"r1","delta":"${b64(pcm)}"}""" to RealtimeEvent.OutputAudio("r1", pcm),
            """{"type":"response.output_audio_transcript.delta","response_id":"r1","delta":"Hel"}""" to RealtimeEvent.OutputTranscriptDelta("r1", "Hel"),
            """{"type":"response.output_audio_transcript.done","response_id":"r1","transcript":"Hello"}""" to RealtimeEvent.OutputTranscriptDone("r1", "Hello"),
            """{"type":"response.output_audio.done","response_id":"r1"}""" to RealtimeEvent.OutputAudioDone("r1"),
            """{"type":"response.done","response":{"id":"r1","status":"completed"}}""" to RealtimeEvent.ResponseDone("r1", "completed"),
            """{"type":"error","error":{"message":"Invalid voice","code":"invalid_value"}}""" to RealtimeEvent.Error("Invalid voice", "invalid_value"),
            """{"type":"rate_limits.updated"}""" to RealtimeEvent.Other("rate_limits.updated"),
        )
        for ((raw, expected) in cases) assertEquals(raw, expected, RealtimeProtocol.decode(raw))
    }

    @Test fun malformedMessagesAreIgnoredSafely() {
        assertEquals(RealtimeEvent.Other("invalid"), RealtimeProtocol.decode("not json"))
        assertEquals(RealtimeEvent.Other("invalid_audio"), RealtimeProtocol.decode("""{"type":"response.output_audio.delta","delta":"%%%"}"""))
        assertEquals(RealtimeEvent.Other("invalid_audio"), RealtimeProtocol.decode("""{"type":"response.output_audio.delta","delta":"${b64(byteArrayOf(1, 2, 3))}"}"""))
        assertTrue(RealtimeProtocol.decode("x".repeat(RealtimeProtocol.MAX_MESSAGE_CHARS + 1)) is RealtimeEvent.Error)
        assertEquals("Live voice error.", (RealtimeProtocol.decode("""{"type":"error"}""") as RealtimeEvent.Error).message)
    }

    @Test fun levelIsZeroForSilenceAndRisesWithLoudness() {
        assertEquals(0f, RealtimeProtocol.level(ByteArray(960)), 0f)
        val loud = ByteArray(960).also { for (i in it.indices step 2) { it[i] = 0; it[i + 1] = if (i % 4 == 0) 0x40 else 0xC0.toByte() } }
        assertTrue(RealtimeProtocol.level(loud) > 0.9f)
        assertEquals(0f, RealtimeProtocol.level(ByteArray(1)), 0f)
    }
}

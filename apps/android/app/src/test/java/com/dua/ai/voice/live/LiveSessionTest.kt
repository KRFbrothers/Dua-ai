package com.dua.ai.voice.live

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveSessionTest {
    private fun listening(max: Int = 600): LiveSession = LiveSession(max, "eve").also {
        it.connecting(); it.opened("{}"); it.server(RealtimeEvent.SessionUpdated)
    }
    private fun played(effects: List<LiveEffect>) = effects.filterIsInstance<LiveEffect.Play>().map { it.pcm.toList() }
    private fun sent(effects: List<LiveEffect>) = effects.filterIsInstance<LiveEffect.Send>().map { JSONObject(it.message).getString("type") }

    @Test fun connectsConfiguresThenListens() {
        val session = LiveSession(600)
        assertEquals(LivePhase.IDLE, session.state.phase)
        session.connecting()
        assertEquals(LivePhase.CONNECTING, session.state.phase)
        assertEquals(listOf(LiveEffect.Send("{\"type\":\"session.update\"}")), session.opened("{\"type\":\"session.update\"}"))
        assertTrue("no audio before the session is configured", session.microphone(ByteArray(4), 4).isEmpty())
        session.server(RealtimeEvent.SessionUpdated)
        assertEquals(LivePhase.LISTENING, session.state.phase)
        assertEquals(listOf("input_audio_buffer.append"), sent(session.microphone(ByteArray(4), 4)))
    }

    @Test fun aFullTurnBuildsCaptionsInOrderAndPlaysAudio() {
        val session = listening()
        session.server(RealtimeEvent.SpeechStarted("i1"))
        assertEquals(LivePhase.USER_SPEAKING, session.state.phase)
        session.server(RealtimeEvent.InputTranscript("i1", "Kal ka", false))
        session.server(RealtimeEvent.SpeechStopped("i1"))
        assertEquals(LivePhase.THINKING, session.state.phase)
        session.server(RealtimeEvent.ResponseCreated("r1"))
        session.server(RealtimeEvent.OutputTranscriptDelta("r1", "Kal "))
        val play = session.server(RealtimeEvent.OutputAudio("r1", byteArrayOf(1, 2)))
        assertEquals(listOf(listOf<Byte>(1, 2)), played(play))
        assertEquals(LivePhase.SPEAKING, session.state.phase)
        session.server(RealtimeEvent.InputTranscript("i1", "Kal ka mausam?", true))
        session.server(RealtimeEvent.OutputTranscriptDelta("r1", "dhoop rahegi."))
        session.server(RealtimeEvent.OutputTranscriptDone("r1", "Kal dhoop rahegi."))
        session.server(RealtimeEvent.ResponseDone("r1", "completed"))
        assertEquals("still speaking until playback drains", LivePhase.SPEAKING, session.state.phase)
        session.playbackDrained()
        assertEquals(LivePhase.LISTENING, session.state.phase)
        assertEquals(listOf(true to "Kal ka mausam?", false to "Kal dhoop rahegi."), session.transcript().map { it.user to it.text })
        assertTrue(session.transcript().all { it.final })
    }

    @Test fun talkingOverDuaFlushesAudioAndIgnoresTheRestOfThatReply() {
        val session = listening()
        session.server(RealtimeEvent.ResponseCreated("r1"))
        session.server(RealtimeEvent.OutputTranscriptDelta("r1", "A long answer"))
        session.server(RealtimeEvent.OutputAudio("r1", byteArrayOf(1, 1)))
        val bargeIn = session.server(RealtimeEvent.SpeechStarted("i2"))
        assertEquals(listOf<LiveEffect>(LiveEffect.FlushPlayback), bargeIn)
        assertEquals(LivePhase.USER_SPEAKING, session.state.phase)
        assertTrue(session.server(RealtimeEvent.OutputAudio("r1", byteArrayOf(2, 2))).isEmpty())
        session.server(RealtimeEvent.OutputTranscriptDelta("r1", " that keeps going"))
        session.server(RealtimeEvent.OutputTranscriptDone("r1", "A long answer that keeps going"))
        val reply = session.transcript().first { !it.user }
        assertEquals("A long answer", reply.text); assertTrue(reply.interrupted)
        session.server(RealtimeEvent.SpeechStopped("i2"))
        session.server(RealtimeEvent.ResponseCreated("r2"))
        assertEquals(listOf(listOf<Byte>(3, 3)), played(session.server(RealtimeEvent.OutputAudio("r2", byteArrayOf(3, 3)))))
    }

    @Test fun muteStopsSendingAndClearsHalfSentSpeech() {
        val session = listening()
        assertEquals(listOf("input_audio_buffer.clear"), sent(session.setMuted(true)))
        assertTrue(session.state.muted)
        assertTrue(session.microphone(ByteArray(4), 4).isEmpty())
        session.setMuted(false)
        assertEquals(listOf("input_audio_buffer.append"), sent(session.microphone(ByteArray(4), 4)))
    }

    @Test fun clientTimerWarnsThenEndsTheSession() {
        val session = listening(max = 300)
        assertTrue(session.tick(200).isEmpty())
        session.tick(241); assertEquals("One minute left in this session.", session.state.status)
        assertEquals(59, session.state.remainingSeconds)
        val end = session.tick(300)
        assertTrue(end.last() is LiveEffect.Close)
        assertEquals(LivePhase.ENDED, session.state.phase)
        assertTrue("ended sessions ignore further input", session.server(RealtimeEvent.OutputAudio("r", byteArrayOf(1, 1))).isEmpty())
        assertTrue(session.end().isEmpty())
    }

    @Test fun failuresAndErrorsKeepTheTranscript() {
        val session = listening()
        session.server(RealtimeEvent.InputTranscript("i1", "Hello", true))
        session.server(RealtimeEvent.Error("Invalid voice", "invalid_value"))
        assertEquals(LivePhase.LISTENING, session.state.phase)
        assertEquals("Invalid voice", session.state.status)
        val failed = session.failed("Connection lost.")
        assertEquals(listOf(LiveEffect.FlushPlayback, LiveEffect.Close("Connection lost.")), failed)
        assertEquals(LivePhase.FAILED, session.state.phase)
        assertEquals(listOf("Hello"), session.transcript().map { it.text })
    }

    @Test fun tapInterruptFlushesCancelsAndRejectsOldAudio() {
        val session = listening()
        session.server(RealtimeEvent.ResponseCreated("r1"))
        session.server(RealtimeEvent.OutputAudio("r1", byteArrayOf(1, 2)))
        val effects = session.interrupt()
        assertTrue(effects.first() is LiveEffect.FlushPlayback)
        assertEquals(listOf("response.cancel"), sent(effects))
        assertEquals(LivePhase.LISTENING, session.state.phase)
        assertTrue(played(session.server(RealtimeEvent.OutputAudio("r1", byteArrayOf(3, 4)))).isEmpty())
        assertTrue(session.interrupt().isEmpty())
    }
    @Test fun tapAfterServerCompletionStillDropsBufferedPlaybackAndLateAudio() {
        val session = listening()
        session.server(RealtimeEvent.ResponseCreated("r1"))
        session.server(RealtimeEvent.OutputAudio("r1", byteArrayOf(1, 2)))
        session.server(RealtimeEvent.ResponseDone("r1", "completed"))
        assertEquals(listOf(LiveEffect.FlushPlayback), session.interrupt())
        assertTrue(played(session.server(RealtimeEvent.OutputAudio("r1", byteArrayOf(3, 4)))).isEmpty())
        assertEquals(LivePhase.LISTENING, session.state.phase)
    }
}

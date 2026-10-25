package com.dua.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModeSessionTest {
    private class Fake : VoiceModeSession.Actions {
        val calls = mutableListOf<String>()
        val states = mutableListOf<VoiceModeState>()
        override fun listen() { calls += "listen" }
        override fun stopListening() { calls += "stopListening" }
        override fun send(text: String, token: Int) { calls += "send:$text:$token" }
        override fun cancelReply() { calls += "cancelReply" }
        override fun speak(text: String, token: Int) { calls += "speak:$text:$token" }
        override fun stopSpeaking() { calls += "stopSpeaking" }
        override fun render(state: VoiceModeState) { states += state }
    }

    private val fake = Fake()
    private val session = VoiceModeSession(fake)
    private val phase get() = session.state.phase

    @Test fun fullTurnLoopsBackToListening() {
        session.start(); assertEquals(VoicePhase.Listening, phase)
        session.transcript("  What's the weather?  "); assertEquals(VoicePhase.Thinking, phase)
        session.reply("Sunny and 28°.", session.token); assertEquals(VoicePhase.Speaking, phase)
        session.spoken(session.token); assertEquals(VoicePhase.Listening, phase)
        assertEquals(listOf("listen", "send:What's the weather?:1", "speak:Sunny and 28°.:1", "listen"), fake.calls)
    }

    @Test fun noSpeechReListensThenWaits() {
        session.start()
        session.recognizerError(RecognizerError.NoSpeech); assertEquals(VoicePhase.Listening, phase)
        session.transcript(""); assertEquals(VoicePhase.Listening, phase)
        session.recognizerError(RecognizerError.NoSpeech)
        assertEquals(VoicePhase.Idle, phase)
        assertTrue(session.state.notice.contains("Tap the orb"))
        assertEquals(3, fake.calls.count { it == "listen" })
    }

    @Test fun speechResetsTheSilentCount() {
        session.start()
        session.recognizerError(RecognizerError.NoSpeech)
        session.recognizerError(RecognizerError.NoSpeech)
        session.transcript("hi"); session.reply("Hello", session.token); session.spoken(session.token)
        session.recognizerError(RecognizerError.NoSpeech)
        assertEquals(VoicePhase.Listening, phase)
    }

    @Test fun networkAndPermissionErrorsStopWithANotice() {
        session.start(); session.recognizerError(RecognizerError.Network)
        assertEquals(VoicePhase.Idle, phase); assertTrue(session.state.notice.contains("network"))
        session.start(); session.recognizerError(RecognizerError.Permission)
        assertEquals(VoicePhase.Idle, phase); assertTrue(session.state.notice.contains("microphone"))
    }

    @Test fun muteStopsListeningAndUnmuteResumes() {
        session.start()
        session.setMuted(true)
        assertEquals(VoicePhase.Idle, phase); assertTrue(session.state.muted)
        assertEquals("stopListening", fake.calls.last())
        session.start(); assertEquals(VoicePhase.Idle, phase) // muted: start does nothing
        session.setMuted(false)
        assertEquals(VoicePhase.Listening, phase); assertEquals("listen", fake.calls.last())
    }

    @Test fun mutedWhileSpeakingFinishesThenWaits() {
        session.start(); session.transcript("hi"); session.reply("Hello", session.token)
        session.setMuted(true)
        assertEquals(VoicePhase.Speaking, phase)
        session.spoken(session.token)
        assertEquals(VoicePhase.Idle, phase)
        assertEquals(1, fake.calls.count { it == "listen" })
    }

    @Test fun orbInterruptsSpeakingAndIgnoresTheStaleDone() {
        session.start(); session.transcript("tell me a story"); session.reply("Once upon a time…", session.token)
        val old = session.token
        session.tapOrb()
        assertEquals(VoicePhase.Listening, phase)
        assertEquals(listOf("stopSpeaking", "listen"), fake.calls.takeLast(2))
        session.spoken(old) // late callback from the interrupted utterance
        assertEquals(VoicePhase.Listening, phase)
        assertEquals(2, fake.calls.count { it == "listen" })
    }

    @Test fun orbInterruptsThinkingAndDropsTheLateReply() {
        session.start(); session.transcript("question")
        val old = session.token
        session.tapOrb()
        assertEquals("cancelReply", fake.calls[fake.calls.size - 2])
        session.reply("late answer", old)
        assertEquals(VoicePhase.Listening, phase)
        assertTrue(fake.calls.none { it.startsWith("speak") })
    }

    @Test fun orbPausesListeningAndStartsAgain() {
        session.start(); session.tapOrb()
        assertEquals(VoicePhase.Idle, phase)
        session.tapOrb()
        assertEquals(VoicePhase.Listening, phase)
    }

    @Test fun orbWhileMutedUnmutesAndListens() {
        session.setMuted(true); session.tapOrb()
        assertEquals(VoicePhase.Listening, phase); assertTrue(!session.state.muted)
    }

    @Test fun replyFailureAndPlaybackFailure() {
        session.start(); session.transcript("hi"); session.replyFailed("No connection.", session.token)
        assertEquals(VoicePhase.Idle, phase); assertEquals("No connection.", session.state.notice)
        session.start(); session.transcript("hi"); session.reply("Hello", session.token); session.speakFailed("Voice unavailable.", session.token)
        assertEquals(VoicePhase.Listening, phase); assertEquals("Voice unavailable.", session.state.notice)
        session.transcript("again"); session.reply("   ", session.token)
        assertEquals(VoicePhase.Idle, phase)
    }

    @Test fun pauseAndCloseStopEverything() {
        session.start(); session.transcript("hi")
        session.pause()
        assertEquals(VoicePhase.Idle, phase); assertEquals("cancelReply", fake.calls.last())
        session.start(); session.transcript("hi"); session.reply("Hello", session.token)
        session.close()
        assertTrue(session.state.closed); assertEquals("stopSpeaking", fake.calls.last())
        session.start(); session.tapOrb(); session.setMuted(true)
        assertEquals(VoicePhase.Idle, phase); assertTrue(!session.state.muted)
    }

    @Test fun tooLongTranscriptIsRejected() {
        session.start(); session.transcript("x".repeat(VoiceModeSession.MAX_CHARS + 1))
        assertEquals(VoicePhase.Idle, phase); assertTrue(fake.calls.none { it.startsWith("send") })
    }

    @Test fun vadStopsPlaybackBeforeListeningAndRejectsStaleOrMutedCallbacks() {
        session.start(); session.transcript("Question"); session.reply("Answer", session.token)
        val token = session.token
        session.bargeIn(token - 1)
        assertEquals(VoicePhase.Speaking, phase)
        session.setMuted(true); session.bargeIn(token)
        assertEquals(VoicePhase.Speaking, phase)
        session.setMuted(false); session.bargeIn(token)
        assertEquals(VoicePhase.Listening, phase)
        assertEquals(listOf("stopSpeaking", "listen"), fake.calls.takeLast(2))
        session.spoken(token)
        session.bargeIn(token)
        assertEquals(1, fake.calls.count { it == "stopSpeaking" })
        assertEquals(VoicePhase.Listening, phase)
    }
}

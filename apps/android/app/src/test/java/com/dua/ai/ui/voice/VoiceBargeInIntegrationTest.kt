package com.dua.ai.ui.voice

import android.app.Application
import com.dua.ai.voice.BargeInDetector
import com.dua.ai.voice.GeneratedAudioOutput
import com.dua.ai.voice.GeneratedVoiceSession
import com.dua.ai.voice.NativeReplySpeaker
import com.dua.ai.voice.VoiceModeSession
import com.dua.ai.voice.VoiceModeState
import com.dua.ai.voice.VoicePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real VAD/session/ReplyVoice cancellation with a fake audio sink; no mic, provider or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoiceBargeInIntegrationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val events = mutableListOf<String>()
    private val completed = mutableListOf<(Boolean) -> Unit>()
    private val audio = mutableListOf<ByteArray>()
    private lateinit var voice: ReplyVoice
    private lateinit var loop: VoiceModeSession

    @Before fun setup() {
        val output = object : GeneratedAudioOutput {
            override fun play(bytes: ByteArray, onFinished: (Boolean) -> Unit) {
                audio += bytes; completed += onFinished; events += "play"
            }
            override fun stop() { events += "release-audio" }
        }
        val generated = GeneratedVoiceSession(scope, { byteArrayOf(1, 2, 3) }, output, { "test-owner" }, { true })
        generated.setForeground(true); generated.setOnline(true)
        voice = ReplyVoice(NativeReplySpeaker(RuntimeEnvironment.getApplication()), generated)
        loop = VoiceModeSession(object : VoiceModeSession.Actions {
            override fun listen() {
                // The real ReplyVoice stop must finish before recognizer capture may resume.
                assertNull(voice.generated.state.value.activeId)
                events += "listen"
            }
            override fun stopListening() { events += "stop-listening" }
            override fun send(text: String, token: Int) { events += "send:$token" }
            override fun cancelReply() { events += "cancel-reply" }
            override fun speak(text: String, token: Int) { voice.preview("reply-$token", text, useGenerated = true) }
            override fun stopSpeaking() { events += "stop-speaking"; voice.stop() }
            override fun render(state: VoiceModeState) = Unit
        })
    }
    @After fun close() { voice.stop(); voice.generated.close(); voice.speaker.close(); scope.cancel() }
    private fun speaking(): Int {
        loop.start(); loop.transcript("Question"); loop.reply("Answer", loop.token)
        assertEquals(VoicePhase.Speaking, loop.state.phase)
        assertEquals("playing", voice.generated.state.value.phase)
        return loop.token
    }

    @Test fun sustainedVadStopsReplyVoiceReleasesAndWipesAudioThenListensExactlyOnce() {
        val token = speaking()
        val detector = BargeInDetector()
        val silence = ShortArray(640)
        val speech = ShortArray(640) { (sin(it * 2 * Math.PI * 220 / 16000) * 12000).toInt().toShort() }
        events.clear()
        repeat(5) { assertFalse(detector.accept(silence, silence.size)) }
        repeat(2) { assertFalse(detector.accept(speech, speech.size)) }
        assertTrue(detector.accept(speech, speech.size))
        loop.bargeIn(token)
        assertEquals(listOf("stop-speaking", "release-audio", "listen"), events)
        assertEquals(VoicePhase.Listening, loop.state.phase)
        assertArrayEquals(ByteArray(3), audio.single())
        completed.single()(true); loop.spoken(token); loop.bargeIn(token)
        assertEquals(listOf("stop-speaking", "release-audio", "listen"), events)
    }

    @Test fun completionFromInterruptedAudioCannotStopOrAdvanceANewReply() {
        val oldToken = speaking()
        loop.bargeIn(oldToken)
        loop.transcript("A different question"); loop.reply("New answer", loop.token)
        val newId = voice.generated.state.value.activeId
        events.clear()
        completed.first()(true); loop.spoken(oldToken); loop.bargeIn(oldToken)
        assertEquals(VoicePhase.Speaking, loop.state.phase)
        assertEquals(newId, voice.generated.state.value.activeId)
        assertTrue(events.isEmpty())
        assertArrayEquals(byteArrayOf(1, 2, 3), audio.last())
    }

    @Test fun mutedPausedAndClosedSessionsRejectLateSpeechAndPlaybackCallbacks() {
        val token = speaking()
        events.clear()
        loop.setMuted(true); loop.bargeIn(token)
        assertTrue(events.isEmpty())
        assertEquals(VoicePhase.Speaking, loop.state.phase)
        loop.pause()
        assertEquals(listOf("stop-speaking", "release-audio"), events)
        loop.bargeIn(token); completed.single()(true); loop.spoken(token)
        assertEquals(VoicePhase.Idle, loop.state.phase)
        assertEquals(listOf("stop-speaking", "release-audio"), events)
        loop.close(); events.clear()
        loop.bargeIn(token); loop.start(); loop.tapOrb(); completed.single()(true)
        assertTrue(loop.state.closed)
        assertTrue(events.isEmpty())
    }
}

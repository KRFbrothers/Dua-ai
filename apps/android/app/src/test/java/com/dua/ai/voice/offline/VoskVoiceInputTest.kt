package com.dua.ai.voice.offline

import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoskVoiceInputTest {
    class FakeRecognizer : OfflineRecognizer {
        var listener: OfflineRecognizer.Listener? = null
        var stops = 0; var cancels = 0; var closed = false
        override fun start(listener: OfflineRecognizer.Listener) { this.listener = listener }
        override fun stop() { stops++ }
        override fun cancel() { cancels++ }
        override fun close() { closed = true }
    }

    private var loads = 0
    private val fake = FakeRecognizer()
    private fun input(fail: Boolean = false) = VoskVoiceInput(File("model"), { loads++; if (fail) error("bad model") else fake }, { it.run() }, Handler(Looper.getMainLooper()))
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun loadsModelListensAndReturnsTheTranscriptOnConfirm() {
        val input = input()
        input.start()
        assertTrue(input.state.value.busy)
        idle()
        assertTrue(input.state.value.status.contains("offline"))
        fake.listener!!.onPartial("नमस्ते")
        assertEquals("नमस्ते", input.state.value.partial)
        fake.listener!!.onResult("नमस्ते दुआ")
        fake.listener!!.onPartial("कैसे")
        assertEquals("नमस्ते दुआ कैसे", input.state.value.partial)
        input.confirm()
        assertEquals(1, fake.stops)
        fake.listener!!.onFinal("कैसे हो")
        assertFalse(input.state.value.busy)
        assertEquals("नमस्ते दुआ कैसे हो", input.state.value.transcript)
        assertFalse("offline never auto-sends", input.state.value.endedBySilence)
        // Second session reuses the loaded model.
        input.start(); idle()
        assertEquals(1, loads)
    }

    @Test fun cancelDropsWordsAndIgnoresLateCallbacks() {
        val input = input(); input.start(); idle()
        fake.listener!!.onPartial("hello")
        val old = fake.listener!!
        input.stop()
        old.onFinal("hello")
        assertFalse(input.state.value.busy)
        assertEquals("", input.state.value.transcript)
        assertEquals(1, fake.cancels)
    }

    @Test fun brokenModelShowsAHelpfulMessage() {
        val input = input(fail = true); input.start(); idle()
        assertFalse(input.state.value.busy)
        assertTrue(input.state.value.status.contains("Settings > Voice"))
    }

    @Test fun timesOutByConfirmingAndCloseReleasesTheModel() {
        val input = input(); input.start(); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        assertEquals(1, fake.stops)
        fake.listener!!.onFinal("")
        assertTrue(input.state.value.status.contains("No speech"))
        input.close()
        assertTrue(fake.closed)
    }
}

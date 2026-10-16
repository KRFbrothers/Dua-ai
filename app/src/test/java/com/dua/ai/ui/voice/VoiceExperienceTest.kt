package com.dua.ai.ui.voice

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.voice.VoicePhase
import com.dua.ai.voice.live.LivePhase
import com.dua.ai.voice.live.LiveState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun waveformReusesBuffersAndBoundsInvalidOrJankyLevels() {
        val buffer = VoiceWaveformBuffer()
        val bins = buffer.bins
        val path = buffer.path
        repeat(10000) { buffer.update(if (it % 2 == 0) 3f else .4f, it / 60f, 1f / 60f) }
        assertSame(bins, buffer.bins); assertSame(path, buffer.path)
        assertTrue(bins.all { it.isFinite() && it in 0f..1f })
        buffer.update(Float.NaN, 200f, 10f)
        assertTrue(buffer.energy.isFinite())
    }

    @Test fun speakingHapticFiresOncePerOnsetAndRespectsOptOut() {
        val speaking = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var pulses = 0
        val haptic = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses++ } }
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides haptic) { VoiceSpeakingFeedback(speaking.value, enabled.value) } }
        compose.runOnIdle { assertEquals(0, pulses); speaking.value = true }
        compose.runOnIdle { assertEquals(1, pulses) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, pulses); speaking.value = false }
        compose.runOnIdle { enabled.value = false; speaking.value = true }
        compose.runOnIdle { assertEquals(1, pulses) }
    }

    @Test fun liveWaveformTapInterruptsWithAccessibleTarget() {
        var interrupted = 0
        compose.setContent { DuaTheme(LocalPreferences()) {
            LiveVoiceContent(LiveState(phase = LivePhase.SPEAKING), false, "", false, null, "Ara",
                {}, {}, {}, {}, {}, onInterrupt = { interrupted++ })
        } }
        compose.onNodeWithTag("voice-frequency-waveform", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Speaking. Tap to interrupt").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(1, interrupted)
    }

    @Test fun audioFramesInvalidateDrawingWithoutRecomposingContent() {
        compose.mainClock.autoAdvance = false
        val level = mutableFloatStateOf(.1f)
        compose.setContent { DuaTheme(LocalPreferences()) {
            VoiceFrequencyWaveform(VoiceLevel { level.floatValue }, speaking = false, muted = false, modifier = Modifier.size(128.dp))
        } }
        compose.mainClock.advanceTimeBy(100)
        val before = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
        compose.runOnIdle { level.floatValue = .9f }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(before, Recomposer.runningRecomposers.value.sumOf { it.changeCount }) }
    }
}

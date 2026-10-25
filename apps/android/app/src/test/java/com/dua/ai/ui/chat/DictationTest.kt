package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dua.ai.model.AgentStep
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class DictationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logicFormatsTimerKeepsWaveAndDecidesAutoSend() {
        assertEquals("0:07", DictationLogic.elapsed(7)); assertEquals("1:05", DictationLogic.elapsed(65))
        val levels = FloatArray(3)
        DictationLogic.push(levels, 0.5f); DictationLogic.push(levels, 2f)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), levels, 0f)
        assertEquals("draft\nhello" to false, DictationLogic.result("draft", "hello", autoSend = false, endedBySilence = true))
        assertEquals("hello" to true, DictationLogic.result("", " hello ", autoSend = true, endedBySilence = true))
        // Confirm tap (not silence) never auto-sends; empty speech changes nothing.
        assertEquals("hello" to false, DictationLogic.result("", "hello", autoSend = true, endedBySilence = false))
        assertNull(DictationLogic.result("x", "  ", autoSend = true, endedBySilence = true))
    }

    @Test fun autoSendSettingDefaultsOffAndRoundTrips() {
        assertFalse(LocalPreferences().dictationAutoSend)
        val on = PreferencesCodec.decode(PreferencesCodec.encode(LocalPreferences(dictationAutoSend = true)))
        assertTrue(on.dictationAutoSend)
        val old = JSONObject(PreferencesCodec.encode(LocalPreferences())).apply { remove("dictationAutoSend") }.toString()
        assertFalse(PreferencesCodec.decode(old).dictationAutoSend)
    }

    @Test fun dictationBarHasWaveTimerAndAccessibleControls() {
        var cancelled = 0; var confirmed = 0
        val level = mutableFloatStateOf(0.6f)
        compose.setContent { DuaTheme(LocalPreferences()) { Row { DictationBar(level, { cancelled++ }, { confirmed++ }) } } }
        compose.onNodeWithTag("dictation-wave").assertExists()
        compose.onNodeWithText("0:00").assertExists()
        compose.onNodeWithContentDescription("Cancel dictation").assertHeightIsAtLeast(Sizes.touchTarget).performClick()
        compose.onNodeWithContentDescription("Done, use dictation").assertWidthIsAtLeast(Sizes.touchTarget).performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(1, confirmed) }
    }

    @Test fun composerSwapsToDictationBarWhileListening() {
        val listening = androidx.compose.runtime.mutableStateOf(true)
        var started = 0
        val dictation = ChatDictation(listening, mutableFloatStateOf(0f), { started++ }, {}, {})
        compose.setContent { DuaTheme(LocalPreferences()) { Composer(dictation = dictation, controlled = true) } }
        compose.onNodeWithContentDescription("Cancel dictation").assertExists()
        compose.onNodeWithContentDescription("Message").assertDoesNotExist()
        listening.value = false
        compose.onNodeWithContentDescription("Dictate").performClick()
        compose.runOnIdle { assertEquals(1, started) }
    }

    @Test fun agentProgressShowsDoneChipsAndActiveStep() {
        compose.mainClock.autoAdvance = false
        compose.setContent { DuaTheme(LocalPreferences()) {
            AgentProgressView(listOf(AgentStep("plan", "Planning…", true), AgentStep("search", "Searching the web…")))
        } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Done: Planning").assertExists()
        compose.onNodeWithContentDescription("Searching the web…").assertHeightIsAtLeast(Sizes.touchTarget)
    }

    @Test fun agentRepliesKeepThinkingCollapsedButExpandable() {
        compose.mainClock.autoAdvance = false
        compose.setContent { DuaTheme(LocalPreferences()) {
            ThinkingBlock("m1", "**Step 1 - Plan**\n- look up news", null, live = true, answering = false, startedAt = 0L, followStream = false)
        } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("- look up news", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Thinking for", substring = true).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("look up news", substring = true).assertExists()
    }
}

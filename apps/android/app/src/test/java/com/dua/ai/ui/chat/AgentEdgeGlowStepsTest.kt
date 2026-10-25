package com.dua.ai.ui.chat

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.dua.ai.model.AgentStep
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.components.agentEdgeGlow
import com.dua.ai.ui.theme.DuaTheme
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
class AgentEdgeGlowStepsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stepPositionCountsActiveStep() {
        assertNull(agentStepPosition(emptyList()))
        assertEquals(2 to 3, agentStepPosition(listOf(AgentStep("a", "A", true), AgentStep("b", "B"), AgentStep("c", "C"))))
        assertEquals(2 to 2, agentStepPosition(listOf(AgentStep("a", "A", true), AgentStep("b", "B", true))))
    }

    @Test fun glowOnlyWhileAgentTyping() {
        assertTrue(agentWorking(ChatUiState(agent = true, typing = true)))
        assertFalse(agentWorking(ChatUiState(agent = true, typing = false)))
        assertFalse(agentWorking(ChatUiState(agent = false, typing = true)))
    }

    @Test fun telegramSpinnerFollowsRadialProgressTiming() {
        val s = TelegramRadialProgressState()
        repeat(30) { s.update(17) } // 510 ms: one rising phase done
        assertTrue(s.radOffset in 0f..360f)
        val r = TelegramRadialProgressState(); r.update(1000) // dt capped at 17 ms
        assertEquals(360f * 17 / 2000f, r.radOffset, 0.001f)
    }

    @Test fun trackerRendersSegmentsAndGlowComposes() {
        val steps = listOf(AgentStep("search", "Searching", true), AgentStep("read", "Reading"), AgentStep("write", "Writing"))
        compose.mainClock.autoAdvance = false // infinite glow + spinner never idle
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                Box(Modifier.size(200.dp).agentEdgeGlow(true, listOf(Color.Red, Color.Blue)).testTag("glow")) { AgentProgressView(steps) }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("glow").assertExists()
        compose.onNodeWithContentDescription("Step 2 of 3").assertExists()
        compose.onNodeWithTag("agent-step-seg-2", useUnmergedTree = true).assertExists()
    }
}

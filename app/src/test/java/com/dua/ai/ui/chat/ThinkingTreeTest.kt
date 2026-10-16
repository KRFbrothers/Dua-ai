package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThinkingTreeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyRealHeadingsCreateNodesAndAllDetailLinesSurvive() {
        val steps = reasoningSteps("1. Analyzing prompt\nUser needs current facts.\n\n2. Searching live web\n- Found two sources\n3. Formulating response\nCompare the sources.")
        assertEquals(listOf("Analyzing prompt", "Searching live web", "Formulating response"), steps.map { it.title })
        assertEquals("User needs current facts.", steps[0].lines)
        assertEquals("- Found two sources", steps[1].lines)
        assertEquals("Compare the sources.", steps[2].lines)
        assertEquals(emptyList<ReasoningStep>(), reasoningSteps(" \n"))
    }

    @Test fun unstructuredSummariesNeverFabricateResearchStepsOrLoseLongDetails() {
        val detail = "a real detail ".repeat(200)
        val steps = reasoningSteps("Considering the request\n$detail")
        assertEquals(1, steps.size)
        assertEquals("Considering the request", steps.single().title)
        assertEquals(detail.trim(), steps.single().lines)
        assertEquals(listOf("Plan", "Answer"), reasoningSteps("**Step 1 - Plan**\nread\n## Answer\nwrite").map { it.title })
    }

    @Test fun timerUsesRequestedLabelsAndNeverGoesNegative() {
        assertEquals("Thinking for 0s", thinkingLabel(true, null))
        assertEquals("Thinking for 0s", thinkingLabel(true, -1))
        assertEquals("Thinking for 8s", thinkingLabel(true, 8))
        assertEquals("Thought for 8s", thinkingLabel(false, 8))
    }

    @Test fun finishedTreeCollapsesAndCanBeReopenedWithoutDroppingDetails() {
        compose.mainClock.autoAdvance = false
        val live = mutableStateOf(true)
        compose.setContent { DuaTheme(LocalPreferences()) {
            ThinkingBlock("reply", "1. Analyze\nExact detail\n2. Respond\nFinal detail", 8, live.value, false, 0)
        } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Thinking for 8s").assertHeightIsAtLeast(Sizes.touchTarget)
        compose.onNodeWithText("Exact detail").assertExists()
        compose.runOnIdle { live.value = false }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Exact detail").assertDoesNotExist()
        compose.onNodeWithContentDescription("Thought for 8s").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Final detail").assertExists()
    }
}

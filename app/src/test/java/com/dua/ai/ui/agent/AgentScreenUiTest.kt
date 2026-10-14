package com.dua.ai.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class AgentScreenUiTest(private val dark: Boolean, private val compact: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},compact={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private val tasks = mutableListOf<AgentTask>()
    private val prompts = mutableListOf<String>()
    private var newTasks = 0
    private var backs = 0
    private var refreshes = 0

    private fun show(available: Boolean = true, busy: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(if (compact) 320.dp else 360.dp, if (compact) 568.dp else 752.dp)) {
                        AgentContent(available, busy, onBack = { backs++ }, onNewTask = { newTasks++ },
                            onRefresh = { refreshes++ }, onTask = { tasks += it }, onSend = { prompts += it })
                    }
                }
            }
        }
    }

    @Test fun referenceScreenRendersWithAllTasksAndFixedAccessibleControls() {
        show()
        assertNull(DuaDestination.Agent.comingSoonTitle)
        assertEquals("ds_agent_mode_regular_24", ReferenceIcons.AgentMode.name)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("New Agent Task").assertIsDisplayed()
        compose.onNodeWithTag("agent-input").assertIsDisplayed()
        compose.onNodeWithText("✨ Agent").assertIsDisplayed()
        compose.onNodeWithText("Deep Web Research").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Live").assertIsDisplayed()
        compose.onNodeWithText("GitHub Repo & PR Assistant").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithText("Daily Morning Briefing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("9:00 AM").assertIsDisplayed()
        compose.onNodeWithText("Runs when tapped").assertIsDisplayed()
        // Render the untouched reference view for review, with no provider or fabricated conversations.
        compose.onNodeWithText("What should Dua Agent do for you?").performScrollTo()
        val output = File("build/approved-ui/agent-${if (dark) "dark" else "light"}-${if (compact) "compact" else "regular"}.png")
        output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
    }

    @Test fun taskCardsDispatchTheirDistinctActions() {
        show()
        compose.onNodeWithText("Deep Web Research").performScrollTo().performClick()
        compose.onNodeWithText("GitHub Repo & PR Assistant").performScrollTo().performClick()
        compose.onNodeWithText("Daily Morning Briefing").performScrollTo().performClick()
        assertEquals(listOf(AgentTask.Research, AgentTask.GitHub, AgentTask.Briefing), tasks)
        assertTrue(GITHUB_AGENT_PROMPT.contains("KRFbrothers/Dua-ai"))
        assertTrue(briefingPrompt(LocalDate.of(2026, 10, 15)).contains("2026-10-15"))
    }

    @Test fun blankPromptCannotSendAndButtonAndKeyboardSendTheTypedPrompt() {
        show()
        compose.onNodeWithContentDescription("Send Agent prompt").assertIsNotEnabled()
        compose.onNodeWithTag("agent-input").performTextInput("Research Kotlin updates")
        compose.onNodeWithContentDescription("Send Agent prompt").performClick()
        compose.onNodeWithTag("agent-input").performImeAction()
        assertEquals(listOf("Research Kotlin updates", "Research Kotlin updates"), prompts)
    }

    @Test fun backAndNewTaskUseSeparateActions() {
        show()
        compose.onNodeWithContentDescription("New Agent Task").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, newTasks)
        assertEquals(1, backs)
        assertTrue(tasks.isEmpty())
    }

    @Test fun missingEngineDoesNotPretendToRunAnAgentTask() {
        show(available = false)
        compose.onNodeWithContentDescription("New Agent Task").assertIsNotEnabled()
        compose.onNodeWithTag("agent-input").performTextInput("Research")
        compose.onNodeWithContentDescription("Send Agent prompt").assertIsNotEnabled()
        compose.onNodeWithText("Deep Web Research").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        assertEquals(1, refreshes)
        assertTrue(tasks.isEmpty())
        assertTrue(prompts.isEmpty())
    }
}

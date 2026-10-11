package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class CompactChatUiTest(private val dark: Boolean, private val small: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},small={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(block: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (small) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(if (small) 320.dp else 360.dp, if (small) 568.dp else 752.dp)
                        .background(MaterialTheme.colorScheme.background)) { block() }
                }
            }
        }
    }
    private fun capture(name: String) {
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/$name-${if (dark) "dark" else "light"}-${if (small) "small-font2" else "normal"}.png")
            file.parentFile?.mkdirs()
            compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }
    @Test fun homeRetainsComputerProjectsVoiceAndMessageControls() {
        var computer = 0; var newChat = 0; var voice = 0
        var suggested = ""
        show { ChatScreen(ChatUiState(webSearchConfigured = true, codeConfig = CodeExecutionConfig(true, providers = setOf("Dua")),
            suggestions = listOf(com.dua.ai.model.PromptSuggestion("Explain some code", "Explain Kotlin", "Coding"),
                com.dua.ai.model.PromptSuggestion("Draft a proposal", "Draft a proposal", "Business"))),
            ChatActions(onDraftChange = { suggested = it }, onOpenComputer = { computer++ }, onNewChat = { newChat++ }, onOpenVoiceMode = { voice++ })) }
        for (description in listOf("Computer, coming soon", "Open chat history", "New chat", "Projects, Personal", "Voice mode", "Add photos and files")) {
            compose.onNodeWithContentDescription(description).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        val header = compose.onNodeWithContentDescription("Projects, Personal").fetchSemanticsNode().boundsInRoot
        val message = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot
        assertTrue(header.bottom <= message.top)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (description in listOf("Let the AI run Python code", "Think before answering", "Search the web")) {
            val bounds = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= root.left && bounds.right <= root.right)
        }
        compose.onNodeWithContentDescription("Computer, coming soon").performClick()
        compose.onNodeWithContentDescription("New chat").performClick()
        compose.onNodeWithContentDescription("Voice mode").performClick()
        compose.runOnIdle { assertEquals(1, computer); assertEquals(1, newChat); assertEquals(1, voice) }
        capture("compact-home")
        if (!small) {
            compose.onNodeWithText("Explain some code").performClick()
            compose.runOnIdle { assertEquals("Explain Kotlin", suggested) }
        }
        compose.onNodeWithContentDescription("Projects, Personal").performClick()
        compose.onNode(hasText("Projects") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Heading, Unit)).assertIsDisplayed()
        compose.onAllNodesWithText("Personal").assertCountEquals(2)
    }
    @Test fun projectCardsKeepSelectionEditMemoryTasksAndPlannerReachable() {
        var selected = "personal"
        val projects = ProjectCodec.defaults.map { if (it.id == "business") it.copy(archived = true) else it }
        show { ProjectsContent(projects, "personal", "", true, { selected = it }, { _, _, _, _ -> true }, tasksEnabled = true, memoryEnabled = true) }
        compose.onNodeWithText("Daily planner").assertIsDisplayed()
        compose.onNodeWithText("Personal").assertIsDisplayed()
        capture("compact-projects")
        compose.onNodeWithText("Coding").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("coding", selected) }
        compose.onNodeWithText("Business (archived)").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Memory · Coding").performScrollTo().performClick()
        compose.onNodeWithText("Coding memory").assertIsDisplayed()
        compose.onNodeWithText("Back to projects").performScrollTo().performClick()
        compose.onAllNodesWithText("Tasks (0)")[1].performScrollTo().performClick()
        compose.onNodeWithText("Coding · Tasks").assertIsDisplayed()
    }
}

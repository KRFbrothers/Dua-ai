package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.features.ImagesContent
import com.dua.ai.ui.features.LibraryContent
import com.dua.ai.ui.features.PluginsContent
import com.dua.ai.ui.features.ProjectsFeaturePage
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
class FeatureIsolationUiTest(private val dark: Boolean, private val compact: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},compact={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.requiredSize(if (compact) 320.dp else 360.dp, if (compact) 568.dp else 752.dp)) { content() }
            }
        }
    }
    private fun assertNoHome() {
        compose.onNodeWithText("Chat").assertDoesNotExist()
        compose.onNodeWithText("Work").assertDoesNotExist()
        compose.onNode(hasText("Task") and isSelectable()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Open chat history").assertDoesNotExist()
        compose.onNodeWithContentDescription("Find in chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("New chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Model, Dua").assertDoesNotExist()
        listOf("Create an image or sticker", "Write or edit").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }
    @Test fun normalHeaderIsUnchangedBySendStreamingAndCompletion() {
        val state = mutableStateOf(ChatUiState())
        var newChats = 0
        show { ChatScreen(state.value, ChatActions(onNewChat = { newChats++ })) }
        for (messages in listOf(emptyList(), listOf(ChatMessage("u", "Question", true)),
            listOf(ChatMessage("a", "Answer", false, state = ChatMessage.STREAMING)), listOf(ChatMessage("a", "Answer", false)))) {
            compose.runOnIdle { state.value = state.value.copy(messages = messages, typing = messages.any { it.streaming }) }
            if (messages.isEmpty()) {
                compose.onNode(hasText("Chat") and isSelectable()).assertIsDisplayed()
                compose.onNode(hasText("Task") and isSelectable()).assertIsDisplayed()
            } else compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
            compose.onNodeWithContentDescription("Open profile and settings").assertIsDisplayed()
            compose.onNodeWithContentDescription("Find in chat").assertDoesNotExist()
            compose.onNodeWithContentDescription("New chat").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("New chat").performClick()
        assertEquals(1, newChats)
    }
    @Test fun agentStartersDisappearAfterSendAndCompactPillStaysCentered() {
        val state = mutableStateOf(ChatUiState(agent = true, agentAvailable = true))
        show { ChatScreen(state.value, ChatActions()) }
        assertNoHome()
        compose.onNodeWithText("Deep Web Research").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GitHub Repo & PR Assistant").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Daily Morning Briefing").performScrollTo().assertIsDisplayed()
        val pill = compose.onNodeWithTag("agent-mode-pill").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot
        assertTrue(pill.width < (if (compact) 320f else 360f))
        assertTrue(pill.bottom <= input.top)
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(screen.center.x, pill.center.x, 1f)
        for (streaming in listOf(true, false)) {
            compose.runOnIdle { state.value = state.value.copy(typing = streaming, messages = listOf(
                ChatMessage("u", "Research Dua", true), ChatMessage("a", "Research output", false,
                    state = if (streaming) ChatMessage.STREAMING else "complete", thinking = "Review sources"))) }
            assertNoHome()
            compose.onNodeWithText("Deep Web Research").assertDoesNotExist()
            compose.onNodeWithText("Research output").assertIsDisplayed()
            compose.onNodeWithText("AI Agent").assertIsDisplayed()
        }
        val output = File("build/approved-ui/agent-conversation-${if (dark) "dark" else "light"}-${if (compact) "compact" else "regular"}.png")
        output.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(output.absolutePath)
    }
    @Test fun agentPlusBackAndAttachmentsStayInsideFeature() {
        val state = mutableStateOf(ChatUiState(agent = true, agentAvailable = true, messages = listOf(ChatMessage("a", "Agent answer", false))))
        show { ChatScreen(state.value, ChatActions(onNewFeatureSession = { state.value = state.value.copy(messages = emptyList()) },
            onExitFeature = { state.value = ChatUiState() })) }
        compose.onNodeWithContentDescription("New AI Agent session").performClick()
        compose.onNodeWithText("Deep Web Research").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Chat") and isSelectable()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Open profile and settings").assertIsDisplayed()
        compose.runOnIdle { state.value = ChatUiState(agent = true, agentAvailable = true) }
        // Verify the accessible attachment action after switching away and back to the feature.
        compose.onNodeWithContentDescription("Add photos and files").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithText("Files", substring = false).assertExists()
        compose.onNodeWithText("Turn off all modes").assertDoesNotExist()
        compose.onNodeWithText("Search", substring = false).assertDoesNotExist()
    }
    @Test fun functionalDestinationsHaveOnlyTheirOwnHeader() {
        val title = mutableStateOf("Images")
        var back = 0
        show {
            when (title.value) {
                "Images" -> ImagesContent(emptyList(), "", "1:1", listOf("1:1"), false, true, "", "", { back++ }, {}, {}, {}, {}, {})
                "Library" -> LibraryContent(emptyList(), false, "", { back++ }, {}, {})
                else -> PluginsContent(com.dua.ai.setup.IntegrationState(), "Dua", false, false, false, false, false, "", "", { back++ }, {}, {}, {}, {}, {})
            }
        }
        for (feature in listOf("Images", "Library", "Plugins")) {
            compose.runOnIdle { title.value = feature }
            assertNoHome()
            compose.onNodeWithText(feature).assertIsDisplayed()
            compose.onNodeWithText("Coming soon").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").performClick()
        }
        assertEquals(3, back)
    }
    @Test fun projectsPlusUsesExistingEditorWithoutHomeChrome() {
        var saved = ""
        show { ProjectsFeaturePage(ChatUiState(), ChatActions(onSaveProject = { _, name, _, _ -> saved = name; true }), {}) }
        assertNoHome()
        compose.onNodeWithContentDescription("New Project").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Project name").performTextInput("Client")
        compose.onNodeWithText("Create Project").assertIsDisplayed().performClick()
        assertEquals("Client", saved)
        assertNoHome()
    }
}

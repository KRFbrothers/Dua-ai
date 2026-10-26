package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.FreeModel
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.ProviderCapability
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.HomeDarkColors
import com.dua.ai.ui.theme.HomeLightColors
import com.dua.ai.ui.theme.ChatDarkColors
import com.dua.ai.ui.theme.ChatLightColors
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
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeChatTaskUiTest(private val dark: Boolean, private val compact: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},compact={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private val initial = ChatUiState(profileName = "Dua Owner", agentAvailable = true,
        providers = listOf(ProviderCapability("Dua", true, "gemini-current-flash", true)))

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (compact) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                assertEquals(if (dark) HomeDarkColors else HomeLightColors, HomeTheme.colors)
                assertEquals(if (dark) ChatDarkColors else ChatLightColors, ChatTheme.colors)
                Box(Modifier.requiredSize(if (compact) 320.dp else 360.dp, if (compact) 568.dp else 752.dp)) { content() }
            }
        }
    }
    private fun capture(name: String) {
        val file = File("build/approved-ui/home-$name-${if (dark) "dark" else "light"}-${if (compact) "font2" else "regular"}.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun taskSuggestionsFillDraftAndSendThroughExistingAgentHandlerWithoutFeatureChrome() {
        val state = mutableStateOf(initial)
        val modes = mutableListOf<Boolean>()
        var submitted: Pair<String, Boolean>? = null
        show { ChatScreen(state.value, ChatActions(onAgent = { modes += it; state.value = state.value.copy(agent = it) },
            onDraftChange = { state.value = state.value.copy(draft = it) }, onSend = {
                submitted = state.value.draft to state.value.agent
                state.value = state.value.copy(draft = "", messages = listOf(ChatMessage(text = "Task output", isUser = false)))
            })) }
        compose.onNode(hasText("Task") and isSelectable()).performClick().assertIsSelected()
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
        compose.onNodeWithTag("supplied-task-starters").assertIsDisplayed()
        compose.onNodeWithText("Describe...").assertIsDisplayed()
        compose.onNodeWithContentDescription("New chat").assertDoesNotExist()
        compose.onNodeWithText("AI Agent").assertIsNotDisplayed() // The closed drawer retains its destination label.
        compose.onNodeWithText("Deep Web Research").assertDoesNotExist()
        for (prompt in HomeTaskPrompts) compose.onNodeWithText(prompt).performScrollTo().assertIsDisplayed()
        capture("task")
        compose.onNodeWithText(HomeTaskPrompts[1]).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Message").assertTextContains(HomeTaskPrompts[1]).assertIsFocused()
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(HomeTaskPrompts[1] to true, submitted)
        compose.onNodeWithText("Task output").assertIsDisplayed()
        compose.onNodeWithText(HomeTaskPrompts[1]).assertDoesNotExist()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        assertEquals(listOf(true), modes)
    }

    @Test fun unavailableTaskModeCannotSilentlySendPlainChatOrEraseDraft() {
        val state = mutableStateOf(initial.copy(agentAvailable = false))
        var sends = 0
        show { ChatScreen(state.value, ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) }, onSend = { sends++ })) }
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Message").performTextInput("Keep this task")
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Message").performImeAction()
        assertEquals(0, sends)
        assertEquals("Keep this task", state.value.draft)
        compose.onNode(hasText("Chat") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        assertEquals(1, sends)
    }

    @Test fun freeAndUnknownModelNamesComeFromCurrentState() {
        assertEquals("Friendly free model", homeModelName(initial.copy(freeModel = FreeModel("vendor/id", "Friendly free model"))))
        assertEquals("Flash Lite", homeModelName(initial.copy(providers = listOf(ProviderCapability("Dua", true, "gemini-new-flash-lite", true)))))
        assertEquals("custom-model-id", homeModelName(initial.copy(providers = listOf(ProviderCapability("Dua", true, "custom-model-id", true)))))
    }

    @Test fun existingTurnOffModesActionAlsoReturnsTheHomeSwitcherToChat() {
        val state = mutableStateOf(initial)
        show { ChatScreen(state.value, ChatActions(onAgent = { state.value = state.value.copy(agent = it) },
            onAuto = { state.value = state.value.copy(agent = false) })) }
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Turn off all modes").performScrollTo().performClick()
        compose.onNode(hasText("Chat") and isSelectable()).assertIsSelected()
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
    }
}

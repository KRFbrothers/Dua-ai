package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.ProviderCapability
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeModeTaskSetupUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enablingAgentInConversationRunsSetupAndClosesSheetWithoutLosingHistory() {
        val old = ChatMessage(id = "old", text = "Earlier answer", isUser = false)
        val state = mutableStateOf(ChatUiState(profileName = "Dua Owner", agentAvailable = true, messages = listOf(old),
            providers = listOf(ProviderCapability("Dua", true, "gemini-current-flash", true))))
        val agentCalls = mutableListOf<Boolean>()
        compose.setContent {
            DuaTheme(LocalPreferences(theme = "light")) {
                ChatScreen(state.value, ChatActions(onAgent = { on -> agentCalls += on; state.value = state.value.copy(agent = on) },
                    onExitFeature = { state.value = state.value.copy(agent = false) }),
                    initialSheet = ChatSheet.Attach)
            }
        }
        compose.onNodeWithTag("attachment-sheet").assertExists()
        compose.onNodeWithText("Tools").performScrollTo().performClick()
        compose.onNodeWithText("Agent").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), agentCalls)
        compose.onNodeWithTag("attachment-sheet").assertDoesNotExist()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        // Agent view trims prior turns visually, while retaining the original history.
        compose.onNodeWithText("Earlier answer").assertDoesNotExist()
        assertEquals(listOf(old), state.value.messages)
        // Exercise the accessible navigation action after closing the modal window.
        compose.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        assertEquals(listOf(true), agentCalls)
        compose.onNodeWithText("Earlier answer").assertExists()
    }
}

package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
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

    @Test fun selectingTaskInConversationRunsAgentSetupAndClosesSheet() {
        val old = ChatMessage(id = "old", text = "Earlier answer", isUser = false)
        val state = mutableStateOf(ChatUiState(profileName = "Dua Owner", agentAvailable = true, messages = listOf(old),
            providers = listOf(ProviderCapability("Dua", true, "gemini-current-flash", true))))
        val agentCalls = mutableListOf<Boolean>()
        compose.setContent {
            DuaTheme(LocalPreferences(theme = "light")) {
                ChatScreen(state.value, ChatActions(onAgent = { on -> agentCalls += on; state.value = state.value.copy(agent = on) }),
                    initialSheet = ChatSheet.Attach)
            }
        }
        compose.onNodeWithTag("attachment-sheet").assertExists()
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), agentCalls)
        compose.onNodeWithTag("attachment-sheet").assertDoesNotExist()
        compose.onNode(hasText("Task") and isSelectable()).assertIsSelected()
        // Task mode keeps the whole thread visible; the agent boundary only trims in chat-with-agent view.
        compose.onNodeWithText("Earlier answer").assertExists()
        compose.onNode(hasText("Chat") and isSelectable()).performClick()
        compose.waitForIdle()
        assertEquals(listOf(true, false), agentCalls)
        compose.onNodeWithText("Earlier answer").assertExists()
    }
}

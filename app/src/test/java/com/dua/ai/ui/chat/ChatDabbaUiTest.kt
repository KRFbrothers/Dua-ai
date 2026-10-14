package com.dua.ai.ui.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.ChatDarkColors
import com.dua.ai.ui.theme.ChatLightColors
import com.dua.ai.ui.theme.ChatMessageStyle
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.markdownBodyStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** In-chat screen follows the Dua AI Dabba reference: grey 20 dp user bubble right, bare assistant text left, 14 dp gaps. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDabbaUiTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()
    private val prompt = ChatMessage(id = "p", text = "Hi", isUser = true)
    private val reply = ChatMessage(id = "r", text = "Hello! How can **Dua AI** help you today?", isUser = false)

    @Test fun conversationMatchesReferenceLayout() {
        var bodyColor = Color.Unspecified
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                bodyColor = markdownBodyStyle.color
                MessageList(ChatUiState(chatId = "c", messages = listOf(prompt, reply)), ChatActions(), rememberLazyListState())
            }
        }
        assertEquals((if (dark) ChatDarkColors else ChatLightColors).assistantText, bodyColor)
        assertEquals(16.sp, ChatMessageStyle.fontSize)
        compose.onAllNodesWithTag("dua-avatar", useUnmergedTree = true).assertCountEquals(0)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val user = compose.onNodeWithTag("message-p").fetchSemanticsNode().boundsInRoot
        val bubble = compose.onNodeWithText("Hi", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bot = compose.onNodeWithTag("assistant-turn", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val density = compose.density
        with(density) {
            // User bubble hugs the right edge (16 dp list padding + 16 dp bubble padding).
            assertEquals((root.right - (ChatSizes.listPaddingH + ChatSizes.bubblePaddingH).toPx()), bubble.right, 1.5f)
            // Assistant box starts at the 16 dp list padding, 14 dp below the user turn.
            assertEquals(ChatSizes.listPaddingH.toPx(), bot.left, 1.5f)
            assertEquals(ChatSizes.messageSpacing.toPx(), bot.top - user.bottom, 1.5f)
        }
        // Long-press menu stays reachable for TalkBack users (sheet behaviour itself: MessageLongPressTest).
        compose.onNode(hasText("Hi") and hasAnyAncestor(hasTestTag("message-p")), useUnmergedTree = true).assertExists()
    }
}

package com.dua.ai.ui.chat

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reading earlier turns must survive reply completion, Stop, and navigation between chats. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class LongReplyScrollTest {
    @get:Rule val compose = createComposeRule()

    private fun conversation(chatId: String, streaming: Boolean = true): ChatUiState = ChatUiState(
        chatId = chatId,
        typing = streaming,
        // Short turns keep this a scrolling regression test, without expensive Markdown fixtures.
        messages = (1..12).flatMap { turn ->
            listOf(
                ChatMessage(id = "u$turn", text = "Question $turn for $chatId.", isUser = true),
                ChatMessage(id = "a$turn", text = "Reply $turn for $chatId.", isUser = false,
                    model = "Dua", state = if (streaming && turn == 12) "streaming" else "complete"),
            )
        },
    )

    private fun show(initial: ChatUiState = conversation("first")): MutableState<ChatUiState> {
        val state = mutableStateOf(initial)
        compose.setContent {
            DuaTheme(LocalPreferences(theme = "dark")) {
                ChatScreen(state.value, ChatActions(onStop = { state.value = completed(state.value, "stopped") }))
            }
        }
        compose.waitForIdle()
        return state
    }

    private fun completed(state: ChatUiState, finalState: String = "complete"): ChatUiState = state.copy(
        typing = false,
        messages = state.messages.map { if (it.streaming) it.copy(state = finalState) else it },
    )

    private fun scrollUpToRead(): Float {
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Go to latest reply").assertIsDisplayed()
        return scrollPosition()
    }

    private fun scrollPosition(): Float = compose.onNodeWithTag("chat-messages")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun completionKeepsReaderPositionAndLatestButtonReturnsToReply() {
        val state = show()
        val position = scrollUpToRead()

        compose.runOnIdle { state.value = completed(state.value) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Stop response").assertDoesNotExist()
        assertEquals("Completing a reply must not move the reader", position, scrollPosition(), 0.02f)
        compose.onNodeWithContentDescription("Go to latest reply").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Reply 12 for first.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go to latest reply").assertDoesNotExist()
    }

    @Test fun stopKeepsReaderPosition() {
        show()
        val position = scrollUpToRead()

        compose.onNodeWithContentDescription("Stop response").performClick()
        compose.waitForIdle()

        assertEquals("Stopping a reply must not move the reader", position, scrollPosition(), 0.02f)
        compose.onNodeWithContentDescription("Go to latest reply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop response").assertDoesNotExist()
    }

    @Test fun switchingChatWithReusedMessageIdsShowsItsLatestReply() {
        val state = show()
        scrollUpToRead()

        // Identity comes from the chat, even if imported conversations reuse their message IDs.
        compose.runOnIdle { state.value = conversation("second", streaming = false) }
        compose.mainClock.advanceTimeByFrame()
        // Wait for the changed conversation to be composed and measured.
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Reply 12 for second.").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Reply 12 for second.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go to latest reply").assertDoesNotExist()
    }

    @Test fun newPromptReturnsReaderToItsReply() {
        val state = show(conversation("first", streaming = false))
        scrollUpToRead()
        compose.runOnIdle {
            state.value = state.value.copy(messages = state.value.messages + listOf(
                ChatMessage(id = "next-user", text = "New question", isUser = true),
                ChatMessage(id = "next-reply", text = "New reply", isUser = false),
            ))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("New reply").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithContentDescription("Go to latest reply").assertDoesNotExist()
    }

    @Test fun openingVeryTallCodeReplyReachesItsActualEnd() {
        val code = List(1_800) { "line $it" }.joinToString("\n")
        val state = ChatUiState(chatId = "tall", messages = listOf(
            ChatMessage(id = "prompt", text = "Show code", isUser = true),
            ChatMessage(id = "reply", text = "```text\n$code\n```\n\nFinal line.", isUser = false),
        ))
        show(state)
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Final line.").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithContentDescription("Go to latest reply").assertDoesNotExist()
    }
}

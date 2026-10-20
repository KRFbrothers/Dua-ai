package com.dua.ai.ui.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.model.ChatMessage
import com.dua.ai.ondevice.RewriteStyle
import com.dua.ai.ondevice.WritingSession
import com.dua.ai.ondevice.WritingTask
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.Sizes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class WritingAndJetchatUiTest {
    @get:Rule val compose = createComposeRule()
    private val user = ChatMessage(id = "u", text = "Question", isUser = true)
    private val bot = ChatMessage(id = "b", text = "A long answer", isUser = false, state = "complete")

    @Test fun rewriteSheetListsStylesWithOnDeviceLabel() {
        var picked: RewriteStyle? = null
        compose.setContent { DuaTheme(LocalPreferences()) {
            WritingToolsContent(WritingSession("hi", fromDraft = true, task = null), WritingActions(onStyle = { picked = it }))
        } }
        compose.onNodeWithText("On-device · your message").assertExists()
        RewriteStyle.entries.forEach { compose.onNodeWithTag("rewrite-${it.name}").performScrollTo().assertHeightIsAtLeast(SheetSizes.touch) }
        compose.onNodeWithTag("rewrite-Shorter").performScrollTo().performClick()
        assertEquals(RewriteStyle.Shorter, picked)
    }

    @Test fun resultCanReplaceTheMessageAndFailureOffersTheServer() {
        var session by mutableStateOf(WritingSession("hi", true, WritingTask.Summarize, WritingSession.Phase.Working))
        var used = 0; var asked = 0
        compose.setContent { DuaTheme(LocalPreferences()) { WritingToolsContent(session, WritingActions(onUse = { used++ }, onAskServer = { asked++ })) } }
        compose.onNodeWithTag("writing-progress").assertExists()
        compose.onNodeWithText("Summarizing on this phone…").assertExists()
        session = session.copy(phase = WritingSession.Phase.Result, result = "- Point")
        compose.onNodeWithTag("writing-result").assertExists()
        compose.onNodeWithText("Replace message").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(1, used)
        session = session.copy(phase = WritingSession.Phase.Unavailable)
        compose.onNodeWithText("Replace message").assertDoesNotExist()
        compose.onNodeWithText("Ask Dua instead").performClick()
        assertEquals(1, asked)
    }

    @Test fun longPressReplyOffersSummarizeAndRewrite() {
        val calls = mutableListOf<Pair<String, WritingTask?>>()
        compose.setContent { DuaTheme(LocalPreferences()) {
            MessageList(ChatUiState(chatId = "c", messages = listOf(user, bot)),
                ChatActions(onWriting = { text, fromDraft, task -> assertFalse(fromDraft); calls += text to task }), rememberLazyListState())
        } }
        compose.onNodeWithText("A long answer", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("popup-more").performClick()
        compose.onNodeWithTag("message-action-summarize").performScrollTo().assertHeightIsAtLeast(SheetSizes.rowMin).performClick()
        assertEquals("A long answer" to WritingTask.Summarize, calls.single())
        compose.onNodeWithText("A long answer", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("popup-more").performClick()
        compose.onNodeWithTag("message-action-rewrite").performScrollTo().performClick()
        assertEquals("A long answer" to null, calls.last())
    }

    @Test fun textMenuAddsSummarizeAndRewriteOnlyWithText() {
        val view = android.view.View(org.robolectric.RuntimeEnvironment.getApplication())
        var hasText = true; val clicked = mutableListOf<String>()
        val toolbar = DuaTextToolbar(view) { if (hasText) listOf(TextToolbarExtra("Summarize") { clicked += "s" }, TextToolbarExtra("Rewrite") { clicked += "r" }) else emptyList() }
        var copied = 0
        toolbar.showMenu(androidx.compose.ui.geometry.Rect(0f, 0f, 10f, 10f), { copied++ }, null, null, null)
        val menu = RoboMenu(org.robolectric.RuntimeEnvironment.getApplication())
        toolbar.populate(menu)
        assertEquals(listOf("Copy", "Summarize", "Rewrite"), (0 until menu.size()).map { menu.getItem(it).title.toString() })
        toolbar.click(DuaTextToolbar.EXTRA + 1); toolbar.click(DuaTextToolbar.COPY)
        assertEquals(listOf("r"), clicked); assertEquals(1, copied)
        hasText = false
        toolbar.showMenu(androidx.compose.ui.geometry.Rect.Zero, null, {}, null, null)
        toolbar.populate(menu)
        assertEquals(listOf("Paste"), (0 until menu.size()).map { menu.getItem(it).title.toString() })
        toolbar.hide()
    }

    @Test fun sameAuthorMessagesAreGroupedCloser() {
        val second = user.copy(id = "u2", text = "Another")
        val list = listOf(user, second, bot)
        assertEquals(0f, MessageGrouping.gapBefore(list, 0).value)
        assertEquals(ChatSizes.groupedMessageSpacing, MessageGrouping.gapBefore(list, 1))
        assertEquals(ChatSizes.messageSpacing, MessageGrouping.gapBefore(list, 2))
        compose.setContent { DuaTheme(LocalPreferences()) { MessageList(ChatUiState(chatId = "c", messages = list), ChatActions(), rememberLazyListState()) } }
        val first = compose.onNodeWithTag("message-u").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("message-u2").fetchSemanticsNode().boundsInRoot
        val reply = compose.onNodeWithTag("message-b").fetchSemanticsNode().boundsInRoot
        assertTrue(next.top - first.bottom < reply.top - next.bottom)
    }

    @Test fun newReplyCountAndPillLabel() {
        val list = listOf(user, bot, user.copy(id = "u2"), bot.copy(id = "b2"), bot.copy(id = "b3"))
        assertEquals(0, MessageGrouping.newReplies(list, 5))
        assertEquals(2, MessageGrouping.newReplies(list, 3))
        assertEquals(0, MessageGrouping.newReplies(list, 9))
        assertEquals("Latest reply", MessageGrouping.label(0))
        assertEquals("1 new reply", MessageGrouping.label(1))
        assertEquals("3 new replies", MessageGrouping.label(3))
        var count by mutableStateOf(0); var clicks = 0
        compose.setContent { DuaTheme(LocalPreferences()) { JumpToLatest(true, count, { clicks++ }) } }
        compose.onNodeWithContentDescription("Go to latest reply").assertHeightIsAtLeast(Sizes.touchTarget).performClick()
        assertEquals(1, clicks)
        count = 2
        compose.onNodeWithTag("page-down-counter", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("2", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Go to latest reply").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "2 new replies"))
    }
}

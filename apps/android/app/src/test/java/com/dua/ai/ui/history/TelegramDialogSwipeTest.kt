package com.dua.ai.ui.history

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.TelegramPageDown
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class TelegramDialogSwipeTest {
    @get:Rule val compose = createComposeRule()
    private fun chat(id: String, title: String, pinned: Boolean = false) =
        SavedChat(id, "Dua", "", listOf(ChatMessage(text = "q", isUser = true)), 1, customTitle = title, pinned = pinned)

    @Test fun thresholdIs45PercentAndOnlyDistanceCounts() {
        assertFalse(TelegramDialogSwipe.revealed(-44f, 100f))
        assertTrue(TelegramDialogSwipe.revealed(-45f, 100f))
        assertTrue(TelegramDialogSwipe.revealed(45f, 100f))
        assertFalse(TelegramDialogSwipe.completes(-10f, 100f))
        assertFalse(TelegramDialogSwipe.revealed(0f, 0f))
        assertEquals(200, TelegramDialogSwipe.CANCEL_MILLIS)
        assertEquals(5000L, TelegramDialogSwipe.UNDO_MILLIS)
    }

    @Test fun bounceInterpolatorMatchesDialogCell() {
        val b = TelegramDialogSwipe.Bounce
        assertEquals(0f, b.transform(0f), 1e-4f)
        assertEquals(0.1f, b.transform(0.33f), 1e-3f)
        assertEquals(-0.05f, b.transform(0.67f), 1e-2f)
        assertEquals(0f, b.transform(1f), 1e-3f)
    }

    @Test fun pageDownNumbersMatchTelegram() {
        assertEquals(0.7f, TelegramPageDown.scale(0f), 0f)
        assertEquals(1f, TelegramPageDown.scale(1f), 0f)
        assertEquals(280, TelegramPageDown.VISIBILITY_MILLIS)
        assertEquals("12", TelegramPageDown.counterText(12))
    }

    @Test fun swipeLeftDeletesAfterUndoWindowAndUndoRestores() {
        val deleted = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent { DuaTheme(LocalPreferences()) {
            HistoryDrawer(chats = listOf(chat("a", "Alpha roadmap"), chat("b", "Beta plugin")), onDeleteChat = { deleted += it })
        } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Alpha roadmap").performTouchInput { swipeLeft(startX = right - 1f, endX = left + 1f) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("chat-undo").assertExists()
        compose.onNodeWithText("Alpha roadmap").assertDoesNotExist()
        compose.onNodeWithTag("chat-undo-button").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Alpha roadmap").assertExists()
        assertTrue(deleted.isEmpty())

        compose.onNodeWithText("Beta plugin").performTouchInput { swipeLeft(startX = right - 1f, endX = left + 1f) }
        compose.mainClock.advanceTimeBy(6000)
        compose.waitForIdle()
        assertEquals(listOf("b"), deleted)
    }

    @Test fun swipeRightPinsWhenPinIsAvailable() {
        val pins = mutableListOf<Pair<String, Boolean>>()
        compose.setContent { DuaTheme(LocalPreferences()) {
            HistoryDrawer(chats = listOf(chat("a", "Alpha roadmap")), pinEnabled = true, onPinChat = { id, p -> pins += id to p; null })
        } }
        compose.onNodeWithText("Alpha roadmap").performTouchInput { swipeRight(startX = left + 1f, endX = right - 1f) }
        compose.waitForIdle()
        assertEquals(listOf("a" to true), pins)
    }
}

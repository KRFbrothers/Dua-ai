package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reference interactions exercised through the same screen used by ChatRoute. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatReplacementUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chatAndTaskShowOnlyTheLogoInPlaceOfTheirGreetings() {
        val state = mutableStateOf(ChatUiState(profileName = "Amina Khan", agentAvailable = true))
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            ChatScreen(state.value, ChatActions(onAgent = { state.value = state.value.copy(agent = it) }))
        } }
        compose.onNodeWithText("Hi Amina, what's on your mind?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed()
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.onNodeWithText("What can I tackle for you?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed()
        compose.onNodeWithText("Hi Amina, what's on your mind?").assertDoesNotExist()
        compose.onNode(hasText("Chat") and isSelectable()).performClick()
        compose.onNodeWithText("Hi Amina, what's on your mind?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed()
    }

    @Test fun conversationOptionsRenameTheActiveChatAndKeepItsDraft() {
        val messages = listOf(ChatMessage(id = "answer", text = "Saved answer", isUser = false))
        val active = SavedChat("active", "Dua", "", messages, 1L, customTitle = "Current chat")
        val other = SavedChat("other", "Dua", "", emptyList(), 2L, customTitle = "Other chat")
        var renamed: Pair<String, String>? = null
        compose.mainClock.autoAdvance = false
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            ChatScreen(ChatUiState(chatId = active.id, savedChats = listOf(other, active),
                messages = messages, draft = "Keep this draft"),
                ChatActions(onRenameChat = { id, title -> renamed = id to title; null }))
        } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Rename").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("rename-field").performTextReplacement("Updated title")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Save").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        assertEquals("active" to "Updated title", renamed)
        compose.onNodeWithContentDescription("Message").assertTextContains("Keep this draft")
    }

    @Test fun deleteFromTheHeaderWaitsForTheExistingCacheConfirmation() {
        val messages = listOf(ChatMessage(id = "answer", text = "Saved answer", isUser = false))
        val active = SavedChat("active", "Dua", "", messages, 1L)
        var removed: String? = null
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            ChatScreen(ChatUiState(chatId = active.id, savedChats = listOf(active), messages = messages),
                ChatActions(onDeleteChat = { removed = it }))
        } }
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.onNodeWithText("Remove cached chat").performClick()
        compose.onNodeWithText("Remove cached chat?").assertIsDisplayed()
        assertEquals(null, removed)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, removed)
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.onNodeWithText("Remove cached chat").performClick()
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        assertEquals("active", removed)
    }

    @Test fun conversationDoesNotOverlayHomeTabsOnMessages() {
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            ChatScreen(ChatUiState(messages = listOf(ChatMessage(text = "An actual reply", isUser = false))), ChatActions())
        } }
        compose.onNodeWithText("An actual reply").assertIsDisplayed()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
    }

    @Test fun drawerHasExplicitCloseAndOneExpandableSecondaryNavigationGroup() {
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            ChatScreen(ChatUiState(), ChatActions())
        } }
        compose.onNodeWithContentDescription("Open chat history").performClick()
        compose.onNodeWithContentDescription("Close chat history").assertIsDisplayed()
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.onNodeWithText("Work").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.onNodeWithText("Work").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close chat history").performClick()
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
    }
}

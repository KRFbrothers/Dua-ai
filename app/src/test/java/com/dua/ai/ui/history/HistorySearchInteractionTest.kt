package com.dua.ai.ui.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class HistorySearchInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun chat(id: String, project: String, oldReply: String) = SavedChat(id, "Dua", "",
        listOf(ChatMessage(text = "Chat $id", isUser = true), ChatMessage(text = oldReply, isUser = false),
            ChatMessage(text = "Thanks", isUser = true), ChatMessage(text = "Done", isUser = false)), 1, projectId = project)
    private fun search(value: String) {
        compose.onNodeWithContentDescription("Search chats").performTextReplacement(value)
        compose.mainClock.advanceTimeBy(200)
    }
    private fun awaitText(text: String) {
        compose.mainClock.advanceTimeBy(200)
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun olderReplyShowsMatchSnippetAndOpensTheCorrectChatAndClearRestoresAllRows() {
        val chats = listOf(chat("first", "coding", "Old invoice-2026 reply"), chat("second", "coding", "Unrelated"))
        var opened = ""
        compose.setContent { DuaTheme(LocalPreferences()) { HistoryDrawer(chats, true, {}, { opened = it }, {}, {}, projectId = "coding") } }
        search("INVOICE-2026")
        awaitText("Dua: Old invoice-2026 reply")
        compose.onNodeWithText("Chat second").assertDoesNotExist()
        compose.onNodeWithText("Dua: Old invoice-2026 reply").performClick()
        compose.runOnIdle { assertEquals("first", opened) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Chat second").assertExists()
    }

    @Test fun projectSwitchClearsQueryAndDoesNotRevealAnotherProjectsSearchMatch() {
        val project = mutableStateOf("coding")
        val chats = listOf(chat("code", "coding", "Code detail"), chat("business", "business", "Private invoice-2026"))
        compose.setContent { DuaTheme(LocalPreferences()) { HistoryDrawer(chats, true, {}, {}, {}, {}, projectId = project.value) } }
        search("invoice-2026")
        awaitText("No chats found")
        compose.onNodeWithText("Chat business").assertDoesNotExist()
        compose.runOnIdle { project.value = "business" }
        compose.onNodeWithContentDescription("Search chats").assertTextEquals("")
        compose.onNodeWithText("Chat business").assertExists()
        compose.onNodeWithText("Chat code").assertDoesNotExist()
        search("invoice-2026")
        awaitText("Dua: Private invoice-2026")
    }

    @Test fun changedSnapshotAndRapidQueriesDiscardPreviousMatchesAndShowUpdatedDraft() {
        val chats = mutableStateOf(listOf(chat("code", "coding", "Old invoice-2026")))
        compose.setContent { DuaTheme(LocalPreferences()) { HistoryDrawer(chats.value, true, {}, {}, {}, {}, projectId = "coding") } }
        search("invoice-2026"); awaitText("Dua: Old invoice-2026")
        compose.runOnIdle { chats.value = listOf(chat("code", "coding", "New content").copy(draft = "Prepare business-plan draft")) }
        awaitText("No chats found")
        compose.onNodeWithText("Dua: Old invoice-2026").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search chats").performTextReplacement("business-plan")
        compose.onNodeWithContentDescription("Search chats").performTextReplacement("not-matched")
        compose.mainClock.advanceTimeBy(200)
        awaitText("No chats found")
        compose.onNodeWithText("Draft: Prepare business-plan draft").assertDoesNotExist()
        search("business-plan"); awaitText("Draft: Prepare business-plan draft")
    }
}

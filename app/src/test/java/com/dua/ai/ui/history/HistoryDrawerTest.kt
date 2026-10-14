package com.dua.ai.ui.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.navigation.DuaDestination
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
class HistoryDrawerTest {
    @get:Rule val compose = createComposeRule()
    private fun chat(id: String, title: String, at: Long, pinned: Boolean = false) =
        SavedChat(id, "Dua", "", listOf(ChatMessage(text = "q", isUser = true)), at, customTitle = title, pinned = pinned)
    private val chats = listOf(chat("a", "Alpha roadmap", 1), chat("b", "Beta plugin", 2), chat("c", "Pinned note", 0, pinned = true))

    @Test fun showsReferenceStructureWithRealChatsAndNoSamples() {
        compose.setContent { DuaTheme(LocalPreferences()) { HistoryDrawer(chats = chats) } }
        for (t in listOf("Dua AI", "Images", "Library", "Projects", "Computer", "AI Agent", "Aura", "Chat", "Alpha roadmap", "Beta plugin", "Pinned note"))
            compose.onNodeWithText(t).assertExists()
        compose.onNodeWithText("Replace Dua").assertDoesNotExist()
        val titles = compose.onNodeWithTag("drawer-recent-chats").onChildren().fetchSemanticsNodes()
        assertTrue(titles.isNotEmpty())
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
            assertTrue("touch target ${it.size}", it.size.height >= 48 * 2 - 1)
        }
    }

    @Test fun everyDestinationIsReachableAndAuraExpands() {
        val opened = mutableListOf<DuaDestination>(); var projects = 0
        compose.setContent { DuaTheme(LocalPreferences(theme = "dark")) { HistoryDrawer(onDestination = { opened += it }, onProjects = { projects++ }) } }
        compose.onNodeWithText("Plugins").assertDoesNotExist()
        compose.onNodeWithText("Aura").performClick()
        for (t in listOf("Images", "Library", "Computer", "AI Agent", "Plugins", "Work")) compose.onNodeWithText(t).performScrollTo().performClick()
        compose.onNodeWithText("Projects").performClick()
        compose.runOnIdle {
            assertEquals(listOf(DuaDestination.Images, DuaDestination.Library, DuaDestination.Computer, DuaDestination.Agent, DuaDestination.Plugins, DuaDestination.Work), opened)
            assertEquals(1, projects)
        }
        compose.onNodeWithText("Aura").performClick()
        compose.onNodeWithText("Plugins").assertDoesNotExist()
    }

    @Test fun selectNewChatProfileAndSearchWithoutVoiceCircle() {
        var selected: String? = null; var newChats = 0; var settings = 0
        compose.setContent { DuaTheme(LocalPreferences()) {
            HistoryDrawer(chats = chats, onSelectChat = { selected = it }, onNewChat = { newChats++ }, onOpenSettings = { settings++ }, profileName = "Fareed")
        } }
        compose.onNodeWithText("Beta plugin").performClick()
        compose.onNodeWithTag("drawer-new-chat").performClick()
        compose.onNodeWithContentDescription("Profile & Settings").performClick()
        compose.onNodeWithContentDescription("Voice").assertDoesNotExist()
        compose.onNodeWithContentDescription("Voice mode").assertDoesNotExist()
        compose.runOnIdle { assertEquals("b", selected); assertEquals(1, newChats); assertEquals(1, settings) }
        compose.onNodeWithText("F").assertExists()
        compose.onNodeWithContentDescription("Search chats").performClick()
        compose.onNodeWithTag("drawer-search-field").performTextInput("alpha")
        compose.onNodeWithText("Alpha roadmap").assertExists()
        compose.onNodeWithText("Beta plugin").assertDoesNotExist()
    }

    @Test fun recentListHidesDraftsAndDuplicateIdsButKeepsSeparateChats() {
        val hi = { id: String, at: Long -> SavedChat(id, "Dua", "", listOf(ChatMessage(text = "hi", isUser = true)), at) }
        val list = listOf(hi("h1", 5), hi("h2", 4), hi("h1", 3),
            SavedChat("d1", "Dua", "", emptyList(), 6), SavedChat("d2", "Dua", "unsent text", emptyList(), 7))
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) { HistoryDrawer(chats = list) } }
        compose.onAllNodesWithText("Draft").assertCountEquals(0)
        compose.onAllNodesWithText("hi").assertCountEquals(2)
    }

    private var renamed: Pair<String, String>? = null
    private var pinned: Pair<String, Boolean>? = null
    private var deleted: String? = null
    private fun longPress(title: String) {
        compose.setContent { DuaTheme(LocalPreferences()) {
            HistoryDrawer(chats = chats, pinEnabled = true, onRenameChat = { id, t -> renamed = id to t; null },
                onPinChat = { id, p -> pinned = id to p; null }, onDeleteChat = { deleted = it })
        } }
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
    }

    @Test fun longPressPins() {
        longPress("Alpha roadmap")
        compose.onNodeWithText("Pin", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("a" to true, pinned) }
    }

    @Test fun longPressUnpinsAndDeletes() {
        longPress("Pinned note")
        compose.onNodeWithText("Unpin", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Remove cached chat", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("c", deleted) }
    }

    @Test fun longPressOffersRename() {
        longPress("Beta plugin")
        // The rename dialog's text field never reports idle under Robolectric, so only the entry point is checked here.
        compose.onNodeWithText("Rename", useUnmergedTree = true).assertExists()
        assertNull(renamed)
    }
}

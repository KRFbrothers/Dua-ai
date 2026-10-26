package com.dua.ai.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WideLayoutUiTest {
    @get:Rule val compose = createComposeRule()
    private val chats = listOf(SavedChat("c1", "dua", "", listOf(ChatMessage(text = "Trip to Goa plan", isUser = true)), System.currentTimeMillis()))
    private var opened: String? = null
    private fun show() = compose.setContent {
        DuaTheme(LocalPreferences()) { ChatScreen(ChatUiState(savedChats = chats), ChatActions(onOpenChat = { opened = it })) }
    }

    @Test fun widthClasses() {
        assertEquals(ChatWidth.Compact, WideLayout.widthClass(599.dp))
        assertEquals(ChatWidth.Medium, WideLayout.widthClass(600.dp))
        assertEquals(ChatWidth.Medium, WideLayout.widthClass(839.dp))
        assertEquals(ChatWidth.Expanded, WideLayout.widthClass(840.dp))
        assertTrue(WideLayout.permanentHistory(1280.dp, featureOpen = false))
        assertFalse(WideLayout.permanentHistory(1280.dp, featureOpen = true))
        assertFalse(WideLayout.permanentHistory(700.dp, featureOpen = false))
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun tabletShowsHistoryBesideChatWithoutOpeningTheDrawer() {
        show()
        compose.onNodeWithTag("history-panel").assertIsDisplayed()
        compose.onNodeWithText("Trip to Goa plan").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("c1", opened) }
        // The menu button hides and shows the panel.
        compose.onNodeWithContentDescription("Dua AI, chats and settings").performClick()
        compose.onNodeWithTag("history-panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI, chats and settings").performClick()
        compose.onNodeWithTag("history-panel").assertIsDisplayed()
    }

    @Config(qualifiers = "w700dp-h1000dp-mdpi")
    @Test fun mediumKeepsTheModalDrawer() {
        show()
        compose.onNodeWithTag("history-panel").assertDoesNotExist()
        compose.onNodeWithText("Trip to Goa plan").assertIsNotDisplayed()
    }

    @Config(qualifiers = "w411dp-h891dp-mdpi")
    @Test fun phoneIsUnchanged() {
        show()
        compose.onNodeWithTag("history-panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI, chats and settings").performClick()
        compose.onNodeWithText("Trip to Goa plan").assertIsDisplayed()
        compose.onNodeWithTag("history-panel").assertDoesNotExist()
    }
}

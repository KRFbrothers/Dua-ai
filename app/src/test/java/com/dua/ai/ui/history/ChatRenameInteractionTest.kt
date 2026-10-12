package com.dua.ai.ui.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ChatRenameInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val original = SavedChat("chat", "Dua", "", listOf(ChatMessage(text = "Original question", isUser = true)), 1)
    private fun openRename() {
        compose.onNodeWithText(original.title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText("Rename", useUnmergedTree = true).performClick()
    }
    private fun name() = compose.onNode(hasSetTextAction() and hasContentDescription("Chat name"))
    private fun replaceName(value: String) = name().performTextReplacement(value)

    @Test fun renameShowsUpdatedTitleAndKeepsConversationIntact() {
        val chats = mutableStateOf(listOf(original))
        compose.setContent { DuaTheme(LocalPreferences()) {
            HistoryDrawer(chats.value, true, {}, {}, {}, {}, onRenameChat = { id, title ->
                chats.value = chats.value.map { if (it.id == id) it.copy(customTitle = title.trim()) else it }; null
            })
        } }
        openRename()
        replaceName("Business notes")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Business notes").assertIsDisplayed()
        compose.onNodeWithText("Rename chat").assertDoesNotExist()
        assertEquals(original.messages, chats.value.single().messages)
    }
    @Test fun invalidNameDisablesSaveAndCancelMakesNoChange() {
        var calls = 0
        compose.setContent { DuaTheme(LocalPreferences()) {
            HistoryDrawer(listOf(original), true, {}, {}, {}, {}, onRenameChat = { _, _ -> calls++; null })
        } }
        openRename()
        replaceName(" ")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        replaceName("x".repeat(121))
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText(original.title).assertIsDisplayed()
    }
    @Test fun failedSaveKeepsDialogAndEditedNameForRetry() {
        compose.setContent { DuaTheme(LocalPreferences()) {
            HistoryDrawer(listOf(original), true, {}, {}, {}, {}, onRenameChat = { _, _ -> "Finish sync, then try again." })
        } }
        openRename()
        replaceName("New title")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Finish sync, then try again.").assertIsDisplayed()
        name().assertTextContains("New title")
        compose.onNodeWithText("Cancel").performClick()
    }
    @Test fun lightThemeDialogKeepsEditorAndActionsReachableAtLargeTextSize() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DuaTheme(LocalPreferences(theme = "light")) { RenameChatDialog("Notes", null, {}, {}, {}) }
            }
        }
        name().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}

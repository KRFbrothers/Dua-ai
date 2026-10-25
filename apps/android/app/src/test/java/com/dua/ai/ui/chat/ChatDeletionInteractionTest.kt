package com.dua.ai.ui.chat

import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.viewmodel.ChatViewModel
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
class ChatDeletionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val chat = SavedChat("target", "Dua", "", listOf(ChatMessage(text = "Question", isUser = true)), 1, customTitle = "Target conversation")
    private fun openDelete() {
        compose.onNodeWithText(chat.title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText("Delete chat", useUnmergedTree = true).performClick()
    }
    @Test fun deleteRequiresConfirmationAndCancelMakesNoRequest() {
        var requests = 0
        compose.setContent { DuaTheme(LocalPreferences()) {
            ChatScreen(ChatUiState(savedChats = listOf(chat), serverChatDeletionEnabled = true),
                ChatActions(onDeleteServerChat = { requests++ }), initialDrawerValue = DrawerValue.Open)
        } }
        openDelete()
        assertEquals(0, requests)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, requests)
        openDelete()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, requests)
    }
    @Test fun failedDeletionRetainsTheTargetAndOffersRetry() {
        var requests = 0
        val state = mutableStateOf(ChatUiState(savedChats = listOf(chat), serverChatDeletionEnabled = true))
        compose.setContent { DuaTheme(LocalPreferences()) {
            ChatScreen(state.value, ChatActions(onDeleteServerChat = {
                requests++; state.value = state.value.copy(chatDeletion = ChatViewModel.ChatDeletionState(it, error = "Connection lost. Retry."))
            }), initialDrawerValue = DrawerValue.Open)
        } }
        openDelete(); compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Connection lost. Retry.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(2, requests)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText(chat.title).assertExists()
    }
    @Test fun cacheRemovalStaysAvailableWhenServerDeletionIsUnsupported() {
        compose.setContent { DuaTheme(LocalPreferences()) {
            ChatScreen(ChatUiState(savedChats = listOf(chat)), ChatActions(), initialDrawerValue = DrawerValue.Open)
        } }
        compose.onNodeWithText(chat.title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText("Delete chat", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Remove cached chat", useUnmergedTree = true).assertExists()
    }
    @Test fun busyDeletionDisablesRepeatedConfirmationAndClosesOnConfirmedSuccess() {
        val state = mutableStateOf(ChatUiState(savedChats = listOf(chat), serverChatDeletionEnabled = true))
        compose.setContent { DuaTheme(LocalPreferences()) {
            ChatScreen(state.value, ChatActions(onDeleteServerChat = {
                state.value = state.value.copy(chatDeletion = ChatViewModel.ChatDeletionState(it, busy = true))
            }), initialDrawerValue = DrawerValue.Open)
        } }
        openDelete(); compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").assertIsNotEnabled(); compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Deleting…").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(savedChats = emptyList(), chatDeletion = ChatViewModel.ChatDeletionState(chat.id, completed = true)) }
        compose.onNodeWithText("Delete chat?").assertDoesNotExist()
    }
    @Test fun lightLargeTextConfirmationKeepsActionsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DuaTheme(LocalPreferences(theme = "light")) {
                    ChatScreen(ChatUiState(savedChats = listOf(chat), serverChatDeletionEnabled = true), ChatActions(), initialDrawerValue = DrawerValue.Open)
                }
            }
        }
        openDelete()
        compose.onNodeWithText("Delete").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}

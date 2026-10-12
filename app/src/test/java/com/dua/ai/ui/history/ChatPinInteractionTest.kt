package com.dua.ai.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class ChatPinInteractionTest(private val dark: Boolean, private val small: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},small={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private val chat = SavedChat("target", "Dua", "", listOf(ChatMessage(text = "Question", isUser = true)), 1, customTitle = "Target notes")
    private fun show(chats: androidx.compose.runtime.MutableState<List<SavedChat>>, enabled: Boolean = true,
        action: (String, Boolean) -> String? = { id, pin -> chats.value = chats.value.map { if (it.id == id) it.copy(pinned = pin) else it }; null }) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (small) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(if (small) 320.dp else 360.dp, if (small) 568.dp else 752.dp)) {
                        HistoryDrawer(chats.value, true, {}, {}, {}, {}, projectId = "personal", pinEnabled = enabled, onPinChat = action)
                    }
                }
            }
        }
    }
    private fun menu() { compose.onNodeWithText(chat.title).performSemanticsAction(SemanticsActions.OnLongClick) { it() } }
    @Test fun pinAndUnpinUpdateGroupingAndKeepSearchProjectScoped() {
        val chats = mutableStateOf(listOf(chat, chat.copy(id = "business", projectId = "business", customTitle = "Business notes", pinned = true)))
        show(chats); menu(); compose.onNodeWithText("Pin", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Pinned").assertIsDisplayed()
        compose.onNodeWithText(chat.title).assertIsDisplayed(); compose.onNodeWithText("Business notes").assertDoesNotExist()
        assertTrue(chats.value.first().pinned); assertEquals(chat.updatedAt, chats.value.first().updatedAt)
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/chat-pins-${if (dark) "dark" else "light"}-${if (small) "small-font2" else "normal"}.png")
            file.parentFile?.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        }
        compose.onNodeWithContentDescription("Search chats").performTextReplacement("target")
        compose.mainClock.advanceTimeBy(250)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Searching chats…").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(chat.title).assertExists(); compose.onNodeWithText("Pinned").assertIsDisplayed()
        menu(); compose.onNodeWithText("Unpin", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Pinned").assertDoesNotExist(); assertFalse(chats.value.first().pinned)
        assertEquals(chat.messages, chats.value.first().messages)
    }
    @Test fun failedPinShowsAnActionableErrorAndLeavesTheChatUnchanged() {
        val chats = mutableStateOf(listOf(chat))
        show(chats, action = { _, _ -> "Finish the current reply or sync action, then try again." })
        menu(); compose.onNodeWithText("Pin", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Pin could not be updated").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        assertEquals(listOf(chat), chats.value)
    }
    @Test fun unsupportedBackendDisablesPin() {
        val chats = mutableStateOf(listOf(chat))
        show(chats, enabled = false); menu()
        compose.onNode(hasText("Pin") and hasAnyAncestor(isNotEnabled()), useUnmergedTree = true).assertExists()
    }
    @Test fun accessibleUnpinUsesTheSameAction() {
        val chats = mutableStateOf(listOf(chat.copy(pinned = true)))
        show(chats)
        val actions = compose.onNodeWithText(chat.title).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first { it.label == "Unpin chat" }.action()) }
        assertFalse(chats.value.single().pinned)
        compose.onNodeWithText("Pinned").assertDoesNotExist()
    }
}

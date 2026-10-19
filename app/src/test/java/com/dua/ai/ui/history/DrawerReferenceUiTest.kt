package com.dua.ai.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
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
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerReferenceUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun chat(id: String, title: String, pinned: Boolean = false) = SavedChat(id, "Dua", "",
        listOf(ChatMessage(text = "My actual message", isUser = true)), 1, customTitle = title, pinned = pinned)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.requiredSize(if (large) 253.dp else 286.dp, if (large) 568.dp else 800.dp)) { content() }
            }
        }
    }
    private fun capture(label: String) {
        val file = File("build/approved-ui/drawer-reference-$label-$dark-$large.png")
        file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun newChatHeaderAndRealProfileFooterStaySeparateFromScrollableHistory() {
        var newChats = 0; var settings = 0
        show { HistoryDrawer(chats = listOf(chat("a", "My first conversation"), chat("b", "My saved question", true)),
            profileName = "Signed-in Owner", onNewChat = { newChats++ }, onOpenSettings = { settings++ }) }
        compose.onNodeWithTag("drawer-new-chat").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        val newChat = compose.onNodeWithTag("drawer-new-chat").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("drawer-body").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("drawer-profile-footer").fetchSemanticsNode().boundsInRoot
        assertTrue(newChat.bottom <= body.top)
        assertTrue(body.bottom <= footer.top)
        compose.onNodeWithContentDescription("Profile & Settings").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Open drawer settings").assertIsDisplayed().assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Signed-in Owner").assertExists()
        compose.onNodeWithTag("drawer-account-avatar", useUnmergedTree = true).assertWidthIsEqualTo(44.dp)
        compose.onNodeWithTag("account-avatar", useUnmergedTree = true).assertDoesNotExist()
        val nameLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Signed-in Owner", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nameLayout) }
        val nameText = nameLayout.single()
        assertFalse("Profile name overflow: size=${nameText.size}, width=${nameText.didOverflowWidth}, height=${nameText.didOverflowHeight}, constraints=${nameText.layoutInput.constraints}", nameText.hasVisualOverflow)
        compose.onNodeWithText("PRO").assertDoesNotExist()
        compose.onNodeWithText("My saved question").performScrollTo().assertIsDisplayed()
        val lastChat = compose.onNodeWithText("My saved question").fetchSemanticsNode().boundsInRoot
        assertTrue(lastChat.bottom <= footer.top)
        capture("profile-history")
        assertEquals(1, newChats); assertEquals(2, settings)
    }

    @Test fun pinnedFilterAndInlinePinUseRealStateWithoutOpeningTheChat() {
        val chats = mutableStateOf(listOf(chat("a", "Owner alpha"), chat("b", "Owner beta", true)))
        var opened = 0
        show { HistoryDrawer(chats.value, pinEnabled = true, onSelectChat = { opened++ }, onPinChat = { id, pinned ->
            chats.value = chats.value.map { if (it.id == id) it.copy(pinned = pinned) else it }; null
        }) }
        compose.onNodeWithContentDescription("Filter chats").performScrollTo().performClick()
        compose.onNodeWithText("Pinned chats").performClick()
        compose.onNodeWithText("Owner alpha").assertDoesNotExist()
        compose.onNodeWithText("Owner beta").performScrollTo().assertIsDisplayed()
        capture("pinned")
        compose.onNodeWithContentDescription("Unpin Owner beta").performScrollTo().performClick()
        compose.onNodeWithText("No pinned chats").assertExists()
        assertFalse(chats.value.single { it.id == "b" }.pinned)
        assertEquals(0, opened)
        compose.onNodeWithContentDescription("Filter chats").performScrollTo().performClick()
        compose.onNodeWithText("All chats").performClick()
        compose.onNodeWithContentDescription("Pin Owner alpha").performScrollTo().performClick()
        assertTrue(chats.value.single { it.id == "a" }.pinned)
        compose.onNodeWithContentDescription("Search chats").performClick()
        compose.onNodeWithTag("drawer-search-field").performTextInput("beta")
        compose.onNodeWithText("Owner beta").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Owner alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open drawer settings").assertIsDisplayed()
        capture("search")
        compose.onNodeWithContentDescription("Close chat search").performClick()
        compose.onNodeWithText("Owner alpha").assertExists()
        assertEquals(0, opened)
    }

    @Test fun emptyGuestDrawerHasNoInventedChatsOrSubscriptionAndRespectsDisabledNewChat() {
        var created = 0
        show { HistoryDrawer(newChatEnabled = false, onNewChat = { created++ }) }
        compose.onNodeWithTag("drawer-new-chat").assertIsNotEnabled()
        compose.onNodeWithText("No chats yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Profile & settings").assertExists()
        compose.onNodeWithText("PRO").assertDoesNotExist()
        compose.onNodeWithContentDescription("Anonymous profile").assertExists()
        assertEquals(0, created)
        capture("empty")
    }
}

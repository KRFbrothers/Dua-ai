package com.dua.ai.ui.history

import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
@Config(sdk = [34], qualifiers = "w393dp-h650dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerSearchUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun chat(id: String, title: String, at: Long = 1, pinned: Boolean = false) = SavedChat(
        id, "Dua", "", listOf(ChatMessage(text = "Saved user message", isUser = true)), at,
        customTitle = title, pinned = pinned,
    )
    private fun show(chats: List<SavedChat>, onSelect: (String) -> Unit = {}) {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.width(if (large) 253.dp else 286.dp).fillMaxHeight()) {
                        HistoryDrawer(chats, currentChatId = "a", onSelectChat = onSelect,
                            drawerState = rememberDrawerState(DrawerValue.Open))
                    }
                }
            }
        }
        keyboard(0)
    }
    private fun keyboard(height: Int) {
        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                .setVisible(WindowInsetsCompat.Type.ime(), height > 0).build())
        }
        compose.waitForIdle()
    }
    private fun capture(label: String) {
        val file = File("build/approved-ui/drawer-search-$label-$dark-$large.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun keyboardSearchShowsResultsImmediatelyAndClearKeepsInputReady() {
        val opened = mutableListOf<String>()
        show(listOf(chat("a", "Alpha plan"), chat("b", "Beta question")), opened::add)
        compose.onNodeWithContentDescription("Search chats").performClick()
        compose.onNodeWithTag("drawer-search-field").assertIsFocused().performTextInput("Alpha")
        keyboard(240)
        compose.onNodeWithText("Library").assertDoesNotExist()
        compose.onNodeWithText("Alpha plan").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("Beta question").assertDoesNotExist()
        val result = compose.onNodeWithText("Alpha plan").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("drawer-body").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("drawer-bottom-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(result.top >= body.top && result.bottom <= footer.top)
        assertTrue("Footer must clear IME and use max(IME, navigation)", footer.bottom <= 650f - 240f)
        compose.onNodeWithTag("drawer-new-chat").assertDoesNotExist()
        compose.onNodeWithTag("drawer-profile").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        capture("keyboard")
        compose.onNodeWithContentDescription("Clear chat search").performClick()
        compose.onNodeWithTag("drawer-search-field").assertIsFocused()
        assertEquals("", compose.onNodeWithTag("drawer-search-field").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Beta question").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("drawer-search-field").performTextInput("No such title")
        compose.onNodeWithText("No matching chats").assertIsDisplayed()
        compose.onNodeWithTag("drawer-search-field").performImeAction()
        compose.onNodeWithTag("drawer-search-field").assertIsNotFocused().assertTextContains("No such title")
        compose.onNodeWithContentDescription("Clear chat search").performClick()
        compose.onNodeWithText("Alpha plan").performScrollTo().performClick()
        assertEquals(listOf("a"), opened)
        compose.onNodeWithContentDescription("Close chat search").performClick()
        keyboard(0)
        compose.onNodeWithTag("drawer-search-field").assertDoesNotExist()
        compose.onNodeWithText("Library").assertExists()
    }

    @Test fun closingSearchRestoresBrowsePositionAndChangingQueryStartsAtFirstResult() {
        val resolver = compose.activity.contentResolver
        val previous = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            show((1..30).map { chat("$it", "Conversation $it", at = it.toLong()) })
            compose.onNodeWithText("Conversation 1").performScrollTo().assertIsDisplayed()
            val before = compose.onNodeWithText("Conversation 1").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("Search chats").performClick()
            compose.onNodeWithText("Conversation 30").assertIsDisplayed()
            compose.onNodeWithText("Conversation 1").performScrollTo()
            compose.onNodeWithTag("drawer-search-field").performTextInput("30")
            compose.onNodeWithText("Conversation 30").assertIsDisplayed()
            capture("results")
            compose.onNodeWithContentDescription("Close chat search").performClick()
            compose.onNodeWithText("Conversation 1").assertIsDisplayed()
            val after = compose.onNodeWithText("Conversation 1").fetchSemanticsNode().boundsInRoot
            assertEquals(before.top, after.top, 1f)
            assertEquals(before.bottom, after.bottom, 1f)
            compose.onNodeWithText("More").performScrollTo().performClick()
            compose.onNodeWithText("Work").assertExists()
            compose.onNodeWithTag("drawer-profile").assertIsDisplayed()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous)
        }
    }

    @Test fun backAfterKeyboardDismissalClosesSearchBeforeTheDrawer() {
        show(listOf(chat("a", "Alpha plan"), chat("b", "Beta question")))
        compose.onNodeWithContentDescription("Search chats").performClick()
        compose.onNodeWithTag("drawer-search-field").performTextInput("Alpha")
        keyboard(240)
        compose.onNodeWithTag("drawer-search-field").assertTextContains("Alpha")
        // Insets simulate the platform's first Back hiding the IME. The next dispatcher
        // event must close search while retaining the drawer and its real navigation.
        keyboard(0)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("drawer-search-field").assertDoesNotExist()
        compose.onNodeWithTag("history-drawer").assertIsDisplayed()
        compose.onNodeWithText("Library").assertIsDisplayed()
        capture("back-to-browse")
        compose.onNodeWithContentDescription("Search chats").performClick()
        compose.onNodeWithTag("drawer-search-field").assertIsFocused().assertTextContains("")
        compose.onNodeWithText("Beta question").performScrollTo().assertIsDisplayed()
    }
}

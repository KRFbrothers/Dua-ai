package com.dua.ai.ui.history

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.SheetSizes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drawer long-press menu as a Dabba sheet keeps Rename / Pin / Unpin / Delete. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatOptionsSheetTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun everyActionStaysAndIsTouchSized() {
        val hits = mutableListOf<String>()
        val pinnedState = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                ChatOptionsContent("Trip plan", pinnedState.value, pinEnabled = true, serverDeletion = true,
                    onRename = { hits += "rename" }, onPin = { hits += "pin"; pinnedState.value = !pinnedState.value }, onDelete = { hits += "delete" })
            }
        }
        compose.onNodeWithText("Trip plan").assertIsDisplayed()
        compose.onNodeWithText("Rename").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Pin").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Unpin").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Delete chat").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(listOf("rename", "pin", "pin", "delete"), hits)
    }
}

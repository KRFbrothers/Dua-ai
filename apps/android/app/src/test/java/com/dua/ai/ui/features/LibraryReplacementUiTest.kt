package com.dua.ai.ui.features

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.library.LibraryItem
import com.dua.ai.library.LibraryKind
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryReplacementUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingEmptyAndFailedInventoryHaveDifferentStatesAndRetry() {
        val busy = mutableStateOf(true)
        val notice = mutableStateOf("")
        val failed = mutableStateOf(false)
        var refreshes = 0
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            LibraryContent(emptyList(), busy.value, notice.value, {}, {}, {}, { refreshes++ }, loading = busy.value, loadFailed = failed.value)
        } }
        compose.onNodeWithTag("library-loading").assertExists()
        compose.onNodeWithTag("library-empty").assertDoesNotExist()
        compose.onNodeWithContentDescription("Upload files").assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithText("Your files, images and code snippets will appear here.").assertIsDisplayed()
        compose.runOnIdle { notice.value = "Connection failed"; failed.value = true }
        compose.onNodeWithText("Couldn't load your library. Retry to reconnect.").assertIsDisplayed()
        compose.onNodeWithText("Retry cloud files").performClick()
        assertEquals(1, refreshes)
    }

    @Test fun documentAndMediaOpenTheOriginalItemAndSearchDoesNotDuplicateInventory() {
        val document = LibraryItem("file", "Meeting notes", kind = LibraryKind.Files)
        val image = LibraryItem("image", "Design sketch", kind = LibraryKind.Images)
        var opened: LibraryItem? = null
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            LibraryContent(listOf(document, image), false, "", {}, {}, { opened = it })
        } }
        compose.onNodeWithText("Documents").assertIsDisplayed()
        compose.onNodeWithText("Media").assertIsDisplayed()
        compose.onAllNodesWithTag("library-item-file").assertCountEquals(1)
        compose.onNodeWithTag("library-item-file").performClick()
        assertEquals(document, opened)
        compose.onNodeWithTag("library-item-image").performClick()
        assertEquals(image, opened)
        compose.onNodeWithTag("library-search").performTextInput("Meeting")
        compose.onNodeWithTag("library-item-image").assertDoesNotExist()
        compose.onAllNodesWithTag("library-item-file").assertCountEquals(1)
    }

    @Test fun refreshFailureKeepsPreviouslyLoadedFilesAvailable() {
        var opened = ""
        val file = LibraryItem("local", "Local document", kind = LibraryKind.Files)
        compose.setContent { DuaTheme(LocalPreferences(theme = "dark")) {
            LibraryContent(listOf(file), false, "Cloud refresh failed", {}, {}, { opened = it.id }, loadFailed = true)
        } }
        compose.onNodeWithTag("library-item-local").performClick()
        assertEquals("local", opened)
        compose.onNodeWithTag("library-loading").assertDoesNotExist()
    }
}

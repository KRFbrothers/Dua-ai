package com.dua.ai.ui.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class AttachmentContextUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactToolsAndLimitsDiscloseWithoutStartingAPicker() {
        var picks = 0
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            AttachmentSheetContent(null, true, true, { picks++ }, { picks++ }, { picks++ }, {}, compact = true)
        } }
        compose.onNodeWithText("Read a link").assertDoesNotExist()
        compose.onNodeWithText("Tools").performClick()
        compose.onNodeWithText("Read a link").assertIsDisplayed()
        compose.onNodeWithText("File limits").performScrollTo().performClick()
        compose.onNodeWithText("Up to 3 files, 2 MB in total:", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(0, picks)
        compose.onNodeWithText("Photos").performScrollTo().performClick()
        assertEquals(1, picks)
    }

    @Test fun busySheetBlocksPickersAndUnavailableLinkReadingCannotToggle() {
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            AttachmentSheetContent(null, false, false, {}, {}, {}, {})
        } }
        for (label in listOf("Photos", "Camera", "Files", "Read a link"))
            compose.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
    }
}

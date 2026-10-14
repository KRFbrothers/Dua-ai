package com.dua.ai.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.SheetDarkColors
import com.dua.ai.ui.theme.SheetLightColors
import com.dua.ai.ui.theme.SheetRowStyle
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** + sheet keeps every action in the derived Dabba sheet style, light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetDabbaUiTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun attachmentSheetKeepsEveryActionWithDabbaTokens() {
        val hits = mutableListOf<String>()
        var container = Color.Unspecified
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                container = SheetTheme.colors.container
                AttachmentSheetContent(webMode = null, pageReadingAvailable = true, enabled = true,
                    onCamera = { hits += "camera" }, onPhotos = { hits += "photos" }, onFiles = { hits += "files" },
                    onReadLink = { hits += "link:$it" }, onAudio = { hits += "audio" },
                    modes = AttachModes(imagine = "Make images", build = "Make an app", toggles = listOf(
                        ModeToggle("Think", "Think before answering", Icons.Outlined.Lightbulb, false) { hits += "think:$it" },
                        ModeToggle("Search", "Search the web", Icons.Outlined.Public, false) { hits += "search:$it" })),
                    onImagine = { hits += "imagine:$it" }, onBuild = { hits += "build" }, onTurnOffModes = { hits += "off" })
            }
        }
        assertEquals(if (dark) SheetDarkColors.container else SheetLightColors.container, container)
        assertEquals(16.sp, SheetRowStyle.fontSize)
        assertEquals(FontWeight.SemiBold, SheetRowStyle.fontWeight)
        assertEquals(22.dp, SheetSizes.rowIcon)
        compose.onNodeWithText("Add to chat").assertIsDisplayed()
        listOf("Camera", "Photos", "Files", "Audio", "Read a link", "Think", "Search", "Imagine", "Build", "Turn off all modes").forEach { label ->
            val node = compose.onNodeWithText(label, useUnmergedTree = false).performScrollTo()
            node.performClick()
            val h = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot.height
            assertTrue("$label touch target", with(compose.density) { h >= 48.dp.toPx() - 0.5f })
        }
        assertEquals(listOf("camera", "photos", "files", "audio", "link:true", "think:true", "search:true", "imagine:true", "build", "off"), hits)
    }
}

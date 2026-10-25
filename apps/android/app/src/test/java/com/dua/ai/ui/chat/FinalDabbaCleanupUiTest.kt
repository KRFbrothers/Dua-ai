package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.imagine.ImagineConfig
import com.dua.ai.imagine.ImagineInfo
import com.dua.ai.imagine.ImagineOptions
import com.dua.ai.imagine.ImagineProvider
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.SheetDarkColors
import com.dua.ai.ui.theme.SheetLightColors
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Imagine options and the shared Dabba dropdown keep every action, 48 dp targets and sheet tokens, light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FinalDabbaCleanupUiTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()
    private val prefs get() = LocalPreferences(theme = if (dark) "dark" else "light")
    private val config = ImagineConfig(imageEnabled = true, videoEnabled = true,
        providers = listOf(ImagineProvider("xai", "Grok Imagine", "m1"), ImagineProvider("gemini", "Gemini", "m2")))

    @Test fun imagineOptionsKeepEveryChoice() {
        var options by mutableStateOf(ImagineOptions())
        var done = 0
        var container = Color.Unspecified
        compose.setContent {
            DuaTheme(prefs) {
                container = SheetTheme.colors.container
                ImagineOptionsContent(options, config, null, false, onChange = { options = it }, onDismiss = { done++ })
            }
        }
        assertEquals((if (dark) SheetDarkColors else SheetLightColors).container, container)
        compose.onNodeWithText("Imagine").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("2 images").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(2, options.count)
        compose.onNodeWithContentDescription("Use Gemini").performScrollTo().assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals("gemini", options.provider)
        compose.onNodeWithContentDescription("Create Video").assertHeightIsAtLeast(SheetSizes.touch).assertIsNotSelected().performClick()
        assertEquals(ImagineInfo.VIDEO, options.kind)
        compose.onNodeWithContentDescription("Create Video").assertIsSelected()
        compose.onNodeWithContentDescription("8 seconds").performScrollTo().assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithContentDescription("Resolution 480p").performScrollTo().performClick()
        assertEquals(8, options.duration); assertEquals("480p", options.resolution)
        compose.onNodeWithText("Done").performScrollTo().assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(1, done)
    }

    @Test fun dabbaDropdownRowsAreTallAndClickable() {
        val hits = mutableListOf<String>()
        compose.setContent {
            DuaTheme(prefs) {
                Box { DuaDropdownMenu(expanded = true, onDismissRequest = {}) {
                    DuaDropdownItem("Open in chat", onClick = { hits += "open" }, icon = Icons.Outlined.Folder)
                    DuaDropdownItem("Business", onClick = { hits += "business" }, selected = true)
                } }
            }
        }
        compose.onNodeWithText("Open in chat").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        compose.onNodeWithText("Business").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(listOf("open", "business"), hits)
    }
}

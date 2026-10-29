package com.dua.ai.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
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
@Config(sdk = [34], qualifiers = "w320dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPolishUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun backAndAboutStayReachableWhileRealSettingsScrollBelowTheHeader() {
        var backs = 0
        var opened: DuaDestination? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    SettingsContent(SettingsUiState(theme = "system", voice = "Device voice"),
                        onBack = { backs++ }, onOpen = { opened = it })
                }
            }
        }
        val before = compose.onNodeWithContentDescription("Back").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("History and data").performScrollTo().performClick()
        assertEquals(DuaDestination.HistoryData, opened)
        val back = compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            .assertWidthIsAtLeast(Sizes.touchTarget).assertHeightIsAtLeast(Sizes.touchTarget)
        assertEquals(before.top, back.fetchSemanticsNode().boundsInRoot.top, 1f)
        val file = File("build/approved-ui/consistency-settings-$dark-$large.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
        compose.onNodeWithContentDescription("About Dua AI").assertIsDisplayed().performClick()
        compose.onNodeWithText("About Dua AI").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("About Dua AI").assertDoesNotExist()
        back.performClick()
        assertEquals(1, backs)
        compose.onNodeWithText("Personalization").performScrollTo()
        compose.onNodeWithText("Match system", substring = true).assertIsDisplayed()
    }
}

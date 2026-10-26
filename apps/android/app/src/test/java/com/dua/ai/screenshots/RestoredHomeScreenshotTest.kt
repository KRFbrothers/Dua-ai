package com.dua.ai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Live original Dua Home semantics and captures in both themes and at small widths. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RestoredHomeScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))
    }

    @get:Rule val compose = createComposeRule()
    private val mode get() = if (dark) "dark" else "light"
    private val home = HomeScreenshotTest.home.copy(profileName = "Fareed Ahmad")

    private fun show(state: ChatUiState = home) {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ChatScreen(state, ChatActions())
                }
            }
        }
        compose.onNodeWithTag("home-mode-switcher").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val file = File("build/outputs/roborazzi/restored_home/${name}_$mode.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun emptyHome() {
        show()
        compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add photos and files").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertDoesNotExist()
        capture("empty_home")
    }

    @Test fun emptyHomeNoName() {
        show(home.copy(profileName = ""))
        compose.onNodeWithContentDescription("Dua AI").assertExists()
        capture("empty_home_no_name")
    }

    @Test fun homeWithDraft() {
        val draft = "Plan a 3-day study schedule for physics"
        show(home.copy(draft = draft))
        compose.onNodeWithContentDescription("Message").assertTextContains(draft)
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voice mode").assertDoesNotExist()
        capture("home_with_draft")
    }

    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    @Test fun homeSmall() {
        show()
        compose.onNodeWithTag("composer-container").assertIsDisplayed()
        capture("home_small")
    }
}

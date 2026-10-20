package com.dua.ai.ui


import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
class AppPausedScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsServerMessageAndRetries() {
        var retries = 0
        compose.setContent { DuaTheme(LocalPreferences()) { AppPausedScreen("Back at 6 pm", checking = false, onRetry = { retries++ }) } }
        compose.onNodeWithText("App paused").assertIsDisplayed()
        compose.onNodeWithText("Back at 6 pm").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun retryIsDisabledWhileChecking() {
        compose.setContent { DuaTheme(LocalPreferences(theme = "dark")) { AppPausedScreen("Soon", checking = true, onRetry = {}) } }
        compose.onNode(hasClickAction()).assertIsNotEnabled()
    }
}

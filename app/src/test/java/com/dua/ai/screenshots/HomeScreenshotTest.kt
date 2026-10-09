package com.dua.ai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dua.ai.imagine.ImagineConfig
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.BuildModeConfig
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Home (new chat) screen as production serves it: Dua selected, Run code, Imagine and Build offered
 * by the server, native search. Dark, light and a small 320 × 568 dp phone.
 * Record with: ./gradlew :app:recordRoborazziDebug -Pdua.screenshots
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HomeScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))

        val home = ChatUiState(greeting = "Good afternoon, Fareed", suggestions = listOf(com.dua.ai.model.PromptSuggestion("Write code snippet", "How do I build a Card in Compose?", "Coding")), webSearchConfigured = true,
            pageReadingAvailable = true, storedFiles = true,
            codeConfig = CodeExecutionConfig(true, providers = setOf("Dua", "Gemini")),
            imagineConfig = ImagineConfig(imageEnabled = true),
            buildConfig = BuildModeConfig(true))
    }

    @get:Rule val compose = createComposeRule()
    private val mode get() = if (dark) "dark" else "light"

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home/${name}_$mode.png")
    }

    @Test fun home() = shot("home") { ChatScreen(home, ChatActions()) }

    @Test fun homeWithDraft() = shot("home_draft") { ChatScreen(home.copy(draft = "Plan a 3-day study schedule for physics", reasoning = true), ChatActions()) }

    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    @Test fun homeSmall() = shot("home_small") { ChatScreen(home, ChatActions()) }
}


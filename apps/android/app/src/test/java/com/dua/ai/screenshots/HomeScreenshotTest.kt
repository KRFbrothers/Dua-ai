package com.dua.ai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.setup.ProviderCapability
import com.dua.ai.ui.chat.ChatSheet
import com.dua.ai.ui.features.ComputerScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
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

/** Live supplied Home in both themes; record with :app:recordRoborazziDebug -Pdua.screenshots. */
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
            buildConfig = BuildModeConfig(true),
            providers = listOf(
                ProviderCapability("Dua", true, "gemini-2.5-flash", true),
                ProviderCapability("ChatGPT", true, "gpt-5-mini", true),
                ProviderCapability("Gemini", true, "gemini-2.5-flash", true),
                ProviderCapability("Claude", true, "claude-sonnet-4-5", true),
                ProviderCapability("Grok", false, "", false),
                ProviderCapability("Kimi", false, "", false),
            ))

        val conversation = home.copy(webMode = "search", messages = listOf(
            ChatMessage(id = "u1", text = "What changed in Android 16 for foreground services?", isUser = true),
            ChatMessage(id = "m1", text = "Android 16 tightens a few rules:\n\n- **Job quotas** now apply to jobs started from a foreground service.\n- **Health** and **data sync** types need a matching permission.\n\nWant a checklist for your app?", isUser = false, model = "Dua"),
        ))
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

    private fun screen(name: String, content: @Composable () -> Unit) {
        compose.setContent { DuaTheme(LocalPreferences(theme = mode)) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/home/${name}_$mode.png")
    }

    @Test fun home() = shot("home") { ChatScreen(home, ChatActions()) }

    @Test fun namedHomeUsesRealProfileAndSuppliedControls() {
        compose.setContent { DuaTheme(LocalPreferences(theme = mode)) {
            ChatScreen(home.copy(profileName = "Fareed Ahmad"), ChatActions())
        } }
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
        compose.onNodeWithText("Hello Fareed!", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home/supplied_named_$mode.png")
    }

    @Test fun modelPicker() = screen("model_picker") { ChatScreen(home, ChatActions(), initialSheet = ChatSheet.Model) }

    @Test fun pillOtherModel() = shot("home_model_claude") { ChatScreen(home.copy(model = "Claude"), ChatActions()) }

    @Test fun computer() = shot("computer") { ComputerScreen(onBack = {}) }

    /** The logo's light sweep part-way through (9 s idle + 0.55 s into the 1.2 s sweep), on the real clock. */
    /** Swipe up on the input card opens the + sheet. */
    @Test fun conversationOneMode() = shot("conversation_search") { ChatScreen(conversation, ChatActions()) }

    @Test fun plusSheet() = screen("plus_sheet") { ChatScreen(conversation, ChatActions(), initialSheet = ChatSheet.Attach) }

    /** +, mic and send keep their 36-38 dp look but take taps across a full 48 dp. */
    @Test fun composerTouchTargets() {
        compose.setContent { DuaTheme(LocalPreferences(theme = mode)) { ChatScreen(home.copy(draft = "Hi"), ChatActions()) } }
        listOf("Add", "Send").forEach {
            compose.onNodeWithContentDescription(it, substring = true).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    /** The voice-mode button (empty field) keeps the same 48 dp target. */
    @Test fun voiceModeButtonTouchTarget() {
        compose.setContent { DuaTheme(LocalPreferences(theme = mode)) { ChatScreen(home, ChatActions()) } }
        compose.onNodeWithContentDescription("Voice").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun homeWithDraft() = shot("home_draft") { ChatScreen(home.copy(draft = "Plan a 3-day study schedule for physics", reasoning = true), ChatActions()) }

    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    @Test fun homeSmall() = shot("home_small") { ChatScreen(home, ChatActions()) }
}

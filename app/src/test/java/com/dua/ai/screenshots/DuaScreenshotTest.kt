package com.dua.ai.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dua.ai.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatSheet
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.settings.SettingsContent
import com.dua.ai.ui.settings.SettingsUiState
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshots of the main surfaces in dark and light.
 * Not part of the default test run; record with:
 *   ./gradlew :app:recordRoborazziDebug -Pdua.screenshots
 * Images land in app/build/outputs/roborazzi/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DuaScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))
    }

    @get:Rule val compose = createComposeRule()

    private val mode get() = if (dark) "dark" else "light"
    private fun path(name: String) = "build/outputs/roborazzi/${name}_$mode.png"

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path(name))
    }

    @Test fun emptyChat() = shot("01_empty_chat") {
        ChatScreen(ScreenshotData.emptyState, ChatActions())
    }

    @Test fun chatWithMarkdown() = shot("02_chat_markdown") {
        ChatScreen(ScreenshotData.conversation, ChatActions())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun composerWithAttachSheet() {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ChatScreen(ScreenshotData.emptyState.copy(draft = "Summarise this PDF for me"), ChatActions(), initialSheet = ChatSheet.Attach)
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(path("03_attach_sheet"))
    }

    @Test fun historyDrawer() = shot("04_history_drawer") {
        ChatScreen(ScreenshotData.conversation.copy(savedChats = ScreenshotData.history), ChatActions(), initialDrawerValue = DrawerValue.Open)
    }

    /** Adaptive launcher icon as a launcher shows it (72dp circle mask over the 108dp layers) and its themed monochrome layer. */
    @Test fun launcherIcon() {
        compose.setContent {
            Row(Modifier.background(if (dark) Color(0xFF000000) else Color(0xFFFFFFFF)).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(colorResource(R.color.ic_launcher_background)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(108.dp))
                }
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFD7E3DF)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_monochrome), contentDescription = null, modifier = Modifier.requiredSize(108.dp),
                        colorFilter = ColorFilter.tint(Color(0xFF1F3A35)))
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path("06_launcher_icon"))
    }

    @Test fun settings() = shot("05_settings") {
        SettingsContent(SettingsUiState(email = "fareed@example.com", theme = mode, haptic = true, notifications = true),
            onBack = {}, onOpen = {}, onHaptic = {}, onGeneratedVoice = {})
    }
}

private object ScreenshotData {
    private val suggestions = listOf(
        PromptSuggestion("Write code snippet", "", "", "Coding"),
        PromptSuggestion("Explain simply", "", "", "Learning"),
        PromptSuggestion("Brainstorm concepts", "", "", "Idea"),
        PromptSuggestion("Draft a plan", "", "", "Productivity"),
    )

    val emptyState = ChatUiState(greeting = "Good evening, Fareed", suggestions = suggestions, webSearchConfigured = true, pageReadingAvailable = true)

    private val reply = """
        ## Card in Jetpack Compose

        Use **Material 3** `Card` with a *subtle* elevation. A few tips:

        - Keep padding on the **inside** of the card
        - Prefer `ElevatedCard` for lists
        - Read the [official guide](https://developer.android.com/develop/ui/compose/components/card)

        ```kotlin
        @Composable
        fun ProfileCard(name: String) {
            ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
        }
        ```

        | Variant | Use when |
        |---|---|
        | Card | Grouped content |
        | OutlinedCard | Low emphasis |

        Sources:
        [1] Cards – Jetpack Compose
        https://developer.android.com/develop/ui/compose/components/card

        [2] Material Design 3 cards
        https://m3.material.io/components/cards/overview
    """.trimIndent()

    val conversation = emptyState.copy(
        messages = listOf(
            ChatMessage(id = "u1", text = "How do I build an elegant Card in Jetpack Compose?", isUser = true),
            ChatMessage(id = "a1", text = reply, isUser = false, model = "Dua"),
        ),
        webMode = "search",
    )

    private fun chat(id: String, title: String, hoursAgo: Long) = SavedChat(id, "Dua", "",
        listOf(ChatMessage(id = "$id-m", text = title, isUser = true)), System.currentTimeMillis() - hoursAgo * 3_600_000L)

    val history = listOf(
        chat("c1", "Card component in Jetpack Compose", 1),
        chat("c2", "Weekend trip plan to Lahore", 3),
        chat("c3", "Explain transformers simply", 26),
        chat("c4", "Colour palette for a calm app", 30),
        chat("c5", "Kotlin coroutines vs threads", 24 * 4),
        chat("c6", "Typography scale for mobile", 24 * 12),
    )
}

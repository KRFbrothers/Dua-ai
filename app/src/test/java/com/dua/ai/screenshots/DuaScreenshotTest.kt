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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatSheet
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.chat.AudioSheetContent
import com.dua.ai.ui.screens.StoredFilesSection
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

    private fun shot(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.dua.ai.ui.chat.LocalFaviconLoader provides ScreenshotData.favicons) {
                DuaTheme(LocalPreferences(theme = mode)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
        before()
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

    @Test fun chatWithThinkingCollapsed() = shot("07_chat_thinking_collapsed") {
        ChatScreen(ScreenshotData.reasoning, ChatActions())
    }

    @Test fun chatWithThinkingExpanded() = shot("08_chat_thinking_expanded") {
        ChatScreen(ScreenshotData.reasoning.copy(expandedThinking = setOf("a2")), ChatActions())
    }

    @Test fun composerWithReasoningChip() = shot("09_composer_reasoning") {
        ChatScreen(ScreenshotData.emptyState.copy(draft = "Plan a 3-day study schedule for physics", reasoning = true), ChatActions())
    }

    @Test fun composerUploadProgress() = shot("10_upload_progress") {
        ChatScreen(ScreenshotData.emptyState.copy(draft = "Compare these two", storedFiles = true, uploads = ScreenshotData.uploads), ChatActions())
    }

    @Test fun messageFileChips() = shot("11_file_chips") {
        ChatScreen(ScreenshotData.files, ChatActions())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun attachSheetWithStoredFiles() {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ChatScreen(ScreenshotData.emptyState.copy(storedFiles = true, storedFilesMaxBytes = 52428800), ChatActions(), initialSheet = ChatSheet.Attach)
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(path("12_attach_sheet_stored"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun attachSheetSignedOut() {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ChatScreen(ScreenshotData.emptyState.copy(storedFilesHint = "Sign in from Profile to attach audio, spreadsheets and large files (up to 50 MB each). Without signing in, up to 3 small files (2 MB total) still work."),
                        ChatActions(), initialSheet = ChatSheet.Attach)
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(path("13_attach_sheet_signed_out"))
    }

    @Test fun audioRecording() = shot("14_audio_recording") {
        AudioSheetContent(recordingSince = 0L, available = true, hint = "", onRecord = {}, onStop = {}, onCancel = {}, onPick = {}, now = { 83_000L })
    }

    @Test fun storageSettings() = shot("15_storage_settings") {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
            StoredFilesSection(signedIn = true, enabled = true, usage = com.dua.ai.files.StorageUsage(14, 38_700_000, 52428800), busy = false, onRefresh = {}, onDeleteAll = {})
        }
    }

    @Test fun sourceChips() = shot("16_source_chips") {
        ChatScreen(ScreenshotData.sources, ChatActions())
    }

    @Test fun xSearchToggle() = shot("17_x_toggle", before = { compose.onNodeWithText("X").performScrollTo() }) {
        ChatScreen(ScreenshotData.emptyState.copy(model = "Grok", draft = "What are people saying about the Pixel 11 launch?", webMode = "search", xSearchAvailable = true, xSearch = true), ChatActions())
    }

    @Test fun earlierMessagesSummarised() = shot("18_summarised") {
        ChatScreen(ScreenshotData.summarised, ChatActions())
    }

    @Test fun settings() = shot("05_settings") {
        SettingsContent(SettingsUiState(email = "fareed@example.com", theme = mode, haptic = true, notifications = true),
            onBack = {}, onOpen = {}, onHaptic = {}, onGeneratedVoice = {})
    }
}

private object ScreenshotData {
    private val suggestions = listOf(
        PromptSuggestion("Write code snippet", "", "Coding"),
        PromptSuggestion("Explain simply", "", "Learning"),
        PromptSuggestion("Brainstorm concepts", "", "Idea"),
        PromptSuggestion("Draft a plan", "", "Productivity"),
    )

    private const val UID = "11111111-2222-4333-8444-555555555555"
    val uploads = listOf(
        com.dua.ai.files.PendingUpload("u1", "Q3 budget.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 4_200_000, sent = 2_730_000),
        com.dua.ai.files.PendingUpload("u2", "Lease agreement.pdf", "application/pdf", 18_400_000, sent = 18_400_000, state = com.dua.ai.files.PendingUpload.DONE,
            ref = com.dua.ai.files.FileRef("$UID/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf", "Lease agreement.pdf", "application/pdf", 18_400_000)),
    )
    val files by lazy { ChatUiState(messages = listOf(
        ChatMessage(id = "f1", text = "What changed between these?\nFiles: Lease agreement.pdf, Q3 budget.xlsx, Voice note 18:42.m4a", isUser = true, files = listOf(
            com.dua.ai.files.FileRef("$UID/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf", "Lease agreement.pdf", "application/pdf", 18_400_000),
            com.dua.ai.files.FileRef("$UID/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.xlsx", "Q3 budget.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 4_200_000),
            com.dua.ai.files.FileRef("$UID/cccccccc-bbbb-4ccc-8ddd-eeeeeeeeeeee.m4a", "Voice note 18:42.m4a", "audio/mp4", 820_000, "Check whether rent went up."))),
        ChatMessage(id = "f2", text = "**Rent** rises from ₹42,000 to ₹45,500 from April (clause 4.2). Your Q3 sheet still budgets ₹42,000, so update row 7. Your voice note asked about rent — yes, it went up by 8.3%.", isUser = false, model = "Dua"),
    ), greeting = "Good evening, Fareed", storedFiles = true) }

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

    private val thinking = """
        **Working out the total**

        The train covers 120 km in 1.5 hours, so its speed is 80 km/h. The return trip is 20% slower: 80 × 0.8 = 64 km/h, which takes 120 / 64 = 1.875 hours.

        **Average speed**

        Total distance is 240 km and total time is 1.5 + 1.875 = 3.375 hours, so the average is 240 / 3.375 ≈ 71.1 km/h — not the simple mean of 80 and 64.
    """.trimIndent()

    val reasoning = emptyState.copy(
        messages = listOf(
            ChatMessage(id = "u2", text = "A train goes 120 km in 90 minutes and returns 20% slower. What's the average speed for the round trip?", isUser = true),
            ChatMessage(id = "a2", text = "The average speed is about **71.1 km/h**.\n\n- Out: 120 km at 80 km/h → 1.5 h\n- Back: 120 km at 64 km/h → 1.875 h\n- Total: 240 km in 3.375 h\n\nAverage speed is total distance over total time, so it is lower than the simple mean (72 km/h).",
                isUser = false, model = "Gemini", thinking = thinking, thinkingSeconds = 12),
        ),
        model = "Gemini",
        reasoning = true,
    )

    /** Fake favicon for one site (no network in tests); other sites show their letter. */
    val favicons: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap? = { domain ->
        if (domain != "bbc.com") null else androidx.compose.ui.graphics.ImageBitmap(32, 32).also { image ->
            val canvas = androidx.compose.ui.graphics.Canvas(image)
            val paint = androidx.compose.ui.graphics.Paint()
            paint.color = Color(0xFFBB1919); canvas.drawRect(0f, 0f, 32f, 32f, paint)
            paint.color = Color.White; canvas.drawRect(6f, 10f, 26f, 22f, paint)
        }
    }

    private val sourcedReply = """
        Mumbai should stay **humid with heavy showers** through the weekend, around 27–30 °C, with an orange alert for Saturday [1][2]. On X, commuters are reporting waterlogging near Andheri and Sion [3].

        Sources:
        [1] Mumbai weather – BBC Weather
        https://www.bbc.com/weather/1275339

        [2] imd.gov.in
        https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQExample

        [3] Post on X
        https://x.com/mumbairains/status/1840000000000000000
    """.trimIndent()

    val sources = emptyState.copy(
        messages = listOf(
            ChatMessage(id = "s1", text = "Will it rain in Mumbai this weekend?", isUser = true),
            ChatMessage(id = "s2", text = sourcedReply, isUser = false, model = "Grok"),
        ),
        model = "Grok", webMode = "search", xSearchAvailable = true, xSearch = true,
    )

    val summarised = emptyState.copy(
        messages = listOf(
            ChatMessage(id = "h1", text = "Help me plan the Lahore trip budget.", isUser = true),
            ChatMessage(id = "h2", text = "Here is a day-by-day budget: travel ₹18,000, stay ₹21,000, food ₹9,000.", isUser = false, model = "Dua"),
            ChatMessage(id = "h3", text = "Now add a buffer and convert the total to PKR.", isUser = true),
            ChatMessage(id = "h4", text = "With a 10% buffer the total is **₹52,800**, about **PKR 176,000** at today's rate. I kept the stay and food split from earlier.", isUser = false, model = "Dua"),
        ),
        summarisedThroughId = "h2",
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

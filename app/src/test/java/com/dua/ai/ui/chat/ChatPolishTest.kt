package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ChatPolishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editingBannerCancelCallsBack() {
        var cancelled = 0
        compose.setContent { DuaTheme(LocalPreferences()) { EditingBanner(onCancel = { cancelled++ }) } }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test fun streamingReplyShowsTextAndFinishedReplyKeepsIt() {
        val message = mutableStateOf(ChatMessage(text = "Hello there", isUser = false, state = ChatMessage.STREAMING))
        compose.mainClock.autoAdvance = false // the cursor blinks forever while streaming
        compose.setContent { DuaTheme(LocalPreferences()) { AssistantMessage(message.value, isLast = true, typing = true, speaking = false,
            speakEnabled = false, generatedVoice = false, retryWebMode = null, onRetry = {}, onSpeak = {}) } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Hello there").assertExists()
        message.value = message.value.copy(state = "complete")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Hello there").assertExists()
    }
}

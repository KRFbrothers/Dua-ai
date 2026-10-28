package com.dua.ai.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
class ChatPolish2Test {
    @get:Rule val compose = createComposeRule()

    @Test fun waitingForFirstTokenShowsPlaceholderThenText() {
        compose.mainClock.autoAdvance = false
        val message = androidx.compose.runtime.mutableStateOf(ChatMessage(text = "", isUser = false, state = ChatMessage.STREAMING))
        compose.setContent { DuaTheme(LocalPreferences()) { AssistantMessage(message.value, isLast = true, typing = true, speaking = false,
            speakEnabled = false, generatedVoice = false, retryWebMode = null, onRetry = {}, onSpeak = {}) } }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("reply-placeholder").assertExists()
        compose.onNodeWithContentDescription("Dua is thinking", useUnmergedTree = true).assertExists()
        message.value = message.value.copy(text = "First words")
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("reply-placeholder").assertDoesNotExist()
        compose.onNodeWithText("First words").assertExists()
    }

    @Test fun copyAndRetryUseTheCentralHaptic() {
        val haptics = mutableListOf<ChatHaptic>()
        var retries = 0
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                CompositionLocalProvider(LocalChatHaptic provides { haptics += it }) {
                    MessageActions(copyText = "hello", showRetry = true, retryEnabled = true, retryDescription = "Retry", speaking = false,
                        speakEnabled = false, speakDescription = "Speak", onRetry = { retries++ }, onSpeak = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Copy response").performClick()
        compose.onNodeWithContentDescription("Retry").performClick()
        compose.runOnIdle { assertEquals(listOf(ChatHaptic.Tap, ChatHaptic.Tap), haptics); assertEquals(1, retries) }
    }
}

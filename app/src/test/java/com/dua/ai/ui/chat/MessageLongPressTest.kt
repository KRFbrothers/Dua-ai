package com.dua.ai.ui.chat

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRun
import com.dua.ai.model.CodeRuns
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageLongPressTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()
    private val old = ChatMessage(id = "old", text = "First answer", isUser = false)
    private val latest = ChatMessage(id = "latest", text = "Latest answer", isUser = false)
    private val prompt = ChatMessage(id = "prompt", text = "A question", isUser = true)
    private val state = mutableStateOf(ChatUiState(chatId = "chat", messages = listOf(prompt, old, latest)))
    private val haptics = mutableListOf<ChatHaptic>()
    private val retries = mutableListOf<String>()
    private val spoken = mutableListOf<String>()
    private lateinit var clipboard: ClipboardManager
    private lateinit var context: android.content.Context

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            clipboard = LocalClipboardManager.current; context = LocalContext.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        MessageList(state.value, ChatActions(onRetry = { retries += it }, onSpeak = { spoken += it.id }, haptic = { haptics += it }), rememberLazyListState())
                    }
                }
            }
        }
    }
    private fun longPress(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).performTouchInput { longClick() }
    private fun sheetAction(key: String) = compose.onNodeWithTag("message-action-$key")
    private fun capture(name: String) {
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = java.io.File("build/approved-ui/message-$name-${if (dark) "dark" else "light"}.png")
            file.parentFile?.mkdirs()
            compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }

    @Test fun onlyLatestFinishedAssistantHasAnActionRowAndAvatarsGroupAdjacentChunks() {
        show()
        compose.onAllNodesWithContentDescription("Copy response").assertCountEquals(1)
        compose.onNode(hasContentDescription("Copy response") and hasAnyAncestor(hasTestTag("message-old"))).assertDoesNotExist()
        compose.onNode(hasContentDescription("Copy response") and hasAnyAncestor(hasTestTag("message-latest"))).assertExists()
        compose.onAllNodesWithTag("dua-avatar", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("dua-avatar", useUnmergedTree = true).assertContentDescriptionEquals("Dua")
        val user = compose.onNodeWithText(prompt.text).fetchSemanticsNode().boundsInRoot
        val bot = compose.onNodeWithText(old.text).fetchSemanticsNode().boundsInRoot
        assertTrue("User bubble stays to the right", user.right > bot.left)
        capture("turns")
    }

    @Test fun longPressOlderAssistantUsesFullCopyPayloadAndDisablesUnsupportedRetry() {
        val run = CodeRun(code = "print(1)", output = "1", outcome = "ok")
        state.value = state.value.copy(messages = listOf(prompt, old.copy(codeRuns = listOf(run)), latest))
        show(); longPress(old.text)
        compose.onNodeWithTag("message-actions-sheet").assertExists()
        sheetAction("retry").assertIsNotEnabled()
        sheetAction("speak").assertIsEnabled()
        sheetAction("copy").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(old.text + CodeRuns.copyText(listOf(run)), clipboard.getText()?.text)
            assertEquals(listOf(ChatHaptic.LongPress), haptics)
        }
        compose.onNodeWithTag("message-actions-sheet").assertDoesNotExist()
    }

    @Test fun userLongPressShowsOnlyCopyShareAndPreservesFullPrompt() {
        show(); longPress(prompt.text)
        sheetAction("retry").assertDoesNotExist(); sheetAction("speak").assertDoesNotExist()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        sheetAction("share").assertIsEnabled()
        sheetAction("copy").assertContentDescriptionEquals("Copy message").performClick()
        compose.runOnIdle { assertEquals(prompt.text, clipboard.getText()?.text) }
    }

    @Test fun sheetShareUsesExistingAndroidChooserAndExactReply() {
        show(); longPress(old.text)
        sheetAction("share").performClick()
        compose.runOnIdle {
            var base = context
            while (base is android.content.ContextWrapper && base !is android.app.Activity) base = base.baseContext
            val chooser = Shadows.shadowOf(base as android.app.Activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("text/plain", send.type); assertEquals(old.text, send.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    @Test fun sheetSpeakUsesSelectedOlderReplyHandler() {
        show(); longPress(old.text)
        sheetAction("speak").performClick()
        compose.runOnIdle { assertEquals(listOf(old.id), spoken) }
    }

    @Test fun streamingKeepsToolbarHiddenAndSheetUpdatesWhenReplyCompletes() {
        val pending = latest.copy(text = "Partial answer", state = ChatMessage.STREAMING)
        state.value = state.value.copy(messages = listOf(prompt, pending), typing = true)
        show()
        compose.onAllNodesWithContentDescription("Copy response").assertCountEquals(0)
        longPress(pending.text)
        sheetAction("copy").assertIsEnabled(); sheetAction("retry").assertIsNotEnabled(); sheetAction("speak").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(messages = listOf(prompt, latest), typing = false) }
        sheetAction("retry").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(latest.id), retries) }
        compose.onNodeWithTag("message-actions-sheet").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Copy response").assertCountEquals(1)
    }

    @Test fun pendingAndStreamingTypingIndicatorsKeepOneDuaAvatar() {
        state.value = state.value.copy(messages = listOf(prompt), typing = true)
        show()
        compose.onNodeWithContentDescription("Dua is thinking", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("dua-avatar", useUnmergedTree = true).assertCountEquals(1)
        compose.runOnIdle { state.value = state.value.copy(messages = listOf(prompt, latest.copy(text = "", state = ChatMessage.STREAMING))) }
        compose.onNodeWithContentDescription("Dua is thinking", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("dua-avatar", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun switchingChatDismissesMessageActions() {
        show(); longPress(old.text)
        compose.onNodeWithTag("message-actions-sheet").assertExists()
        compose.runOnIdle { state.value = state.value.copy(chatId = "other-chat") }
        compose.onNodeWithTag("message-actions-sheet").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun largeTextSheetActionsKeepTouchTargetsAndRemainReachable() {
        show(fontScale = 2f); longPress(old.text)
        for (action in listOf("copy", "retry", "share", "speak"))
            sheetAction(action).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }
}

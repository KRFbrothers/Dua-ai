package com.dua.ai.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRun
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Received code remains available after reply progress ends without a sandbox result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class CodeRunStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stoppingAReplyRemovesUnfinishedCodeProgress() {
        val reply = mutableStateOf(ChatMessage(text = "", isUser = false,
            state = ChatMessage.STREAMING, codeRuns = listOf(CodeRun(code = "print('hello')"))))
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                AssistantMessage(reply.value, isLast = true, typing = reply.value.streaming,
                    speaking = false, speakEnabled = false, generatedVoice = false,
                    retryWebMode = null, onRetry = {}, onSpeak = {}, codeExpanded = true)
            }
        }
        compose.onNodeWithText("Running code…").assertExists()
        compose.onNodeWithText("Output · running…").assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true).assertCountEquals(1)

        compose.runOnIdle { reply.value = reply.value.copy(state = "stopped") }

        compose.onNodeWithText("Running code…").assertDoesNotExist()
        compose.onNodeWithText("Output · running…").assertDoesNotExist()
        compose.onNodeWithText("Code interrupted").assertExists()
        compose.onNodeWithText("Output interrupted").assertExists()
        compose.onNodeWithText("print('hello')").assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun receivedResultsKeepTheirCompletedFailureAndTimeoutLabels() {
        val run = mutableStateOf(CodeRun(code = "print('hello')", output = "hello\n", outcome = "ok"))
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                CodeRunBlock("reply", 0, 1, run.value, initiallyExpanded = true, live = false)
            }
        }
        compose.onNodeWithText("Ran code").assertExists()
        compose.onNodeWithText("Output").assertExists()
        compose.onNodeWithText("hello").assertExists()
        compose.onNodeWithText("Code interrupted").assertDoesNotExist()

        compose.runOnIdle { run.value = run.value.copy(output = "Execution error\n", outcome = "error") }
        compose.onNodeWithText("Code failed").assertExists()
        compose.onNodeWithText("Execution error").assertExists()

        compose.runOnIdle { run.value = run.value.copy(output = "", outcome = "timeout") }
        compose.onNodeWithText("Code timed out").assertExists()
        compose.onNodeWithText("The code ran out of time before printing anything.").assertExists()
        compose.onNodeWithText("Output interrupted").assertDoesNotExist()
    }

    @Test fun interruptedCodeCanStillBeCopiedWithItsOriginalWhitespace() {
        val code = "  print('unfinished')\n\n"
        val reply = ChatMessage(text = "", isUser = false, state = "stopped",
            codeRuns = listOf(CodeRun(code = code)))
        lateinit var clipboard: ClipboardManager
        compose.setContent {
            clipboard = LocalClipboardManager.current
            DuaTheme(LocalPreferences()) {
                AssistantMessage(reply, isLast = true, typing = false, speaking = false,
                    speakEnabled = false, generatedVoice = false, retryWebMode = null,
                    onRetry = {}, onSpeak = {}, codeExpanded = true)
            }
        }
        compose.onNodeWithText("Code interrupted").assertExists()
        compose.onNodeWithText("Copy code").performClick()
        compose.runOnIdle { assertEquals(code, clipboard.getText()?.text) }
        compose.onNodeWithText("Copied").assertExists()
    }
}

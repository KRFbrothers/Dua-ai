package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRun
import com.dua.ai.model.CodeRuns
import com.dua.ai.model.ReplyRetryPlan
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageActionsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shareOpensAndroidChooserWithTheFullReplyAndExecutedCode() {
        val reply = ChatMessage(text = "**Sample answer**", isUser = false,
            codeRuns = listOf(CodeRun(code = "print(1)", output = "1", outcome = "ok")))
        lateinit var context: android.content.Context
        compose.setContent {
            context = androidx.compose.ui.platform.LocalContext.current
            DuaTheme(LocalPreferences()) {
                AssistantMessage(reply, isLast = true, typing = false, speaking = false, speakEnabled = false,
                    generatedVoice = false, retryWebMode = null, onRetry = {}, onSpeak = {})
            }
        }
        compose.onNodeWithContentDescription("Share response").assertIsEnabled().performClick()
        compose.runOnIdle {
            var base = context
            while (base is android.content.ContextWrapper && base !is android.app.Activity) base = base.baseContext
            val chooser = org.robolectric.Shadows.shadowOf(base as android.app.Activity).nextStartedActivity
            assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            assertEquals(android.content.Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertEquals(reply.text + CodeRuns.copyText(reply.codeRuns), send.getStringExtra(android.content.Intent.EXTRA_TEXT))
        }
    }

    @Test fun fullReplyCopyKeepsMarkdownAndExecutedCode() {
        val reply = ChatMessage(text = "**Answer**\n\n```python\nprint(1)\n```\n", isUser = false,
            codeRuns = listOf(CodeRun(code = "print(1)\n", output = "1\n", outcome = "ok")))
        lateinit var clipboard: ClipboardManager
        compose.setContent {
            clipboard = LocalClipboardManager.current
            DuaTheme(LocalPreferences()) {
                AssistantMessage(reply, isLast = true, typing = false, speaking = false, speakEnabled = true,
                    generatedVoice = false, retryWebMode = null, onRetry = {}, onSpeak = {})
            }
        }
        compose.onNodeWithContentDescription("Copy response").performClick()
        compose.runOnIdle { assertEquals(reply.text + CodeRuns.copyText(reply.codeRuns), clipboard.getText()?.text) }
        compose.onNodeWithContentDescription("Response copied").assertExists()
    }

    @Test fun stoppedEmptyReplyCanRetryItsOriginalPromptButCannotCopyOrSpeak() {
        val prompt = ChatMessage(text = "Explain this", isUser = true)
        val reply = ChatMessage(text = "", isUser = false, state = "stopped")
        var plan: ReplyRetryPlan? = null
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                AssistantMessage(reply, isLast = true, typing = false, speaking = false, speakEnabled = true,
                    generatedVoice = false, retryWebMode = null,
                    onRetry = { plan = ReplyRetryPlan.create(listOf(prompt, reply), reply.id) }, onSpeak = {})
            }
        }
        compose.onNodeWithText("Stopped").assertExists()
        compose.onNodeWithContentDescription("Copy response").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Speak response with device voice").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Retry response with web off").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(prompt), plan?.context) }
    }

    @Test fun actionRowIsHiddenWhileAnotherReplyIsPending() {
        var retried = false
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                AssistantMessage(ChatMessage(text = "Saved reply", isUser = false), isLast = true,
                    typing = true, speaking = false, speakEnabled = false, generatedVoice = false,
                    retryWebMode = null, onRetry = { retried = true }, onSpeak = {})
            }
        }
        compose.onNodeWithContentDescription("Retry response with web off").assertDoesNotExist()
        compose.onNodeWithContentDescription("Copy response").assertDoesNotExist()
        compose.runOnIdle { assertTrue(!retried) }
    }

    @Test fun copyingDoesNotMoveOtherActionsInANarrowLargeTextColumn() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                DuaTheme(LocalPreferences()) {
                    Box(Modifier.width(240.dp).testTag("copy-stability")) {
                        MessageActions(copyText = "Answer", showRetry = true, retryEnabled = true,
                            retryDescription = "Retry response", speaking = false, speakEnabled = true,
                            speakDescription = "Speak response", onRetry = {}, onSpeak = {})
                    }
                }
            }
        }
        val descriptions = listOf("Retry response", "Share response", "Speak response")
        compose.onAllNodesWithText("Copied").assertCountEquals(0)
        fun capture(label: String) {
            val file = File("build/approved-ui/copy-feedback-$label.png")
            file.parentFile!!.mkdirs()
            compose.onNodeWithTag("copy-stability").captureRoboImage(file.absolutePath)
        }
        capture("ready")
        val before = descriptions.associateWith { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        compose.onNodeWithContentDescription("Copy response").performClick()
        compose.onNodeWithContentDescription("Response copied").assertIsDisplayed()
        capture("copied")
        descriptions.forEach { description ->
            val after = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("Copy feedback must not move $description horizontally", before.getValue(description).left, after.left, .5f)
            assertEquals("Copy feedback must not move $description to another row", before.getValue(description).top, after.top, .5f)
        }
    }

    @Test fun largeTextKeepsEveryActionVisibleAndTappableInANarrowColumn() {
        val events = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                DuaTheme(LocalPreferences()) {
                    Box(Modifier.width(240.dp).testTag("action-space")) {
                        MessageActions(copyText = "Answer", showRetry = true, retryEnabled = true,
                            retryDescription = "Retry response", speaking = false, speakEnabled = true,
                            speakDescription = "Speak response", onRetry = { events += "retry" },
                            onSpeak = { events += "speak" })
                    }
                }
            }
        }
        val space = compose.onNodeWithTag("action-space").fetchSemanticsNode().boundsInRoot
        val minimum = space.width * (48f / 240f)
        val share = compose.onNodeWithContentDescription("Share response").assertIsDisplayed().assertIsEnabled()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Share needs a full touch target", share.width >= minimum - 1f && share.height >= minimum - 1f)
        assertTrue("Share must fit inside the column", share.left >= space.left - 1f && share.right <= space.right + 1f)
        for (action in listOf("Copy response", "Retry response", "Speak response")) {
            val node = compose.onNodeWithContentDescription(action).assertIsDisplayed().assertIsEnabled()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$action needs a full touch target: $bounds", bounds.width >= minimum - 1f && bounds.height >= minimum - 1f)
            assertTrue("$action must fit inside the column", bounds.left >= space.left - 1f && bounds.right <= space.right + 1f)
            node.performClick()
        }
        compose.runOnIdle { assertEquals(listOf("retry", "speak"), events) }
        compose.onNodeWithContentDescription("Response copied").assertIsDisplayed()
        val copy = compose.onNodeWithContentDescription("Response copied").fetchSemanticsNode().boundsInRoot
        val retry = compose.onNodeWithContentDescription("Retry response").fetchSemanticsNode().boundsInRoot
        val speak = compose.onNodeWithContentDescription("Speak response").fetchSemanticsNode().boundsInRoot
        assertTrue("Copy and retry must not overlap", copy.left >= retry.right || copy.top >= retry.bottom)
        assertTrue("Copy and speech must not overlap", speak.left >= copy.right || speak.top >= copy.bottom)
    }
}

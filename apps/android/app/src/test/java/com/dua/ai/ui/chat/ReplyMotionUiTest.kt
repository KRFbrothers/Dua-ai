package com.dua.ai.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dua.ai.model.AgentStep
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyMotionUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var owner: Owner
    private var previousScale = 1f
    @Before fun setup() {
        previousScale = Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.runOnUiThread { owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED } }
        compose.mainClock.autoAdvance = false
    }
    @After fun cleanup() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
        compose.mainClock.autoAdvance = true
    }
    private fun scale(value: Float) {
        compose.runOnUiThread { Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, value) }
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeByFrame()
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalLifecycleOwner provides owner,
            LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Column(Modifier.requiredSize(360.dp, 800.dp).background(ChatTheme.colors.background)
                    .padding(16.dp).testTag("reply-motion-preview")) { content() }
            }
        }
    }
    private fun capture(label: String): Bitmap {
        val file = File("build/approved-ui/reply-motion-$label-$dark-$large.png")
        file.parentFile!!.mkdirs()
        compose.onNodeWithTag("reply-motion-preview").captureRoboImage(file.absolutePath)
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
    }
    private fun assertStatic(label: String) {
        compose.mainClock.advanceTimeBy(400)
        val before = capture("$label-a")
        compose.mainClock.advanceTimeBy(608)
        val after = capture("$label-b")
        try { assertTrue("Decorative reply feedback should stay still: $label", before.sameAs(after)) }
        finally { before.recycle(); after.recycle() }
    }

    @Test fun livePreferenceAndLifecycleChangesStopAndResumeDecorativeFeedback() {
        scale(1f)
        show {
            ReplyPlaceholder()
            StreamingCursor()
            AgentProgressView(listOf(AgentStep("search", "Searching documents…")))
            ThinkingBlock("thinking", "1. Check the facts\nUse the supplied notes", 4,
                live = true, answering = false, startedAt = 0)
        }
        compose.mainClock.advanceTimeBy(416)
        val first = capture("active-a")
        compose.mainClock.advanceTimeBy(400)
        val second = capture("active-b")
        try { assertFalse("Foreground motion should retain the existing animated feedback", first.sameAs(second)) }
        finally { first.recycle(); second.recycle() }
        compose.onNodeWithContentDescription("Dua is thinking").assertExists()
        compose.onNodeWithContentDescription("Searching documents…").assertExists()
        scale(0f)
        assertStatic("reduced")
        scale(1f)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeByFrame()
        assertStatic("paused")
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(416)
        val resumed = capture("resumed-a")
        compose.mainClock.advanceTimeBy(400)
        val later = capture("resumed-b")
        try { assertFalse("Feedback should resume without recreating the content", resumed.sameAs(later)) }
        finally { resumed.recycle(); later.recycle() }
    }

    @Test fun reducedMotionKeepsStreamingTextStepsAndRetryAvailable() {
        scale(0f)
        val steps = mutableStateOf(listOf(AgentStep("search", "Searching documents…")))
        val reply = mutableStateOf(ChatMessage(id = "answer", text = "", isUser = false, state = ChatMessage.STREAMING))
        var retries = 0
        show {
            AgentProgressView(steps.value)
            MessageList(ChatUiState(chatId = "chat", messages = listOf(reply.value), typing = reply.value.streaming),
                ChatActions(onRetry = { retries++ }), rememberLazyListState())
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("reply-placeholder").assertExists()
        compose.runOnIdle {
            steps.value = listOf(AgentStep("search", "Searching documents…", true), AgentStep("write", "Writing reply…"))
            reply.value = reply.value.copy(text = "The visible answer")
        }
        compose.mainClock.advanceTimeByFrame()
        val answer = compose.onNodeWithText("The visible answer")
        // Markdown composition and lazy-list measurement can need subsequent frames
        // even when every decorative transition is disabled.
        compose.mainClock.advanceTimeUntil(timeoutMillis = 1_000) { answer.isDisplayed() }
        answer.assertIsDisplayed()
        compose.onNodeWithTag("reply-placeholder").assertDoesNotExist()
        compose.onNodeWithContentDescription("Done: Searching documents").assertExists()
        compose.onNodeWithContentDescription("Writing reply…").assertExists()
        compose.onNodeWithContentDescription("Retry response with web off").assertDoesNotExist()
        compose.runOnIdle { reply.value = reply.value.copy(state = "stopped") }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeUntil(timeoutMillis = 1_000) {
            compose.onNodeWithText("Stopped · partial reply").isDisplayed()
        }
        compose.onNodeWithText("Stopped · partial reply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry response with web off").assertIsEnabled().performClick()
        assertEquals(1, retries)
        capture("stopped").recycle()
    }
}

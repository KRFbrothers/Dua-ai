package com.dua.ai.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.After
import org.junit.Assert.*
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
class AmbientMotionUiTest(private val dark: Boolean, private val large: Boolean) {
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
    private fun capture(tag: String, label: String): Bitmap {
        val file = File("build/approved-ui/ambient-$tag-$label-$dark-$large.png")
        file.parentFile!!.mkdirs()
        compose.onNodeWithTag(tag).captureRoboImage(file.absolutePath)
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
    }
    private fun compare(label: String, static: Boolean) {
        for (tag in listOf("home-gradient", "orb-preview")) {
            val a = capture(tag, "$label-a")
            compose.mainClock.advanceTimeBy(608)
            val b = capture(tag, "$label-b")
            try { assertEquals("$tag, $label: decorative motion state", static, a.sameAs(b)) }
            finally { a.recycle(); b.recycle() }
        }
    }

    @Test fun eachEffectFollowsLiveMotionAndLifecycleWithoutRecreatingContent() {
        scale(1f)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner,
                LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Column(Modifier.requiredSize(360.dp, 800.dp).background(ChatTheme.colors.background)) {
                        Box(Modifier.fillMaxWidth().height(620.dp).homeBackdrop(task = false, generating = true))
                        Box(Modifier.fillMaxWidth().height(100.dp).testTag("orb-preview")) { TypingIndicator() }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(416)
        compose.onNodeWithContentDescription("Dua is thinking").assertExists()
        compare("active", static = false)
        scale(0f)
        compose.mainClock.advanceTimeBy(608)
        compare("reduced", static = true)
        scale(1f)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeBy(608)
        compare("paused", static = true)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(416)
        compare("resumed", static = false)
        compose.onNodeWithContentDescription("Dua is thinking").assertExists()
    }

    @Test fun endingGenerationRestoresTheConversationBackground() {
        scale(0f)
        compose.mainClock.autoAdvance = true
        val generating = mutableStateOf(false)
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.requiredSize(360.dp, 700.dp).background(ChatTheme.colors.background)
                    .homeBackdrop(task = false, home = false, generating = generating.value))
            }
        }
        val idle = capture("conversation-ambient", "idle")
        compose.runOnIdle { generating.value = true }
        val busy = capture("conversation-ambient", "busy")
        compose.runOnIdle { generating.value = false }
        val restored = capture("conversation-ambient", "restored")
        try {
            assertFalse("Processing should visibly change the backdrop", idle.sameAs(busy))
            assertTrue("Stop/completion must restore the quiet conversation wash", idle.sameAs(restored))
        } finally { idle.recycle(); busy.recycle(); restored.recycle() }
    }
}

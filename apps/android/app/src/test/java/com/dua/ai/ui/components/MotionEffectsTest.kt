package com.dua.ai.ui.components

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.graphics.BitmapFactory
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "mdpi")
class MotionEffectsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var previousScale = 1f

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var owner: Owner

    @Before fun setup() {
        previousScale = Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.runOnUiThread { owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED } }
        compose.mainClock.autoAdvance = false
    }
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalLifecycleOwner provides owner) { DuaTheme(LocalPreferences()) { content() } }
    }
    /** Reads one pixel from a capture (needs `-Pdua.ui-record`, as in CI and docs/TESTING.md). */
    private fun pixel(tag: String, x: Int, y: Int): Int {
        val file = File("build/approved-ui/motion-effects-$tag.png")
        file.parentFile!!.mkdirs(); file.delete()
        compose.onNodeWithTag(tag).captureRoboImage(file.absolutePath)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Run with -Pdua.ui-record" }
        return try { bitmap.getPixel(x, y) } finally { bitmap.recycle() }
    }

    private fun animations(on: Boolean) {
        compose.runOnUiThread { Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (on) 1f else 0f) }
        ShadowLooper.idleMainLooper()
    }

    /** A 100 dp red square inside a white frame; returns whether its corner is still red after a long press-down. */
    private fun cornerRedWhilePressed(pressedScale: Float): Boolean {
        var clicks = 0
        show {
            Box(Modifier.size(120.dp).background(Color.White).testTag("frame")) {
                val source = remember { MutableInteractionSource() }
                Box(Modifier.size(100.dp).pressScale(source, pressedScale).background(Color.Red)
                    .clickable(interactionSource = source, indication = null) { clicks++ }.testTag("target"))
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("target").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        val red = pixel("frame", 2, 2) == android.graphics.Color.RED
        compose.onNodeWithTag("target").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(1, clicks) }
        return red
    }

    @Test fun pressShrinksTheControlButKeepsItClickable() {
        animations(true)
        assertFalse("corner should leave the shrunken control", cornerRedWhilePressed(0.5f))
    }

    @Test fun pressDoesNotScaleWhenAnimationsAreRemoved() {
        animations(false)
        assertTrue(cornerRedWhilePressed(0.5f))
    }

    @Test fun indicationsWithTheSameSettingsAreEqual() {
        assertEquals(PressScaleIndication(0.9f, true), PressScaleIndication(0.9f, true))
        assertEquals(PressScaleIndication(0.9f, true).hashCode(), PressScaleIndication(0.9f, true).hashCode())
        assertNotEquals(PressScaleIndication(0.9f, true), PressScaleIndication(0.9f, false))
    }

    private fun shimmerValues(enabled: Boolean): List<Float> {
        var read: () -> Float = { -1f }
        show { read = rememberShimmerAlpha(enabled = enabled, low = 0.2f, high = 1f, rest = 0.9f) }
        compose.mainClock.advanceTimeByFrame()
        return (0 until 6).map { compose.mainClock.advanceTimeBy(160); read() }
    }

    @Test fun shimmerPulsesWhenMotionIsAllowed() {
        animations(true)
        val values = shimmerValues(enabled = true)
        assertTrue(values.toString(), values.all { it in 0.2f..1f })
        assertTrue(values.toString(), values.distinct().size > 2)
    }

    @Test fun shimmerStaysStillWhenAnimationsAreRemoved() {
        animations(false)
        assertEquals(List(6) { 0.9f }, shimmerValues(enabled = true))
    }

    @Test fun shimmerStaysStillWhenDisabled() {
        animations(true)
        assertEquals(List(6) { 0.9f }, shimmerValues(enabled = false))
    }

    @Test fun shimmerBoxStaysStillWithoutMotion() {
        animations(false)
        show { Box(Modifier.size(60.dp).testTag("still")) { ShimmerBox(Modifier.size(60.dp)) } }
        compose.mainClock.advanceTimeBy(100)
        val first = pixel("still", 30, 30)
        compose.mainClock.advanceTimeBy(700)
        assertEquals(first, pixel("still", 30, 30))
    }
}

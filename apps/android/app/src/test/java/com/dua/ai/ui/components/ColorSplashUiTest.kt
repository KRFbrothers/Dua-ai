package com.dua.ai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class ColorSplashUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun radiusReachesFarthestCorner() {
        assertEquals(500f, splashRadius(Size(300f, 400f), Offset.Zero), 0.01f)
        assertEquals(500f, splashRadius(Size(300f, 400f), Offset(300f, 400f)), 0.01f)
        assertEquals(250f, splashRadius(Size(300f, 400f), Offset(150f, 200f)), 0.01f)
    }

    private fun run(motion: Boolean): Pair<() -> Int, () -> Int> {
        var covered = 0; var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var request by remember { mutableStateOf<ColorSplashRequest?>(ColorSplashRequest(Offset(10f, 700f), Color.Red)) }
            Box(Modifier.fillMaxSize()) {
                request?.let { ColorSplash(it, motion, onCovered = { covered++ }, onFinished = { finished++; request = null }) }
            }
        }
        return { covered } to { finished }
    }

    @Test fun reduceMotionSwitchesInstantly() {
        val (covered, finished) = run(motion = false)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, covered()); assertEquals(1, finished())
        compose.onNodeWithTag("color-splash").assertDoesNotExist()
    }

    @Test fun motionGrowsThenSwitchesThenClears() {
        val (covered, finished) = run(motion = true)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0, covered())
        compose.onNodeWithTag("color-splash").assertExists()
        compose.mainClock.advanceTimeBy(SPLASH_GROW_MS.toLong() + 50)
        assertEquals(1, covered()); assertEquals(0, finished())
        compose.mainClock.advanceTimeBy(SPLASH_SHRINK_MS.toLong() + 100)
        assertEquals(1, finished())
        compose.onNodeWithTag("color-splash").assertDoesNotExist()
    }
}

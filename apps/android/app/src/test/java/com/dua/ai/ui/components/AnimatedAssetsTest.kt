package com.dua.ai.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimatedAssetsTest {
    @get:Rule val compose = createComposeRule()
    private val assets get() = RuntimeEnvironment.getApplication().assets

    @Test fun everySettingsLottieAssetIsBundledAndValid() {
        for (path in AnimatedAssets.settingsIcons.values) {
            val json = assets.open(path).bufferedReader().readText()
            val r = com.airbnb.lottie.LottieCompositionFactory.fromJsonStringSync(json, path)
            assertNull("$path: ${r.exception}", r.exception)
            assertTrue(r.value!!.duration > 0f)
        }
    }

    @Test fun riveAssetIsBundledAndRiveFallsBackOnTheJvm() {
        val res = RuntimeEnvironment.getApplication().resources.openRawResource(com.dua.ai.R.raw.rive_thinking_dots).readBytes()
        assertEquals("RIVE", String(res.copyOfRange(0, 4)))
        assertFalse(AnimatedAssets.riveSupported)
        compose.setContent { DuaTheme(LocalPreferences()) { RiveOrFallback(com.dua.ai.R.raw.rive_thinking_dots) { Text("fallback orb") } } }
        compose.onNodeWithText("fallback orb").assertExists()
        compose.onNodeWithTag("rive-loader").assertDoesNotExist()
    }

    @Test fun missingLottieAssetShowsTheStaticIcon() {
        compose.setContent { DuaTheme(LocalPreferences()) { LottieIconOrFallback("lottie/missing.json", Icons.Outlined.Lock, Color.Red, 24.dp) } }
        compose.waitForIdle()
        compose.onNodeWithTag("lottie-icon").assertDoesNotExist()
    }

    @Test fun reduceMotionShowsTheStaticIcon() {
        android.provider.Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { DuaTheme(LocalPreferences()) { LottieIconOrFallback("lottie/lock.json", Icons.Outlined.Lock, Color.Red, 24.dp) } }
        compose.waitForIdle()
        compose.onNodeWithTag("lottie-icon").assertDoesNotExist()
    }

    @Test fun lottieIconRendersWhenMotionIsAllowed() {
        compose.setContent { DuaTheme(LocalPreferences()) { LottieIconOrFallback("lottie/lock.json", Icons.Outlined.Lock, Color.Red, 24.dp) } }
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("lottie-icon")).fetchSemanticsNodes().isNotEmpty() }
    }
}

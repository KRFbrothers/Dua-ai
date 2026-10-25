package com.dua.ai.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.dua.ai.settings.AppearancePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.TelegramImageCrossfade
import com.dua.ai.ui.settings.AppearanceContent
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelegramThemeRevealTest {
    @get:Rule val compose = createComposeRule()

    @Test fun finalRadiusReachesTheFarthestCorner() {
        assertEquals(500f, TelegramThemeReveal.finalRadius(300f, 400f, 0f, 0f), 0.01f)
        assertEquals(250f, TelegramThemeReveal.finalRadius(300f, 400f, 150f, 200f), 0.01f)
        assertEquals(500f, TelegramThemeReveal.finalRadius(300f, 400f, 300f, 400f), 0.01f)
    }

    @Test fun darkGrowsAndLightShrinks() {
        assertEquals(0f, TelegramThemeReveal.radius(true, 100f, 0f), 0f)
        assertEquals(100f, TelegramThemeReveal.radius(true, 100f, 1f), 0f)
        assertEquals(100f, TelegramThemeReveal.radius(false, 100f, 0f), 0f)
        assertEquals(0f, TelegramThemeReveal.radius(false, 100f, 1f), 0f)
        assertEquals(400, TelegramThemeReveal.DURATION)
        val e = TelegramThemeReveal.EASE_IN_OUT_QUAD
        assertEquals(0.5f, e.transform(0.5f), 0.02f)
        assertTrue(e.transform(0.2f) < 0.2f)
    }

    @Test fun imageCrossfadeOnlyForFreshLoads() {
        assertTrue(TelegramImageCrossfade.shouldAnimate(fromMemoryCache = false, motionAllowed = true))
        assertFalse(TelegramImageCrossfade.shouldAnimate(fromMemoryCache = true, motionAllowed = true))
        assertFalse(TelegramImageCrossfade.shouldAnimate(fromMemoryCache = false, motionAllowed = false))
        assertEquals(150, TelegramImageCrossfade.DURATION)
    }

    @Test fun choosingAThemeGoesThroughTheSwitcherWithTheRowCentre() {
        val calls = mutableListOf<Offset>(); val themes = mutableListOf<String>()
        compose.setContent { DuaTheme(LocalPreferences()) {
            CompositionLocalProvider(LocalThemeSwitcher provides ThemeSwitcher { at, change -> calls += at; change() }) {
                AppearanceContent(AppearancePreferences(theme = "light"), LocalPreferences(), onBack = {}, onTheme = { themes += it }, onFont = {}, onHaptic = {})
            }
        } }
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.onNodeWithText("Light").performScrollTo().performClick() // already selected: no switch
        compose.runOnIdle {
            assertEquals(listOf("dark"), themes)
            assertEquals(1, calls.size)
            assertTrue(calls.single().y > 0f)
        }
    }

    @Test fun hostRunsTheChangeAndRendersContent() {
        var changed = 0
        compose.setContent { DuaTheme(LocalPreferences()) {
            TelegramThemeSwitchHost(isDark = false, motionAllowed = true) {
                val s = LocalThemeSwitcher.current
                androidx.compose.material3.TextButton(onClick = { s.switch(Offset(10f, 10f)) { changed++ } }) { androidx.compose.material3.Text("Toggle") }
            }
        } }
        compose.onNodeWithText("Toggle").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, changed) }
        compose.onNodeWithText("Toggle").assertExists()
    }
}

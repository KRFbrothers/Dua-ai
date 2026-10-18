package com.dua.ai.motion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DuaMotionTest {

    private lateinit var context: Context
    private lateinit var motionConfig: DuaMotionConfig
    private lateinit var hapticManager: DuaHapticManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        motionConfig = DuaMotionConfig(context)
        hapticManager = DuaHapticManager(context)
    }

    @Test
    @Config(sdk = [33])
    fun testShaderSupportedOnApi33() {
        assertTrue(motionConfig.isShaderSupported)
    }

    @Test
    @Config(sdk = [31])
    fun testShaderNotSupportedBelowApi33() {
        assertFalse(motionConfig.isShaderSupported)
    }

    @Test
    fun testReducedMotionDefaultsToFalse() {
        assertFalse(motionConfig.isReducedMotionEnabled)
        assertEquals(1.0f, motionConfig.animationScale, 0.01f)
    }

    @Test
    fun testHapticManagerDoesNotCrash() {
        // Safe to call on any device configuration
        hapticManager.onTokenTick()
        hapticManager.onStateChange()
        hapticManager.onAgentStepComplete()
    }

    @Test
    fun testMotionTokens() {
        assertEquals(150, com.dua.ai.ui.theme.DuaMotion.DurationFast)
        assertEquals(250, com.dua.ai.ui.theme.DuaMotion.DurationNormal)
        assertEquals(400, com.dua.ai.ui.theme.DuaMotion.DurationSlow)
        assertNotNull(com.dua.ai.ui.theme.DuaMotion.bouncy<Float>())
        assertNotNull(com.dua.ai.ui.theme.DuaMotion.gentle<Float>())
        assertNotNull(com.dua.ai.ui.theme.DuaMotion.fast<Float>())
    }
}

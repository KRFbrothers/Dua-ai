package com.dua.ai.motion

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Device tiering and motion configuration manager.
 * Determines whether the device can safely run hardware-accelerated AGSL RuntimeShaders,
 * high-framerate fluid animations, or should fall back to lightweight Compose gradients and simple transitions.
 */
class DuaMotionConfig(private val context: Context) {

    /**
     * True if the device supports AGSL RuntimeShader (Android 13 / API 33+).
     * On earlier versions, shaders fall back to Compose Brush gradients.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val isShaderSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Conservative eligibility for optional shader effects: API support and a non-low-RAM device.
     * This does not measure refresh rate, GPU throughput or thermal headroom.
     */
    val isHighPerformanceTier: Boolean
        get() {
            if (!isShaderSupported) return false
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            return activityManager?.isLowRamDevice == false
        }

    /**
     * Checks if the user enabled "Remove animations" / "Reduce motion" in Android Accessibility settings.
     * When true, all continuous or playful animations should instantly resolve or use standard Crossfade.
     */
    val isReducedMotionEnabled: Boolean
        get() = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0.0f
        }.getOrDefault(false)

    /**
     * Recommended animation duration multiplier based on user accessibility preferences.
     */
    val animationScale: Float
        get() = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        }.getOrDefault(1.0f)
}

package com.dua.ai.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dua.ai.motion.DuaHapticManager
import com.dua.ai.motion.DuaMotionConfig

/** Standard motion physics tokens adhering to Dua AI animation guidelines. */
object DuaMotion {
    const val DurationFast = 150
    const val DurationNormal = 250
    const val DurationSlow = 400

    fun <T> bouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> gentle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> fast() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/** False when the system "Remove animations" setting is on (animator duration scale 0): UI motion should then snap. */
@Composable
fun rememberMotionAllowed(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/** Remember the device tier and motion capabilities configuration. */
@Composable
fun rememberDuaMotionConfig(): DuaMotionConfig {
    val context = LocalContext.current.applicationContext
    return remember(context) { DuaMotionConfig(context) }
}

/** Remember the tactile haptic manager. */
@Composable
fun rememberDuaHapticManager(): DuaHapticManager {
    val context = LocalContext.current.applicationContext
    return remember(context) { DuaHapticManager(context) }
}

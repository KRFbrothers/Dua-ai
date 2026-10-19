package com.dua.ai.ui.theme

import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
    val owner = LocalLifecycleOwner.current
    fun read() = runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    var allowed by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver, owner) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { allowed = read() }
        }
        val registered = runCatching {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            true
        }.getOrDefault(false)
        val lifecycleObserver = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = read() }
        owner.lifecycle.addObserver(lifecycleObserver)
        allowed = read()
        onDispose {
            if (registered) resolver.unregisterContentObserver(observer)
            owner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return allowed
}

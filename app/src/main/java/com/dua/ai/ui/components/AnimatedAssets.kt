package com.dua.ai.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.dua.ai.ui.theme.rememberMotionAllowed

/** Glue for Rive/Lottie: every animated asset has a static fallback (reduce motion, load failure, JVM tests). */
object AnimatedAssets {
    /** Rive needs its native library; Robolectric/JVM tests cannot load it. */
    internal val riveSupported: Boolean get() = Build.FINGERPRINT != "robolectric"

    @Volatile private var riveReady: Boolean? = null

    /** Rive.init loads librive-android.so; any failure (missing ABI, old device) means "use the fallback". */
    fun ensureRive(context: android.content.Context): Boolean = riveReady ?: synchronized(this) {
        riveReady ?: (riveSupported && runCatching { app.rive.runtime.kotlin.core.Rive.init(context.applicationContext) }.isSuccess)
            .also { riveReady = it }
    }

    /** Lottie icons for Settings rows (useAnimations, MIT): file in assets/lottie by row label. */
    val settingsIcons = mapOf(
        "General" to "lottie/settings.json",
        "Notifications" to "lottie/notification.json",
        "Voice" to "lottie/microphone.json",
        "Safety" to "lottie/shield.json",
        "Security and login" to "lottie/lock.json",
        "Storage" to "lottie/database.json",
    )
}

/** Rive animation from res/raw; shows [fallback] when motion is off or Rive cannot load. Decorative. */
@Composable
fun RiveOrFallback(resId: Int, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val motion = rememberMotionAllowed()
    val context = androidx.compose.ui.platform.LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val ready = remember { motion && AnimatedAssets.ensureRive(context) }
    if (!motion || !ready || failed) { fallback(); return }
    AndroidView(
        factory = { ctx ->
            app.rive.runtime.kotlin.RiveAnimationView(ctx).also { view ->
                runCatching { view.setRiveResource(resId, autoplay = true) }.onFailure { failed = true }
            }
        },
        modifier = modifier.testTag("rive-loader").clearAndSetSemantics { },
        onRelease = { runCatching { it.stop() } },
    )
}

/**
 * Lottie icon tinted to [tint]: plays once when shown and again whenever [playKey] changes. Static [fallback] icon
 * when motion is off or the composition fails to load.
 */
@Composable
fun LottieIconOrFallback(assetPath: String, fallback: ImageVector, tint: Color, size: Dp, playKey: Int = 0, modifier: Modifier = Modifier) {
    val motion = rememberMotionAllowed()
    val result = rememberLottieComposition(LottieCompositionSpec.Asset(assetPath))
    val composition = result.value
    if (!motion || result.isFailure || composition == null) {
        Icon(fallback, null, tint = tint, modifier = modifier.size(size)); return
    }
    var restart by remember { mutableStateOf(0) }
    LaunchedEffect(playKey) { restart++ }
    val progress by animateLottieCompositionAsState(composition, iterations = 1, restartOnPlay = true, isPlaying = restart > 0,
        cancellationBehavior = com.airbnb.lottie.compose.LottieCancellationBehavior.OnIterationFinish)
    val argb = tint.toArgb()
    val props = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, argb, "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, argb, "**"),
    )
    Box(modifier.size(size).testTag("lottie-icon")) {
        LottieAnimation(composition, { progress }, dynamicProperties = props, modifier = Modifier.size(size))
    }
}

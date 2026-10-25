/*
 * Adapted from Androidify's ColorSplashTransition.kt (https://github.com/android/androidify, read through
 * the fork https://github.com/KRFbrothers/androidify), Copyright 2025 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md. Changes: animates a radius
 * that reaches the farthest corner exactly (no 1.1x oversize), Dua's M3 tween specs instead of the
 * expressive motion scheme, and no animation at all when the system turns animations off.
 */
package com.dua.ai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import kotlin.math.hypot
import kotlin.math.max

/** One splash: where it starts (root pixels), its colour, and what happens once the screen is covered. */
data class ColorSplashRequest(val origin: Offset, val color: Color, val id: Long = System.nanoTime())

/** Radius that covers the whole [size] from [origin] (distance to the farthest corner). */
fun splashRadius(size: Size, origin: Offset): Float = max(
    max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
    max(hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y)),
)

const val SPLASH_GROW_MS = 320
const val SPLASH_SHRINK_MS = 420

/**
 * Grows a circle from [request]'s origin to fill the screen, calls [onCovered] (navigate or switch mode
 * there), then shrinks it back and calls [onFinished]. With [motion] off both callbacks run at once.
 */
@Composable
fun ColorSplash(request: ColorSplashRequest, motion: Boolean, onCovered: () -> Unit, onFinished: () -> Unit) {
    val covered by rememberUpdatedState(onCovered)
    val finished by rememberUpdatedState(onFinished)
    val progress = remember(request.id) { Animatable(0f) }
    LaunchedEffect(request.id, motion) {
        if (!motion) { covered(); finished(); return@LaunchedEffect }
        progress.animateTo(1f, tween(SPLASH_GROW_MS, easing = FastOutSlowInEasing))
        covered()
        progress.animateTo(0f, tween(SPLASH_SHRINK_MS, easing = FastOutSlowInEasing))
        finished()
    }
    if (!motion) return
    Canvas(Modifier.fillMaxSize().testTag("color-splash")) {
        drawCircle(request.color, radius = splashRadius(size, request.origin) * progress.value, center = request.origin)
    }
}

/*
 * The loading sweep in [ShimmerBox] is adapted from Androidify's LoadingShimmerOverlay.kt
 * (https://github.com/android/androidify), Copyright 2025 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md.
 * Changes: Dua colours, gated by the reduce-motion and foreground checks, combined with Dua's pulse.
 */
package com.dua.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import com.dua.ai.ui.chat.rememberReplyMotionAllowed
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import kotlin.math.max

/** Shared loading-shimmer values (one place instead of a copy per screen). */
object ShimmerDefaults {
    /** Dim end of the pulse for icons and labels. */
    const val Low = 0.55f
    /** Pulse range of a placeholder box, and its still value when motion is off. */
    const val BoxLow = 0.35f
    const val BoxHigh = 0.7f
    const val BoxRest = 0.5f
    const val SweepMs = 1500
}

/**
 * The one loading pulse used across Dua. Returns an alpha to read in the draw phase
 * (`graphicsLayer { alpha = shimmer() }`), so it never recomposes the content. When [enabled] is false,
 * the system removes animations, or the app is in the background, it stays at [rest].
 */
@Composable
fun rememberShimmerAlpha(
    enabled: Boolean = true,
    low: Float = ShimmerDefaults.Low,
    high: Float = 1f,
    rest: Float = high,
): () -> Float {
    val motion = enabled && rememberReplyMotionAllowed()
    if (!motion) return remember(rest) { { rest } }
    val value = rememberInfiniteTransition(label = "dua-shimmer").animateFloat(low, high,
        infiniteRepeatable(tween(ChatSizes.placeholderPulseMs, easing = LinearEasing), RepeatMode.Reverse), label = "dua-shimmer-alpha")
    return remember(value) { { value.value } }
}

/** Placeholder while media loads or is generated: a pulsing fill with a soft light sweep. Still when motion is off. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val fill = ChatTheme.colors.shimmer
    val motion = rememberReplyMotionAllowed()
    val alpha = rememberShimmerAlpha(motion, ShimmerDefaults.BoxLow, ShimmerDefaults.BoxHigh, ShimmerDefaults.BoxRest)
    val sweep = if (motion) rememberInfiniteTransition(label = "dua-shimmer-sweep").animateFloat(0f, 1f,
        infiniteRepeatable(tween(ShimmerDefaults.SweepMs, easing = LinearEasing)), label = "dua-shimmer-progress") else null
    val highlight = if (fill.luminance() > 0.5f) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f)
    Box(modifier.testTag("shimmer-box").graphicsLayer { this.alpha = alpha() }.background(fill).drawWithContent {
        drawContent()
        val progress = sweep?.value ?: return@drawWithContent
        drawRect(Brush.radialGradient(listOf(highlight.copy(alpha = 0f), highlight, highlight.copy(alpha = 0f)),
            center = Offset.Zero, radius = (max(size.width, size.height) * progress * 2.5f).coerceAtLeast(0.01f)))
    })
}

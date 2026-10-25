/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java (crossfade from the
 * placeholder when an image arrives from network/disk, not from the memory cache: DEFAULT_CROSSFADE_DURATION = 150,
 * currentAlpha += dt / crossfadeDuration) (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756).
 * Original: Telegram for Android, Copyright Nikolai Kudashov, 2013-2018, https://github.com/DrKLO/Telegram
 * (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756), licensed under GNU GPL v. 2 or later.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details
 * (LICENSE in the repository root, or <https://www.gnu.org/licenses/>).
 */
package com.dua.ai.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object TelegramImageCrossfade {
    /** ImageReceiver.DEFAULT_CROSSFADE_DURATION (linear alpha). */
    const val DURATION = 150
    /** Dua roadmap: blur 25 -> 0 over 500 ms on top of Telegram's crossfade (Android 12+; no-op below). */
    const val BLUR_START_DP = 25f
    const val BLUR_MILLIS = 500
    /** ImageReceiver only crossfades when the bitmap did not come from the memory cache. */
    fun shouldAnimate(fromMemoryCache: Boolean, motionAllowed: Boolean) = !fromMemoryCache && motionAllowed
}

/** Fade (and un-blur) a freshly loaded image in over its placeholder. */
@Composable
fun Modifier.imageReveal(animate: Boolean): Modifier {
    if (!animate) return this
    val alpha = remember { Animatable(0f) }
    val blur = remember { Animatable(TelegramImageCrossfade.BLUR_START_DP) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(TelegramImageCrossfade.DURATION, easing = LinearEasing)) }
        blur.animateTo(0f, tween(TelegramImageCrossfade.BLUR_MILLIS, easing = TelegramCubicBezier.EASE_OUT))
    }
    return graphicsLayer { this.alpha = alpha.value }.blur(blur.value.dp)
}

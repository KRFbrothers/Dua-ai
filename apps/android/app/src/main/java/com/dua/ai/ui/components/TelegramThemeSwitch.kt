/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java (needSetDayNightTheme:
 * window snapshot + circular reveal, finalRadius, 400 ms Easings.easeInOutQuad) and ui/Components/Easings.java
 * (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756).
 * Original: Telegram for Android, Copyright Nikolai Kudashov, 2013-2018, https://github.com/DrKLO/Telegram
 * (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756), licensed under GNU GPL v. 2 or later.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details
 * (LICENSE in the repository root, or <https://www.gnu.org/licenses/>).
 */
package com.dua.ai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.dua.ai.ui.chat.TelegramCubicBezier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/** LaunchActivity theme-switch numbers. */
object TelegramThemeReveal {
    const val DURATION = 400 // anim.setDuration(400)
    /** Easings.easeInOutQuad */
    val EASE_IN_OUT_QUAD = TelegramCubicBezier(0.455f, 0.03f, 0.515f, 0.955f)

    /** finalRadius: distance from the tap to the farthest corner. */
    fun finalRadius(w: Float, h: Float, x: Float, y: Float): Float {
        val r1 = max(sqrt((w - x) * (w - x) + (h - y) * (h - y)), sqrt(x * x + (h - y) * (h - y)))
        val r2 = max(sqrt((w - x) * (w - x) + y * y), sqrt(x * x + y * y))
        return max(r1, r2)
    }

    /** toDark: the new screen grows 0 -> R under the old snapshot; to light: the old snapshot shrinks R -> 0. */
    fun radius(toDark: Boolean, finalRadius: Float, progress: Float) = if (toDark) finalRadius * progress else finalRadius * (1f - progress)
}

/** Ask the root to snapshot the screen, run [change] (which flips the theme) and reveal it from [center] (root px). */
fun interface ThemeSwitcher { fun switch(center: Offset, change: () -> Unit) }

val LocalThemeSwitcher = staticCompositionLocalOf<ThemeSwitcher> { ThemeSwitcher { _, change -> change() } }

/**
 * Root wrapper: records the screen into a graphics layer (Telegram snapshots the window), and when the theme's
 * darkness flips after a [ThemeSwitcher.switch], draws the old snapshot clipped by the animated circle.
 */
@Composable
fun TelegramThemeSwitchHost(isDark: Boolean, motionAllowed: Boolean, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var fromDark by remember { mutableStateOf(isDark) }
    var animating by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val currentDark by androidx.compose.runtime.rememberUpdatedState(isDark)
    val switcher = remember(motionAllowed) {
        ThemeSwitcher { at, change ->
            if (!motionAllowed || snapshot != null) { change(); return@ThemeSwitcher }
            scope.launch {
                val shot = runCatching { layer.toImageBitmap() }.getOrNull()
                if (shot != null) { center = at; fromDark = currentDark; progress.snapTo(0f); snapshot = shot }
                change()
                // The theme may not change darkness (e.g. System -> Light on a light phone): drop the snapshot.
                delay(600)
                if (!animating) snapshot = null
            }
        }
    }
    LaunchedEffect(isDark) {
        if (snapshot != null && isDark != fromDark) {
            animating = true
            progress.animateTo(1f, tween(TelegramThemeReveal.DURATION, easing = TelegramThemeReveal.EASE_IN_OUT_QUAD))
            snapshot = null; animating = false
        }
    }
    CompositionLocalProvider(LocalThemeSwitcher provides switcher) {
        Box(Modifier.fillMaxSize().drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            val shot = snapshot ?: return@drawWithContent
            val toDark = isDark && !fromDark
            if (!animating) { drawImage(shot); return@drawWithContent } // hold the old frame until the reveal starts
            val r = TelegramThemeReveal.radius(toDark, TelegramThemeReveal.finalRadius(size.width, size.height, center.x, center.y), progress.value)
            val circle = Path().apply { addOval(Rect(center, r)) }
            val clip = if (toDark) Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(Offset.Zero, Size(size.width, size.height))); addOval(Rect(center, r)) } else circle
            clipPath(clip) { drawImage(shot) }
        }) { content() }
    }
}

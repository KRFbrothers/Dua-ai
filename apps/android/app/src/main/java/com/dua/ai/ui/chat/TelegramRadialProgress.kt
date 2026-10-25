/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/ui/Components/RadialProgressView.java
 * (indeterminate "noProgress" spinner: rotationTime 2000, risingTime 500, arc 4 + 266 accelerate / 4 - 270 decelerate)
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
package com.dua.ai.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** RadialProgressView's indeterminate state machine (pure, unit-tested). Angles in degrees. */
class TelegramRadialProgressState {
    var radOffset = 0f; private set
    var currentCircleLength = 0f; private set
    private var risingCircleLength = false
    private var currentProgressTime = 0f

    /** updateAnimation(dt) for noProgress = true, toCircleProgress = 0. dt is capped at 17 ms as in Telegram. */
    fun update(dtMillis: Long) {
        val dt = dtMillis.coerceAtMost(17).toFloat()
        radOffset += 360 * dt / ROTATION_TIME
        val count = (radOffset / 360).toInt()
        radOffset -= count * 360
        currentProgressTime += dt
        if (currentProgressTime >= RISING_TIME) currentProgressTime = RISING_TIME
        val t = currentProgressTime / RISING_TIME
        currentCircleLength = if (risingCircleLength) 4 + 266 * accelerate(t) else 4 - 270 * (1.0f - decelerate(t))
        if (currentProgressTime == RISING_TIME) {
            if (risingCircleLength) { radOffset += 270; currentCircleLength = -266f }
            risingCircleLength = !risingCircleLength
            currentProgressTime = 0f
        }
    }

    companion object {
        const val ROTATION_TIME = 2000f
        const val RISING_TIME = 500f
        private fun accelerate(t: Float) = t * t // AccelerateInterpolator()
        private fun decelerate(t: Float) = 1f - (1f - t) * (1f - t) // DecelerateInterpolator()
    }
}

/** Telegram's spinner (onDraw: drawArc(rect, radOffset, currentCircleLength, false, paint), round cap, 3 dp stroke). */
@Composable
fun TelegramRadialProgress(color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 3.dp) {
    val state = remember { TelegramRadialProgressState() }
    var tick by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { now -> state.update(now - last); last = now; tick = now.toFloat() }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        @Suppress("UNUSED_EXPRESSION") tick
        val w = strokeWidth.toPx()
        drawArc(color, state.radOffset, state.currentCircleLength, false, topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

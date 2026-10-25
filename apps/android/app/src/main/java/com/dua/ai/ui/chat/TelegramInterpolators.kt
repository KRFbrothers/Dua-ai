/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/ui/Components/CubicBezierInterpolator.java, ui/recyclerview/ChatListItemAnimator.java and ActionBar/ActionBarPopupWindow.java (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756).
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

import androidx.compose.animation.core.Easing

/**
 * Kotlin port of Telegram's CubicBezierInterpolator (Newton-Raphson on x, then evaluate y) as a Compose [Easing].
 */
class TelegramCubicBezier(startX: Float, private val startY: Float, endX: Float, private val endY: Float) : Easing {
    private val sx = startX
    private val ex = endX

    init {
        require(startX in 0f..1f) { "startX value must be in the range [0, 1]" }
        require(endX in 0f..1f) { "endX value must be in the range [0, 1]" }
    }

    override fun transform(fraction: Float): Float = getBezierCoordinateY(getXForTime(fraction))

    private fun getBezierCoordinateY(time: Float): Float {
        val cy = 3 * startY
        val by = 3 * (endY - startY) - cy
        val ay = 1 - cy - by
        return time * (cy + time * (by + time * ay))
    }

    private fun getXForTime(time: Float): Float {
        var x = time
        for (i in 1 until 14) {
            val z = getBezierCoordinateX(x) - time
            if (kotlin.math.abs(z) < 1e-3) break
            x -= z / getXDerivate(x)
        }
        return x
    }

    private fun getXDerivate(t: Float): Float {
        val cx = 3 * sx; val bx = 3 * (ex - sx) - cx; val ax = 1 - cx - bx
        return cx + t * (2 * bx + 3 * ax * t)
    }

    private fun getBezierCoordinateX(time: Float): Float {
        val cx = 3 * sx; val bx = 3 * (ex - sx) - cx; val ax = 1 - cx - bx
        return time * (cx + time * (bx + time * ax))
    }

    companion object {
        val DEFAULT = TelegramCubicBezier(0.25f, 0.1f, 0.25f, 1f)
        val EASE_OUT = TelegramCubicBezier(0f, 0f, .58f, 1f)
        val EASE_OUT_QUINT = TelegramCubicBezier(.23f, 1f, .32f, 1f)
        val EASE_IN = TelegramCubicBezier(.42f, 0f, 1f, 1f)
        val EASE_BOTH = TelegramCubicBezier(.42f, 0f, .58f, 1f)
    }
}

/** Telegram ChatListItemAnimator.DEFAULT_INTERPOLATOR / DEFAULT_DURATION, used for new message bubbles. */
object TelegramChatAnimator {
    val DEFAULT_INTERPOLATOR = TelegramCubicBezier(0.19919472913616398f, 0.010644531250000006f, 0.27920937042459737f, 0.91025390625f)
    const val DEFAULT_DURATION = 250
}

/** android.view.animation.DecelerateInterpolator (factor 1), which Telegram uses for swipe return and popup items. */
val TelegramDecelerate = Easing { t -> 1f - (1f - t) * (1f - t) }

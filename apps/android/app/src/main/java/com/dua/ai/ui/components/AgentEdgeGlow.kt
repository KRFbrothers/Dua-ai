/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from Androidify core/theme/src/main/java/com/android/developers/androidify/theme/components/AnimatingGradientBrush.kt
 * (https://github.com/android/androidify, commit a4be10fe). Changes (Dua, GPL-3.0-or-later as part of Dua): the
 * brush is drawn as a screen-edge stroke while the agent works; fades in/out; static brush when animations are off.
 * See THIRD_PARTY_NOTICES.md.
 */
package com.dua.ai.ui.components

import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.rememberMotionAllowed

/** Androidify: LinearGradientBrush that animates back and forth with the provided set of colors. */
@Composable
fun Brush.Companion.infinitelyAnimatingLinearGradient(
    colors: List<Color>,
    animationSpec: DurationBasedAnimationSpec<Float> = tween(durationMillis = 2000, easing = LinearEasing),
    animate: Boolean = true,
): Brush {
    val offset = if (animate) {
        val infiniteTransition = rememberInfiniteTransition(label = "edge-glow")
        val value by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = animationSpec, repeatMode = RepeatMode.Reverse),
            label = "edge-glow-offset",
        )
        value
    } else 0f
    return remember(offset, colors) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val widthOffset = size.width * offset
                val heightOffset = size.height * offset
                return LinearGradientShader(
                    colors = colors,
                    from = Offset(widthOffset, heightOffset),
                    to = Offset(widthOffset + size.width, heightOffset + size.height),
                    tileMode = TileMode.Mirror,
                )
            }
        }
    }
}

object AgentEdgeGlow {
    val STROKE = 3.dp
    val CORNER = 24.dp
    const val FADE_MILLIS = 400
    const val CYCLE_MILLIS = 2400
}

/** Dua glue: a soft animated gradient around the screen edge while [active] (the agent is working). Decorative. */
@Composable
fun Modifier.agentEdgeGlow(active: Boolean, colors: List<Color>): Modifier {
    val motion = rememberMotionAllowed()
    val alpha by animateFloatAsState(if (active) 1f else 0f, tween(if (motion) AgentEdgeGlow.FADE_MILLIS else 0), label = "edge-glow-alpha")
    if (alpha == 0f && !active) return this
    val brush = Brush.infinitelyAnimatingLinearGradient(colors, tween(AgentEdgeGlow.CYCLE_MILLIS, easing = LinearEasing), animate = motion)
    return drawWithContent {
        drawContent()
        val w = AgentEdgeGlow.STROKE.toPx()
        val r = AgentEdgeGlow.CORNER.toPx()
        drawRoundRect(brush, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(r, r), style = Stroke(w), alpha = alpha * 0.9f)
    }
}

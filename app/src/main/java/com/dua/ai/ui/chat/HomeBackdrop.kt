package com.dua.ai.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import com.dua.ai.ui.theme.AmbientDark
import com.dua.ai.ui.theme.AmbientLight
import com.dua.ai.ui.theme.AmbientTokens
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Standard Home/chat decoration only; no layout, inset, account or feature ownership. */
@Composable
internal fun Modifier.homeBackdrop(task: Boolean, home: Boolean = true): Modifier {
    val dark = DuaTheme.colors.isDark
    val palette = if (dark) AmbientDark else AmbientLight
    val base = ChatTheme.colors.background
    val motion = rememberReplyMotionAllowed()
    val transition = if (motion) rememberInfiniteTransition(label = "ambient-backdrop") else null
    val drift = transition?.animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(AmbientTokens.driftMs, easing = LinearEasing)), label = "ambient-drift")
    val breathe = transition?.animateFloat(0.78f, 1.18f,
        infiniteRepeatable(tween(AmbientTokens.breatheMs, easing = LinearEasing), RepeatMode.Reverse), label = "ambient-breathe")
    // A zero-duration animation still schedules a later update. Static mode uses the actual
    // target immediately, and creates no finite or infinite animation while paused/reduced.
    val taskTarget = if (task) 1f else 0f
    val intensityTarget = if (home) AmbientTokens.homeIntensity else AmbientTokens.conversationIntensity
    val washTarget = if (home) 1f else AmbientTokens.conversationWash
    val taskFraction = if (motion) animateFloatAsState(taskTarget, tween(AmbientTokens.modeMs), label = "Home ambient mode")
        else rememberUpdatedState(taskTarget)
    val intensity = if (motion) animateFloatAsState(intensityTarget, tween(AmbientTokens.modeMs), label = "ambient-intensity")
        else rememberUpdatedState(intensityTarget)
    val washAlpha = if (motion) animateFloatAsState(washTarget, tween(AmbientTokens.modeMs), label = "ambient-wash")
        else rememberUpdatedState(washTarget)
    return testTag(if (home) "home-gradient" else "conversation-ambient").drawWithCache {
        val w = size.width
        val h = size.height
        val longest = maxOf(w, h)
        val baseWash = Brush.verticalGradient(0f to base, 0.55f to base, 1f to palette.baseEnd)
        val skyWash = Brush.verticalGradient(listOf(Color.Transparent, palette.sky.copy(alpha = if (dark) 187 / 255f else 221 / 255f)),
            startY = h * 0.45f, endY = h)
        val taskWash = Brush.radialGradient(listOf(palette.task.copy(alpha = if (dark) 0.8f else 221 / 255f), Color.Transparent),
            center = Offset(0f, h), radius = h * 0.72f)
        // Match the selected preview's four radial blobs. Cache brushes/geometry per size/theme.
        val colors = listOf(palette.blue, palette.purple, palette.magenta, palette.orange)
        val x = floatArrayOf(.16f, .86f, .24f, .90f)
        val y = floatArrayOf(.10f, .24f, .86f, .92f)
        val radius = floatArrayOf(.62f, .58f, .60f, .52f)
        val orbitX = floatArrayOf(.10f, .08f, .09f, .07f)
        val orbitY = floatArrayOf(.05f, .07f, .06f, .08f)
        val phase = floatArrayOf(0f, .28f, .55f, .78f)
        val alpha = floatArrayOf(.34f, .30f, .26f, .22f)
        val brushes = colors.mapIndexed { i, color ->
            Brush.radialGradient(0f to color.copy(alpha = alpha[i]), .45f to color.copy(alpha = alpha[i] * .35f),
                1f to Color.Transparent, center = Offset.Zero, radius = longest * radius[i])
        }
        onDrawBehind {
            // Infinite states are read only while drawing; the message list/composer do not recompose per frame.
            val angle = drift?.value ?: 0f
            val gain = (breathe?.value ?: .78f) * (if (dark) 1f else .72f) * intensity.value
            clipRect {
                drawRect(baseWash)
                for (i in brushes.indices) {
                    val orbit = angle + phase[i] * (2 * PI).toFloat()
                    translate(x[i] * w + cos(orbit) * orbitX[i] * w, y[i] * h + sin(orbit) * orbitY[i] * h) {
                        drawCircle(brushes[i], radius = longest * radius[i], center = Offset.Zero, alpha = gain)
                    }
                }
                drawRect(skyWash, alpha = washAlpha.value)
                drawRect(taskWash, alpha = taskFraction.value * washAlpha.value)
            }
        }
    }
}

package com.dua.ai.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.rememberMotionAllowed

/** Decorative empty-Home layer only. Conversation/feature surfaces and shared tokens stay intact. */
@Composable
internal fun Modifier.homeBackdrop(task: Boolean): Modifier {
    val dark = DuaTheme.colors.isDark
    val base = ChatTheme.colors.background
    val sky = if (dark) Color(0xFF173853) else Color(0xFFB9DEFF)
    val violet = if (dark) Color(0xFF4A3588) else Color(0xFF9294FF)
    val duration = if (rememberMotionAllowed()) 500 else 0
    val taskFraction by animateFloatAsState(if (task) 1f else 0f, tween(duration), label = "Home ambient mode")
    return testTag("home-gradient").drawWithCache {
        val chatWash = Brush.verticalGradient(
            0f to base, 0.5f to base,
            0.76f to sky.copy(alpha = 0.4f).compositeOver(base), 1f to sky,
        )
        val taskWash = Brush.verticalGradient(
            0f to base, 0.5f to base,
            0.76f to sky.copy(alpha = 0.22f).compositeOver(base), 1f to sky,
        )
        val glow = Brush.radialGradient(
            colors = listOf(violet, Color.Transparent),
            center = Offset(0f, size.height), radius = maxOf(size.width, size.height * 0.55f),
        )
        // Geometry adapted from the supplied DuaSilkyWaveBackground. Build both end states
        // once per size/theme and blend them, without rebuilding paths during the mode tween.
        val w = size.width
        val h = size.height
        fun wave(fraction: Float) = Path().apply {
            moveTo(0f, h * (0.56f + 0.04f * fraction))
            cubicTo(w * 0.28f, h * (0.66f - 0.03f * fraction),
                w * 0.64f, h * (0.76f - 0.04f * fraction), w, h * (0.71f - 0.02f * fraction))
            lineTo(w, h); lineTo(0f, h); close()
        }
        val chatWave = wave(0f)
        val taskWave = wave(1f)
        val chatSheen = Brush.verticalGradient(listOf(sky.copy(alpha = 0.28f), Color.Transparent), h * 0.56f, h)
        val taskSheen = Brush.verticalGradient(listOf(violet.copy(alpha = 0.14f), Color.Transparent), h * 0.56f, h)
        val crest = Path().apply {
            moveTo(0f, h * 0.67f)
            cubicTo(w * 0.35f, h * 0.77f, w * 0.72f, h * 0.78f, w, h * 0.72f)
        }
        val crestColor = Color.White.copy(alpha = if (dark) 0.07f else 0.25f)
        val crestStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        onDrawBehind {
            // Read animation state in the draw phase, retaining cached brushes between frames.
            drawRect(chatWash)
            drawRect(taskWash, alpha = taskFraction)
            drawRect(glow, alpha = taskFraction)
            drawPath(chatWave, chatSheen, alpha = 1f - taskFraction)
            drawPath(taskWave, taskSheen, alpha = taskFraction)
            drawPath(crest, crestColor, style = crestStroke)
        }
    }
}

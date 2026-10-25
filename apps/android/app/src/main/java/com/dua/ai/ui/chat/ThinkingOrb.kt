package com.dua.ai.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dua.ai.ui.theme.AmbientDark
import com.dua.ai.ui.theme.AmbientLight
import com.dua.ai.ui.theme.AmbientTokens
import com.dua.ai.ui.theme.DuaTheme

/** Decorative only: the parent owns the existing “Dua is thinking” announcement. */
@Composable
internal fun ThinkingOrb(modifier: Modifier = Modifier) {
    val palette = if (DuaTheme.colors.isDark) AmbientDark else AmbientLight
    val transition = if (rememberReplyMotionAllowed()) rememberInfiniteTransition(label = "thinking-orb") else null
    val spin = transition?.animateFloat(0f, 360f,
        infiniteRepeatable(tween(AmbientTokens.spinMs, easing = LinearEasing)), label = "orb-spin")
    val pulse = transition?.animateFloat(.72f, 1f,
        infiniteRepeatable(tween(AmbientTokens.pulseMs, easing = LinearEasing), RepeatMode.Reverse), label = "orb-pulse")
    val sparkle = transition?.animateFloat(.15f, 1f,
        infiniteRepeatable(tween(AmbientTokens.sparkleMs, easing = LinearEasing), RepeatMode.Reverse), label = "orb-sparkle")
    Box(modifier.size(AmbientTokens.orbSize).clearAndSetSemantics { }.drawWithCache {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val sweep = Brush.sweepGradient(palette.sweep, center)
        val glow = Brush.radialGradient(listOf(palette.purple.copy(alpha = .55f), palette.blue.copy(alpha = .22f), Color.Transparent),
            center = center, radius = r * 1.7f)
        val highlight = Brush.radialGradient(listOf(Color.White.copy(alpha = .85f), Color.Transparent), center, r * .62f)
        val sx = center.x + r * .72f
        val sy = center.y - r * .72f
        val sr = r * .42f
        val waist = sr * .16f
        val star = Path().apply {
            moveTo(sx, sy - sr)
            quadraticTo(sx + waist, sy - waist, sx + sr, sy)
            quadraticTo(sx + waist, sy + waist, sx, sy + sr)
            quadraticTo(sx - waist, sy + waist, sx - sr, sy)
            quadraticTo(sx - waist, sy - waist, sx, sy - sr)
            close()
        }
        onDrawBehind {
            // The halo is wider than the glyph. Clipping it to 28 dp leaves a hard square.
            // Its transparent edge fits the parent's 48 dp status row and the existing text gap.
            drawCircle(glow, radius = r * 1.7f, alpha = pulse?.value ?: .72f)
            rotate(spin?.value ?: 0f) { drawCircle(sweep, radius = r * .92f) }
            drawCircle(highlight, radius = r * .5f, alpha = .5f)
            drawPath(star, Color.White, alpha = .9f * (sparkle?.value ?: .15f))
        }
    })
}

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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.ChatTheme

/** Decorative empty-Home layer only. Conversation/feature surfaces and shared tokens stay intact. */
@Composable
internal fun Modifier.homeBackdrop(task: Boolean): Modifier {
    val dark = DuaTheme.colors.isDark
    val base = ChatTheme.colors.background
    val sky = if (dark) Color(0xFF173853) else Color(0xFFB9DEFF)
    val violet = if (dark) Color(0xFF4A3588) else Color(0xFF9294FF)
    val taskFraction by animateFloatAsState(if (task) 1f else 0f, tween(500), label = "Home ambient mode")
    return testTag("home-gradient").drawWithCache {
        val chatWash = Brush.verticalGradient(
            0f to base, 0.4f to base,
            0.72f to sky.copy(alpha = 0.4f).compositeOver(base), 1f to sky,
        )
        val taskWash = Brush.verticalGradient(
            0f to base, 0.4f to base,
            0.72f to sky.copy(alpha = 0.22f).compositeOver(base), 1f to sky,
        )
        val glow = Brush.radialGradient(
            colors = listOf(violet, Color.Transparent),
            center = Offset(0f, size.height), radius = maxOf(size.width, size.height * 0.55f),
        )
        onDrawBehind {
            // Read animation state in the draw phase, retaining cached brushes between frames.
            drawRect(chatWash)
            drawRect(taskWash, alpha = taskFraction)
            drawRect(glow, alpha = taskFraction)
        }
    }
}

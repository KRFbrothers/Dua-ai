package com.dua.ai.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.dua.ai.ui.theme.DuaTheme

/** Decorative empty-Home layer only. Conversation/feature surfaces and shared tokens stay intact. */
@Composable
internal fun Modifier.homeBackdrop(task: Boolean): Modifier {
    val dark = DuaTheme.colors.isDark
    val sky = if (dark) Color(0xFF173853) else Color(0xFFB9DEFF)
    val violet = if (dark) Color(0xFF4A3588) else Color(0xFF9294FF)
    return testTag("home-gradient").drawWithCache {
        val wash = Brush.verticalGradient(
            0f to Color.Transparent, 0.4f to Color.Transparent,
            0.72f to sky.copy(alpha = if (task) 0.22f else 0.4f), 1f to sky,
        )
        val glow = Brush.radialGradient(
            colors = listOf(violet, Color.Transparent),
            center = Offset(0f, size.height), radius = maxOf(size.width, size.height * 0.55f),
        )
        onDrawBehind {
            drawRect(wash)
            if (task) drawRect(glow)
        }
    }
}

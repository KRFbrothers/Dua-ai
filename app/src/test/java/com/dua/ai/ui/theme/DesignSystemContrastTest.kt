package com.dua.ai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemContrastTest {
    private fun aa(label: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("$label contrast=$ratio", ratio >= 4.5)
    }

    @Test fun semanticTextColorsMeetAaOnTheirActualSurfacesInBothAppearances() {
        for (colors in listOf(DuaLightColors, DuaDarkColors)) {
            val scheme = if (colors.isDark) DuaDarkColorScheme else DuaLightColorScheme
            val label = "dark=${colors.isDark}"
            aa("$label code", colors.onCode, colors.codeBackground)
            aa("$label added", colors.diff.added, colors.diff.addedBackground.compositeOver(colors.codeBackground))
            aa("$label removed", colors.diff.removed, colors.diff.removedBackground.compositeOver(colors.codeBackground))
            aa("$label hunk", colors.diff.hunk, colors.codeBackground)
            for (color in listOf(colors.diff.added, colors.diff.removed)) {
                aa("$label proposal totals", color, scheme.surfaceContainerLow)
                aa("$label proposal badges", color, color.copy(alpha = .14f).compositeOver(scheme.surfaceContainerLow))
            }
            aa("$label danger", colors.onDanger, colors.danger)
            aa("$label voice heading", scheme.onSurface, colors.voice.background)
            aa("$label voice transcript", scheme.onSurfaceVariant, colors.voice.background)
            val brightestGlow = if (colors.isDark) Color.White else Color.Black
            aa("$label voice text over glow", scheme.onSurfaceVariant, colors.voice.textSurface.compositeOver(brightestGlow))
            aa("$label voice status", scheme.onSurfaceVariant, colors.voice.statusSurface.compositeOver(colors.voice.background))
            aa("$label media", colors.onMedia, colors.mediaBackground)
            // White artwork is the worst backdrop for white labels on a black overlay.
            aa("$label media over white image", colors.onMedia, colors.mediaOverlay.compositeOver(Color.White))
        }
    }

    @Test fun syntaxColorsAreReadableAndAlreadyAccessibleHuesStayUnchanged() {
        val samples = listOf(0x000000, 0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0x999999, 0xf3e3b8)
        for (colors in listOf(DuaLightColors, DuaDarkColors)) {
            for (rgb in samples) {
                val adjusted = codeSyntaxColor(rgb, colors)
                aa("syntax $rgb", adjusted, colors.codeBackground)
                aa("syntax $rgb header", adjusted, colors.codeHeader)
                val raw = Color(rgb).copy(alpha = 1f)
                if (listOf(colors.codeBackground, colors.codeHeader).all { contrast(raw, it) >= 4.5 })
                    org.junit.Assert.assertEquals(raw, adjusted)
            }
        }
    }
}

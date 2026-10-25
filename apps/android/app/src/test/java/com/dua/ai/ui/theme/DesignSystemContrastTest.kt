package com.dua.ai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.dua.ai.ui.chat.highlightCode
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import org.junit.Assert.assertEquals
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
            // End-call uses an icon, not text. Preserve the original red and check non-text AA (3:1).
            assertTrue("$label end-call icon", contrast(colors.onDanger, colors.danger) >= 3.0)
            aa("$label voice heading", scheme.onSurface, colors.voice.background)
            aa("$label voice transcript", scheme.onSurfaceVariant, colors.voice.background)
            aa("$label voice status", scheme.onSurfaceVariant, colors.voice.statusSurface.compositeOver(colors.voice.background))
            aa("$label media", colors.onMedia, colors.mediaBackground)
            // This overlay is used by the original video poster badge, not the viewer rows.
            aa("$label media over white image", colors.onMedia, colors.mediaOverlay.compositeOver(Color.White))
        }
    }

    @Test fun syntaxTokensPreserveTheOriginalAtomPaletteWithoutContrastAdjustment() {
        val code = "# comment\ndef greet(name):\n    return 'Hi ' + name\nprint(greet('Dua'))"
        val language = SyntaxLanguage.getByName("python")!!
        for (colors in listOf(DuaLightColors, DuaDarkColors)) {
            val original = Highlights.Builder().code(code).language(language).theme(SyntaxThemes.atom(colors.isDark)).build()
            val styled = highlightCode(code, "python", colors)
            val expected = original.getHighlights().filterIsInstance<ColorHighlight>()
            assertTrue(expected.isNotEmpty())
            for (highlight in expected) {
                val matching = styled.spanStyles.filter { it.start == highlight.location.start && it.end == highlight.location.end && it.item.color != Color.Unspecified }
                assertEquals(Color(highlight.rgb).copy(alpha = 1f), matching.single().item.color)
            }
        }
    }
}

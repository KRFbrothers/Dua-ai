package com.dua.ai.ui.theme

import androidx.compose.ui.graphics.Color
import com.dua.ai.settings.AccentPreset
import org.junit.Assert.*
import org.junit.Test

class AccentContrastTest {
    @Test fun allPresetsKeepExactColorsAndReadableForegroundsInBothThemes() {
        for (dark in listOf(false, true)) for (preset in AccentPreset.entries) {
            val base = if (dark) DuaDarkColorScheme else DuaLightColorScheme
            val scheme = accentedScheme(base, preset, dark)
            val accent = accentColors(preset, dark)
            assertEquals(Color(preset.argb), accent.color)
            assertTrue("${preset.name} button", contrast(accent.onColor, accent.color) >= 4.5)
            assertTrue("${preset.name} container", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5)
            for (surface in listOf(base.background, base.surface, base.surfaceVariant, base.surfaceContainerLowest, base.surfaceContainerLow,
                base.surfaceContainer, base.surfaceContainerHigh, base.surfaceContainerHighest)) {
                assertTrue("${preset.name} text on $surface dark=$dark", contrast(scheme.primary, surface) >= 4.5)
                assertTrue(contrast(base.onSurface, surface) >= 4.5)
            }
            assertEquals(base.background, scheme.background)
            assertEquals(base.surfaceContainer, scheme.surfaceContainer)
            assertEquals(base.error, scheme.error)
            assertEquals(base.secondary, scheme.secondary)
        }
    }
}

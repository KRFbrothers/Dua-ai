package com.dua.ai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Floating chrome must remain readable in both appearances, independent of its backdrop. */
class PolishedThemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val high = maxOf(a.luminance(), b.luminance())
        val low = minOf(a.luminance(), b.luminance())
        return (high + .05f) / (low + .05f)
    }

    @Test fun floatingComposerAndItsLabelsMeetAaInBothThemes() {
        for ((scheme, colors) in listOf(DuaLightColorScheme to DuaLightColors, DuaDarkColorScheme to DuaDarkColors)) {
            val backing = colors.floatingSurface.compositeOver(colors.composer)
            assertTrue(contrast(scheme.onSurface, backing) >= 4.5f)
            assertTrue(contrast(scheme.onSurfaceVariant, backing) >= 4.5f)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        }
    }

    @Test fun coreFeatureSurfacesUseTheSharedLightAndDarkCanvases() {
        assertEquals(DuaLightColorScheme.background, ChatLightColors.background)
        assertEquals(DuaDarkColorScheme.background, ChatDarkColors.background)
        assertEquals(DuaLightColorScheme.surfaceContainerLow, DrawerLightColors.container)
        assertEquals(DuaDarkColorScheme.surfaceContainerLow, SheetDarkColors.container)
        assertEquals(DuaLightColorScheme.primary, HomeLightColors.voice)
        assertEquals(DuaDarkColorScheme.primary, HomeDarkColors.voice)
    }
}

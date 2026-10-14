package com.dua.ai.ui.settings

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.dua.ai.ui.theme.PageDarkColors
import com.dua.ai.ui.theme.PageLightColors
import com.dua.ai.ui.theme.SettingsDarkColors
import com.dua.ai.ui.theme.SettingsLightColors
import com.dua.ai.ui.theme.SheetLightColors
import com.dua.ai.ui.theme.pageColorScheme
import org.junit.Assert.*
import org.junit.Test

class PageTokensTest {
    @Test fun lightPageMatchesSettingsLook() {
        val s = pageColorScheme(lightColorScheme(), PageLightColors)
        assertEquals(SettingsLightColors.background, s.background)
        assertEquals(SettingsLightColors.card, s.surface)
        assertEquals(SettingsLightColors.rowText, s.onSurface) // #111827
        assertEquals(SheetLightColors.divider, s.outlineVariant) // #E5E7EB
        assertEquals(SheetLightColors.accent, s.primary) // purple
    }
    @Test fun darkVariantUsesDarkTokens() {
        val s = pageColorScheme(darkColorScheme(), PageDarkColors)
        assertEquals(SettingsDarkColors.background, s.background)
        assertEquals(SettingsDarkColors.card, s.surface)
        assertEquals(SettingsDarkColors.rowText, s.onSurface)
        assertNotEquals(PageLightColors.background, PageDarkColors.background)
    }
}

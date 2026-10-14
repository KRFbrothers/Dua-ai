package com.dua.ai.ui.settings

import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dua.ai.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ReferenceSettingsTokensTest {
    @Test fun referenceTypographyUsesInterAndRequestedWeightsAndSizes() {
        assertEquals(InterFont, SettingsNameStyle.fontFamily)
        assertEquals(18.sp, SettingsNameStyle.fontSize); assertEquals(FontWeight.SemiBold, SettingsNameStyle.fontWeight)
        assertEquals(17.sp, SettingsRowStyle.fontSize); assertEquals(FontWeight.Normal, SettingsRowStyle.fontWeight)
        assertEquals(17.sp, SettingsValueStyle.fontSize); assertEquals(FontWeight.Normal, SettingsValueStyle.fontWeight)
        assertEquals(34.sp, WelcomeTitleStyle.fontSize); assertEquals(FontWeight.Bold, WelcomeTitleStyle.fontWeight)
        assertEquals(Sizes.touchTarget, SettingsSizes.touch)
    }
    @Test fun textAndActionsMeetAaInBothSettingsAndWelcomeThemes() {
        for (c in listOf(SettingsLightColors, SettingsDarkColors)) {
            for (foreground in listOf(c.rowText, c.value, c.upgrade, c.danger))
                assertTrue("Card text contrast", ColorUtils.calculateContrast(foreground.toArgb(), c.card.toArgb()) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(c.name.toArgb(), c.background.toArgb()) >= 4.5)
        }
        for (c in listOf(WelcomeLightColors, WelcomeDarkColors)) {
            assertTrue(ColorUtils.calculateContrast(c.text.toArgb(), c.background.toArgb()) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(c.onAction.toArgb(), c.action.toArgb()) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(c.email.toArgb(), c.action.toArgb()) >= 4.5)
        }
    }
}

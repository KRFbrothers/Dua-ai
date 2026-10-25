package com.dua.ai.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dua.ai.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ReferenceSettingsTokensTest {
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
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
                assertTrue("Card text contrast", contrast(foreground, c.card) >= 4.5)
            assertTrue(contrast(c.name, c.background) >= 4.5)
        }
        for (c in listOf(WelcomeLightColors, WelcomeDarkColors)) {
            assertTrue(contrast(c.text, c.background) >= 4.5)
            assertTrue(contrast(c.onAction, c.action) >= 4.5)
            assertTrue(contrast(c.email, c.action) >= 4.5)
        }
    }
}

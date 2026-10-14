package com.dua.ai.ui.settings

import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import com.dua.ai.ui.theme.SoehneFont
import com.dua.ai.ui.theme.fontFamilyFor
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class FontDefaultTest {
    @Test fun defaultFontIsSoehne() {
        assertEquals("default", LocalPreferences().font)
        assertEquals(SoehneFont, fontFamilyFor(LocalPreferences().font))
        assertEquals("Söhne", fontLabel("default"))
    }

    @Test fun explicitChoicesAreRespectedAndRoundTrip() {
        assertEquals(FontFamily.Serif, fontFamilyFor("serif"))
        assertEquals("Device font", fontLabel("system"))
        listOf("default", "system", "serif", "monospace").forEach {
            assertEquals(it, PreferencesCodec.decode(PreferencesCodec.encode(LocalPreferences(font = it))).font)
        }
    }
}

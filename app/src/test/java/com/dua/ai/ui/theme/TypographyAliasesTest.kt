package com.dua.ai.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dua.ai.R
import org.junit.Assert.*
import org.junit.Test

class TypographyAliasesTest {
    @Test fun aliasesDoNotChangeExistingTextOrCodeStyles() {
        assertSame(InterFont, KrfBrothersFont)
        assertSame(FontFamily.Monospace, KrfBrothersCodeFont)
        assertEquals(FontFamily(Font(R.font.krfbrothers_regular, FontWeight.Normal), Font(R.font.krfbrothers_regular, FontWeight.Medium),
            Font(R.font.krfbrothers_regular, FontWeight.SemiBold), Font(R.font.krfbrothers_regular, FontWeight.Bold)), InterFont)
        val legacy = FontFamily(Font(R.font.krfbrothers_code, FontWeight.Normal), Font(R.font.krfbrothers_code_bold, FontWeight.Bold))
        assertEquals(legacy, CodeTextStyle.fontFamily)
        assertEquals(13.5.sp, CodeTextStyle.fontSize)
        assertEquals(legacy, fontFamilyFor("monospace"))
        assertSame(InterFont, duaTypography().bodyLarge.fontFamily)
    }
}

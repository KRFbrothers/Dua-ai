package com.dua.ai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA (4.5:1) for the Home tagline and Task starter text in both themes. */
class HomeContrastTest {
    private fun ratio(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test fun taglineAndStarterTextMeetAaOnHomeSurfaces() {
        // Plain background plus the strongest wash tint seen behind the rows in the approved screenshots.
        val cases = listOf(HomeLightColors to ChatLightColors.background, HomeLightColors to Color(0xFFC7D2FE),
            HomeDarkColors to ChatDarkColors.background, HomeDarkColors to Color(0xFF3B3366))
        for ((home, bg) in cases) {
            assertTrue("tagline ${ratio(home.tagline, bg)}", ratio(home.tagline, bg) >= 4.5f)
            val row = home.promptRow.compositeOver(bg)
            assertTrue("starter ${ratio(home.promptText, row)}", ratio(home.promptText, row) >= 4.5f)
            assertTrue("starter icon ${ratio(home.promptIcon, row)}", ratio(home.promptIcon, row) >= 3f)
        }
    }
}

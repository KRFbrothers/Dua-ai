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

    @Test fun floatingTogglePillTextMeetsAaOverConversationBackgrounds() {
        val cases = listOf(HomeLightColors to ChatLightColors.background, HomeLightColors to Color(0xFF111827),
            HomeDarkColors to ChatDarkColors.background, HomeDarkColors to Color(0xFFF9FAFB))
        for ((home, under) in cases) {
            val pill = home.segmentFloatSelected.compositeOver(under)
            // Worst case: the pill sits over a solid stroke of message text (light text in dark, dark text in light).
            assertTrue("pill text ${ratio(home.segmentSelectedText, pill)} over $under", ratio(home.segmentSelectedText, pill) >= 4.5f)
        }
        for ((home, bg) in listOf(HomeLightColors to ChatLightColors.background, HomeDarkColors to ChatDarkColors.background))
            assertTrue(ratio(home.segmentText, bg) >= 4.5f)
    }
}

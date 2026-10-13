package com.dua.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dua.ai.R

/** Inter (SIL OFL 1.1, see assets/licenses/Inter-OFL.txt), bundled as a Latin subset. */
/** Montserrat Medium (SIL OFL 1.1, see assets/licenses/Montserrat-OFL.txt), Latin subset: the Home tagline only. */
val MontserratFontFamily = FontFamily(Font(R.font.montserrat_medium, FontWeight.Medium))

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Maps the saved "Font style" preference to a family. Unknown values fall back to Inter. */
fun fontFamilyFor(preference: String): FontFamily = when (preference) {
    "system" -> FontFamily.Default
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    else -> InterFontFamily
}

/**
 * One type scale for the whole app. All sizes are in sp so the system font size
 * setting scales them. Roles in use:
 * - headlineSmall: empty-state greeting
 * - titleLarge: page titles; titleMedium: section and dialog titles; titleSmall: list titles
 * - bodyLarge: messages and composer; bodyMedium: secondary text; bodySmall: captions
 * - labelLarge: buttons and chips; labelMedium: metadata; labelSmall: overlines
 */
fun duaTypography(family: FontFamily = InterFontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
        TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.em)
    return Typography(
        displayLarge = style(48, 56, FontWeight.SemiBold, -0.02),
        displayMedium = style(40, 48, FontWeight.SemiBold, -0.02),
        displaySmall = style(32, 40, FontWeight.SemiBold, -0.015),
        headlineLarge = style(28, 36, FontWeight.SemiBold, -0.01),
        headlineMedium = style(24, 32, FontWeight.SemiBold, -0.01),
        headlineSmall = style(22, 30, FontWeight.SemiBold, -0.005),
        titleLarge = style(20, 28, FontWeight.SemiBold),
        titleMedium = style(17, 24, FontWeight.SemiBold),
        titleSmall = style(15, 22, FontWeight.Medium),
        bodyLarge = style(16, 25),
        bodyMedium = style(14, 21),
        bodySmall = style(12, 17),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 16, FontWeight.Medium, 0.02),
    )
}

/** Monospace style for code blocks and inline code. */
val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, lineHeight = 20.sp)

/** Original Montserrat branding: keep Home's compact layout and voice greeting proportions. */
val HomeTaglineStyle = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp)
val HomeTaglineCompactStyle = HomeTaglineStyle.copy(fontSize = 18.sp)
val VoiceGreetingStyle = HomeTaglineStyle.copy(fontSize = 26.sp, lineHeight = 34.sp)

/** Markdown keeps the existing proportional line spacing and inline-code sizing on the chosen type scale. */
fun markdownBodyStyle(type: Typography): TextStyle = type.bodyLarge.copy(lineHeight = type.bodyLarge.fontSize * 1.55f)
fun markdownInlineCodeStyle(body: TextStyle): TextStyle = body.copy(fontFamily = FontFamily.Monospace, fontSize = body.fontSize * .9f)

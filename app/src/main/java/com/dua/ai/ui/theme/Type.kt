package com.dua.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dua.ai.R

val SoehneFont = FontFamily(
    Font(R.font.soehne_kraeftig, FontWeight.Normal)
)

val MontserratFontFamily = SoehneFont

fun fontFamilyFor(preference: String): FontFamily = when (preference) {
    "system" -> FontFamily.Default
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    else -> SoehneFont
}

fun duaTypography(family: FontFamily = SoehneFont): Typography {
    return Typography(
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 48.sp),
        displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
        displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 15.sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    )
}

val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp)
val HomeTaglineStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Medium, fontSize = 20.sp)
val HomeTaglineCompactStyle = HomeTaglineStyle.copy(fontSize = 18.sp)
val VoiceGreetingStyle = HomeTaglineStyle.copy(fontSize = 26.sp, lineHeight = 34.sp)

fun voiceTranscriptStyle(type: Typography): TextStyle = type.bodyLarge.copy(lineHeight = 22.sp)
val VoiceReplyCaptionStyle = TextStyle(fontSize = 24.sp, lineHeight = 33.sp, fontWeight = FontWeight.Normal)
val VoicePermissionHeadingStyle = VoiceGreetingStyle.copy(fontSize = 22.sp, lineHeight = 28.sp)

// Home screen + composer styles, taken from the "Dua AI Dabba" reference shell (Söhne, ChatGPT sizes).
/** Quick prompt rows on Home ("Create an image…"), Dabba bodyLarge. */
val HomePromptStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Normal, fontSize = 16.sp)
/** Composer input and its "Ask Dua AI" placeholder. */
val ComposerTextStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
/** Small pill labels. */
val PillLabelStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
/** Labels on the three Home action buttons. */
val HomeActionLabelStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)

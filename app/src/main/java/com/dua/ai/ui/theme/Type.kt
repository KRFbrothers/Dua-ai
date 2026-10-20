package com.dua.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dua.ai.R

/** Existing primary resource family; retained for backward-compatible typography. */
val InterFont: FontFamily = FontFamily(
    Font(R.font.krfbrothers_regular, FontWeight.Normal),
    Font(R.font.krfbrothers_regular, FontWeight.Medium),
    Font(R.font.krfbrothers_regular, FontWeight.SemiBold),
    Font(R.font.krfbrothers_regular, FontWeight.Bold)
)

/**
 * Google Sans (SIL OFL 1.1), byte-identical to google/fonts ofl/googlesans/GoogleSans[GRAD,opsz,wght].ttf.
 * Home only; see app/src/main/assets/licenses/GoogleSans-NOTICE.txt, GoogleSans-OFL.txt and THIRD_PARTY_NOTICES.md.
 */
@OptIn(ExperimentalTextApi::class)
val HomeFontFamily = FontFamily(
    Font(R.font.home_google_sans_variable, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.home_google_sans_variable, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

/** Existing code style keeps its bundled Menlo artwork. */
private val LegacyCodeFont = FontFamily(
    Font(R.font.krfbrothers_code, FontWeight.Normal),
    Font(R.font.krfbrothers_code_bold, FontWeight.Bold)
)

/** Inter fallback font family. */
val OriginalInterFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Public aliases for new components. Keep their definitions acyclic. */
val KrfBrothersFont: FontFamily = InterFont
val KrfBrothersCodeFont: FontFamily = FontFamily.Monospace

/** Flat backup settings reference: normal-weight headings, readable body and summaries. */
val BackupHeadingStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp)
val BackupRowTitleStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp)
val BackupBodyStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp)

val AgentTitleStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
val AgentHeroStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp)

fun fontFamilyFor(preference: String): FontFamily = when (preference) {
    "system" -> FontFamily.Default
    "serif" -> FontFamily.Serif
    "monospace" -> LegacyCodeFont
    "inter" -> OriginalInterFont
    "krfbrothers" -> KrfBrothersFont
    else -> KrfBrothersFont
}

fun duaTypography(family: FontFamily = InterFont): Typography {
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

val CodeTextStyle = TextStyle(fontFamily = LegacyCodeFont, fontSize = 13.5.sp)
val HomeTaglineStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 20.sp)
val HomeTaglineCompactStyle = HomeTaglineStyle.copy(fontSize = 18.sp)
/** Quiet Home tagline under the logo: smaller and regular weight. */
val HomeTaglineQuietStyle = HomeTaglineStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
val VoiceGreetingStyle = HomeTaglineStyle.copy(fontSize = 26.sp, lineHeight = 34.sp)

fun voiceTranscriptStyle(type: Typography): TextStyle = type.bodyLarge.copy(lineHeight = 22.sp)
val VoiceReplyCaptionStyle = TextStyle(fontSize = 24.sp, lineHeight = 33.sp, fontWeight = FontWeight.Normal)
val VoicePermissionHeadingStyle = VoiceGreetingStyle.copy(fontSize = 22.sp, lineHeight = 28.sp)

// Home screen + composer styles, taken from the "Dua AI Dabba" reference shell (Inter, standard chat sizes).
/** Quick prompt rows on Home ("Create an image…"), Dabba bodyLarge. */
val HomePromptStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 16.sp)
/** Primary brand in the Home header. */
val HomeHeaderStyle = TextStyle(fontFamily = HomeFontFamily, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 26.sp)
/** Smaller model choice, visually separate from the Dua AI brand. */
val HomeHeaderModelStyle = HomeHeaderStyle.copy(fontSize = 14.sp, lineHeight = 20.sp)
/** Home uses Google Sans; other screens retain their existing font preference. */
val HomeTaskPromptStyle = HomePromptStyle.copy(fontFamily = HomeFontFamily)
/** Composer input and its "Ask Dua AI" placeholder. */
val ComposerTextStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
/** Dynamic chat composer, independent of other existing input styles. */
val ComposerInputStyle = ComposerTextStyle.copy(fontSize = 18.sp, lineHeight = 26.sp)
/** assistant_robin_input_oneline explicitly declares an 18 sp input. */
val HomeComposerInputStyle = ComposerInputStyle.copy(fontFamily = HomeFontFamily)
val ComposerModeStyle = TextStyle(fontFamily = HomeFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)
/** Labels on the three Home action buttons. */
val HomeActionLabelStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)

// Settings, Account and welcome reference styles.
val SettingsAvatarStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Bold, fontSize = 24.sp)
val SettingsNameStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
val SettingsRowStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 17.sp)
val SettingsValueStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 17.sp)
/** Group heading above a card on the inner settings pages. */
val SettingsSectionStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

val WelcomeTitleStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.7).sp)
val WelcomeButtonStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
val WelcomeContinueStyle = WelcomeButtonStyle.copy(fontSize = 15.sp)
val WelcomeEmailStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 13.sp)

internal val ProjectHeaderStyle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
internal val ProjectDetailTitleStyle = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
internal val ProjectRowStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
internal val ProjectChatRowStyle = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal)
internal val ProjectBodyStyle = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
internal val ProjectTabStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
internal val ProjectTabSelectedStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
internal val ProjectInputStyle = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
internal val ProjectEmptyTitleStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)

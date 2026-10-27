package com.dua.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Page surfaces and controls use the shared semantic palette defined in Color.kt. */
@Immutable
data class PageColors(
    val background: Color, val card: Color, val cardBorder: Color, val fill: Color, val text: Color, val subText: Color,
    val divider: Color, val accent: Color, val onAccent: Color, val accentContainer: Color, val onAccentContainer: Color,
    val danger: Color, val outline: Color,
)

val PageLightColors = pageColors(DuaLightColorScheme, DuaLightColors)

val PageDarkColors = pageColors(DuaDarkColorScheme, DuaDarkColors)

object PageTheme {
    val colors: PageColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) PageDarkColors else PageLightColors
}

/** Maps page tokens onto a Material scheme (pure, so it is unit-testable). */
fun pageColorScheme(base: ColorScheme, p: PageColors): ColorScheme = base.copy(
    primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentContainer, onPrimaryContainer = p.onAccentContainer,
    secondary = p.accent, onSecondary = p.onAccent, secondaryContainer = p.accentContainer, onSecondaryContainer = p.onAccentContainer,
    tertiary = p.accent, onTertiary = p.onAccent,
    background = p.background, onBackground = p.text, surface = p.card, onSurface = p.text, surfaceVariant = p.fill, onSurfaceVariant = p.subText,
    surfaceTint = Color.Transparent, surfaceBright = p.card, surfaceDim = p.background,
    surfaceContainerLowest = p.card, surfaceContainerLow = p.card, surfaceContainer = p.fill, surfaceContainerHigh = p.fill, surfaceContainerHighest = p.fill,
    outline = p.outline, outlineVariant = p.divider, error = p.danger,
)

/** Wrap a full page so Material defaults use the Gemini Light & Claude Warm Dark page palette. Typography stays Inter via DuaTheme. */
@Composable
fun DuaPageTheme(content: @Composable () -> Unit) {
    val p = PageTheme.colors
    MaterialTheme(colorScheme = pageColorScheme(MaterialTheme.colorScheme, p), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides p.text, content = content)
    }
}

/** Full-page sizes (Data controls, Profile, Cloud sync, Feature setup, Onboarding, Computer). Touch slots stay 48 dp. */
object PageSizes {
    val cardPadding = 16.dp
    val cardGap = 12.dp
    val itemGap = 8.dp
    val avatar = 48.dp
    val buttonIcon = 20.dp
    val heroRing = 112.dp
    val heroIcon = 52.dp
    val heroSparkle = 26.dp
    val heroSparkleX = 6.dp
    val heroSparkleY = (-2).dp
    val heroMinHeight = 420.dp
    val heroTextMaxWidth = 320.dp
    val onboardingLogo = 168.dp
    val onboardingActionsMaxWidth = 420.dp
    val touch = 48.dp
}

/** Card heading on full pages. */
val PageCardTitleStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
/** Body copy on full pages. */
val PageBodyStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
/** Secondary / explanatory notes on full pages. */
val PageNoteStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

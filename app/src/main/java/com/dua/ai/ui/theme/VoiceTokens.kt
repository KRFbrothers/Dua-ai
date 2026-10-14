package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Voice tokens for voice mode, live voice and dictation. Every colour is taken from the shared page/sheet tokens, so the
 * accent is the same #8B5CF6 as Sheet/Page/Chat/Home (no voice-only copy). The voice-mode canvas, glow and orb keep
 * their decorative [DuaVoiceColors] roles.
 */
@Immutable
data class VoiceColors(
    val background: Color, val text: Color, val subText: Color, val pill: Color,
    val control: Color, val controlBorder: Color, val selected: Color, val onSelected: Color,
    val accent: Color, val onAccent: Color, val idle: Color, val snackbar: Color, val onSnackbar: Color,
    val error: Color, val iconFill: Color,
)

val VoiceLightColors = VoiceColors(
    background = PageLightColors.background, text = PageLightColors.text, subText = PageLightColors.subText, pill = PageLightColors.fill,
    control = PageLightColors.card, controlBorder = PageLightColors.cardBorder,
    selected = PageLightColors.accentContainer, onSelected = PageLightColors.onAccentContainer,
    accent = PageLightColors.accent, onAccent = PageLightColors.onAccent, idle = PageLightColors.outline,
    snackbar = PageLightColors.text, onSnackbar = PageLightColors.card, error = PageLightColors.danger, iconFill = PageLightColors.accentContainer,
)

val VoiceDarkColors = VoiceColors(
    background = PageDarkColors.background, text = PageDarkColors.text, subText = PageDarkColors.subText, pill = PageDarkColors.fill,
    // Dark controls sit on the near-black canvas without a border (as before).
    control = PageDarkColors.fill, controlBorder = Color.Transparent,
    selected = PageDarkColors.accentContainer, onSelected = PageDarkColors.onAccentContainer,
    accent = PageDarkColors.accent, onAccent = PageDarkColors.onAccent, idle = PageDarkColors.outline,
    snackbar = PageDarkColors.fill, onSnackbar = PageDarkColors.text, error = PageDarkColors.danger, iconFill = PageDarkColors.accentContainer,
)

object VoiceTheme {
    val colors: VoiceColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) VoiceDarkColors else VoiceLightColors
}

/** Voice sizes. Every tappable control keeps at least a 48 dp touch slot. */
object VoiceSizes {
    val touch = 48.dp
    val border = 1.dp
    val contentMaxWidth = 420.dp
    // Voice mode
    val controlMax = 56.dp
    val controlGap = 6.dp
    val statusDot = 7.dp
    val statusPaddingV = 6.dp
    val logo = 88.dp
    val replyActionsOffset = (-12).dp
    val permissionBadge = 64.dp
    val permissionIcon = 30.dp
    // Live voice
    val liveOrb = 200.dp
    val liveMute = 64.dp
    val liveMuteIcon = 28.dp
    val liveEnd = 72.dp
    val liveEndIcon = 32.dp
    val startButtonHeight = 52.dp
    // Dictation
    val cubeSlot = 160.dp
    val cube = 140.dp
    val micButton = 72.dp
    val micIcon = 32.dp
    val buttonIcon = 18.dp
}

object VoiceShapes {
    val snackbar = RoundedCornerShape(16.dp)
    val permissionCard = RoundedCornerShape(28.dp)
    val startButton = RoundedCornerShape(26.dp)
    val pill = RoundedCornerShape(50)
}

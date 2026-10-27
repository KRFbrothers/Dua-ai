package com.dua.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.snipme.highlights.model.SyntaxTheme
import dev.snipme.highlights.model.SyntaxThemes

/** Reference Agent task surface, with a readable dark counterpart. */
internal val AgentTaskSurfaceLight = Color(0xFFF8FAFD)
internal val AgentTaskSurfaceDark = Color(0xFF21201E)

/**
 * Raw palette (Layer 1). Screens never use these directly: they read [ColorScheme] roles from
 * MaterialTheme or the semantic roles in [DuaColors] / feature token objects (`HomeTheme.colors`,
 * `ChatTheme.colors`, `DrawerTheme.colors`, `SheetTheme.colors`, `SettingsTheme.colors`, `PageTheme.colors`).
 *
 * - Light Mode = Google Gemini Light Mode (#FFFFFF canvas, #F0F4F9 pills/bubbles, #F8FAFD cards, #E1E6EF borders, #D3E8FD voice pill)
 * - Dark Mode = Anthropic Claude Warm Dark Mode (#191918 canvas, #21201E cards/composer, #2B2A27 fills/bubbles, #393834 borders, #D97757 terracotta voice)
 */
internal object DuaPalette {
    // Neutrals & warm charcoal ramp, dark -> light.
    val Neutral0 = Color(0xFF09090B)
    val Neutral4 = Color(0xFF141413)
    val Neutral8 = Color(0xFF191918)
    val Neutral10 = Color(0xFF21201E)
    val Neutral13 = Color(0xFF21201E)
    val Neutral16 = Color(0xFF2B2A27)
    val Neutral22 = Color(0xFF393834)
    val Neutral30 = Color(0xFF4A4944)
    val Neutral45 = Color(0xFF5E5E5E)
    val Neutral62 = Color(0xFF9C9A92)
    val Neutral72 = Color(0xFFDCD9CE)
    val Neutral88 = Color(0xFFE1E6EF)
    val Neutral90 = Color(0xFFE1E6EF)
    val Neutral93 = Color(0xFFE8EEF5)
    val Neutral95 = Color(0xFFF0F4F9)
    val Neutral97 = Color(0xFFF8FAFD)
    val Neutral100 = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1F1F1F)
    val InkMuted = Color(0xFF444746)

    // Gold: the Home tagline sparkle and the icon of a selected mode chip.
    val Gold80 = Color(0xFFE8C46A)
    val Gold40 = Color(0xFFA67C1E)
    val Gold35 = Color(0xFF8A6514)

    // Gemini Light Blue & Claude Warm Terracotta accents.
    val GeminiBluePrimary = Color(0xFF0B57D0)
    val GeminiBlueContainer = Color(0xFFD3E8FD)
    val GeminiOnBlueContainer = Color(0xFF041E49)

    val ClaudeTerracotta = Color(0xFFD97757)
    val ClaudeOnTerracotta = Color(0xFF191918)
    val ClaudeTerracottaContainer = Color(0xFF4A2B20)
    val ClaudeOnTerracottaContainer = Color(0xFFFFDBD0)
    val ClaudeDivider = Color(0xFF2E2D2A)

    // Restrained teal tokens kept for backward compatibility.
    val Teal80 = Color(0xFF79D4C3)
    val Teal20 = Color(0xFF00382F)
    val Teal30 = Color(0xFF15463E)
    val Teal90 = Color(0xFFC4F1E7)
    val Teal40 = Color(0xFF0B7366)
    val Teal95 = Color(0xFFDDF5EF)
    val Teal10 = Color(0xFF00201B)

    val Red80 = Color(0xFFFFB4AB)
    val Red20 = Color(0xFF690005)
    val Red30 = Color(0xFF3D1716)
    val Red90 = Color(0xFFFFDAD6)
    val Red40 = Color(0xFFBA1A1A)
    val Red10 = Color(0xFF410002)
}

/**
 * Reference-matched surface values for Claude Warm Dark Mode and Google Gemini Light Mode.
 * Values only: no third-party logo, wordmark or brand font is referenced here. Dua's own logo
 * and heading assets stay the sole brand identity.
 */
internal object DuaMatch {
    // Claude Warm Dark surfaces.
    val DarkComposer = Color(0xFF21201E)
    val DarkBackground = Color(0xFF191918)
    val DarkBubble = Color(0xFF2B2A27)
    val DarkText = Color(0xFFF4F3EE)
    val DarkSubText = Color(0xFFDCD9CE)
    val DarkMuted = Color(0xFF9C9A92)
    val DarkBorder = Color(0xFF393834)
    val DarkDivider = Color(0xFF2E2D2A)

    // Google Gemini Light surfaces.
    val LightComposer = Color(0xFFFFFFFF)
    val LightBackground = Color(0xFFFFFFFF)
    val LightBubble = Color(0xFFF0F4F9)
    val LightCard = Color(0xFFF8FAFD)
    val LightTrack = Color(0xFFE8EEF5)
    val LightBorder = Color(0xFFE1E6EF)
    val LightText = Color(0xFF1F1F1F)
    val LightMuted = Color(0xFF444746)
    val LightPlaceholder = Color(0xFF5E5E5E)

    // Light composer gradient (top -> bottom) and its suggestion chip.
    val ComposerGradientTop = Color(0xFFFFFFFF)
    val ComposerGradientBottom = Color(0xFFD3E8FD)
    val SuggestionChip = Color(0xFFF0F4F9)
    val WarmCopper = Color(0xFFD97757)
}

/** Anthropic Claude Warm Dark Material 3 scheme (#191918 canvas, #21201E cards, #2B2A27 fills, #393834 borders, #D97757 accent). */
internal val DuaDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DuaPalette.ClaudeTerracotta,
    onPrimary = DuaPalette.ClaudeOnTerracotta,
    primaryContainer = DuaPalette.ClaudeTerracottaContainer,
    onPrimaryContainer = DuaPalette.ClaudeOnTerracottaContainer,
    inversePrimary = DuaPalette.GeminiBluePrimary,
    secondary = DuaMatch.DarkSubText,
    onSecondary = DuaMatch.DarkBackground,
    secondaryContainer = Color(0xFF2B2A27),
    onSecondaryContainer = DuaMatch.DarkText,
    tertiary = DuaPalette.ClaudeTerracotta,
    onTertiary = DuaPalette.ClaudeOnTerracotta,
    tertiaryContainer = DuaPalette.ClaudeTerracottaContainer,
    onTertiaryContainer = DuaPalette.ClaudeOnTerracottaContainer,
    background = DuaMatch.DarkBackground,
    onBackground = DuaMatch.DarkText,
    surface = DuaMatch.DarkBackground,
    onSurface = DuaMatch.DarkText,
    surfaceVariant = DuaMatch.DarkBorder,
    onSurfaceVariant = DuaMatch.DarkMuted,
    inverseSurface = Color(0xFFF4F3EE),
    inverseOnSurface = Color(0xFF21201E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = DuaMatch.DarkMuted,
    outlineVariant = DuaMatch.DarkDivider,
    surfaceBright = Color(0xFF393834),
    surfaceDim = Color(0xFF141413),
    surfaceContainerLowest = Color(0xFF141413),
    surfaceContainerLow = Color(0xFF21201E),
    surfaceContainer = Color(0xFF21201E),
    surfaceContainerHigh = Color(0xFF2B2A27),
    surfaceContainerHighest = Color(0xFF393834),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)

/** Google Gemini Light Material 3 scheme (#FFFFFF canvas, #F8FAFD cards, #F0F4F9 pills, #E8EEF5 tracks, #E1E6EF borders). */
internal val DuaLightColorScheme: ColorScheme = lightColorScheme(
    primary = DuaPalette.GeminiBluePrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = DuaPalette.GeminiBlueContainer,
    onPrimaryContainer = DuaPalette.GeminiOnBlueContainer,
    inversePrimary = Color(0xFFA8C7FA),
    secondary = DuaMatch.LightMuted,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = DuaMatch.LightBubble,
    onSecondaryContainer = DuaMatch.LightText,
    tertiary = DuaPalette.GeminiBluePrimary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = DuaPalette.GeminiBlueContainer,
    onTertiaryContainer = DuaPalette.GeminiOnBlueContainer,
    background = DuaMatch.LightBackground,
    onBackground = DuaMatch.LightText,
    surface = DuaMatch.LightBackground,
    onSurface = DuaMatch.LightText,
    surfaceVariant = DuaMatch.LightBorder,
    onSurfaceVariant = DuaMatch.LightMuted,
    inverseSurface = Color(0xFF21201E),
    inverseOnSurface = Color(0xFFF4F3EE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = DuaMatch.LightPlaceholder,
    outlineVariant = DuaMatch.LightBorder,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE8EEF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE8EEF5),
    surfaceContainerHighest = Color(0xFFE1E6EF),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)

/** Roles Material 3 does not name but the chat UI needs. */
@Immutable
data class DuaColors(
    val userBubble: Color,
    val onUserBubble: Color,
    val codeBackground: Color,
    val codeHeader: Color,
    val codeBorder: Color,
    val onCode: Color,
    val composer: Color,
    val composerBorder: Color,
    /** Floating glass overlay; an opaque composer base keeps text contrast independent of content below. */
    val floatingSurface: Color,
    val floatingBorder: Color,
    /** High-emphasis neutral action (send button, selected chip): warm cream on dark, ink on light. */
    val action: Color,
    val onAction: Color,
    val voiceCubeStroke: Color,
    val voiceCubeHighlight: Color,
    val diff: DuaDiffColors,
    /** Original Atom palette selected for this appearance, without runtime hue adjustment. */
    val syntaxTheme: SyntaxTheme,
    val voice: DuaVoiceColors,
    val danger: Color,
    val onDanger: Color,
    /** Media canvases stay black in both appearances; these are independent of the app surface. */
    val mediaBackground: Color,
    val mediaOverlay: Color,
    val onMedia: Color,
    /** Fixed artwork highlights and the provider sign-in badge retain their original colors. */
    val logoHighlight: Color,
    val brandBadgeBackground: Color,
    /** Small gold accent (the Home tagline sparkle). Decorative only. */
    val sparkle: Color,
    /** Selected mode chip: silver on dark, ink on light, with a gold icon. */
    val chipSelected: Color,
    val onChipSelected: Color,
    val chipSelectedIcon: Color,
    val isDark: Boolean,
)

@Immutable
data class DuaDiffColors(val added: Color, val addedBackground: Color, val removed: Color, val removedBackground: Color, val hunk: Color)

/** Decorative voice light and a deliberately dark orb capsule; no text is drawn in the glow. */
@Immutable
data class DuaVoiceColors(
    val background: Color,
    val glowSilver: Color,
    val highlight: Color,
    val statusSurface: Color,
    val speakingGlow: Color,
    val orbSurfaceTop: Color,
    val orbSurfaceBottom: Color,
    val orbSilverLow: Color,
    val orbSilverHigh: Color,
    val orbGold: Color,
    val orbCream: Color,
    val orbRim: Color,
)

// Vector paths are tint masks, not visible screen foregrounds; callers supply the themed Icon tint.
internal val DuaVectorStroke = Color.Black

/** Geometry supplies a computed shade, while the theme owns the cube's color construction. */
internal fun voiceCubeFaceColor(shade: Float, alpha: Float): Color = Color(shade, shade, shade, alpha)

/** Decode an original syntax token's RGB exactly as the pre-refactor renderer did. */
internal fun syntaxTokenColor(rgb: Int): Color = Color(rgb).copy(alpha = 1f)

internal val DuaDarkColors = DuaColors(
    userBubble = DuaMatch.DarkBubble,
    onUserBubble = DuaMatch.DarkText,
    codeBackground = Color(0xFF141413),
    codeHeader = DuaMatch.DarkComposer,
    codeBorder = DuaMatch.DarkBorder,
    onCode = DuaMatch.DarkText,
    composer = DuaMatch.DarkComposer,
    composerBorder = DuaMatch.DarkBorder,
    floatingSurface = DuaMatch.DarkComposer.copy(alpha = .94f),
    floatingBorder = DuaMatch.DarkBorder,
    action = DuaMatch.DarkText,
    onAction = DuaMatch.DarkBackground,
    voiceCubeStroke = DuaMatch.DarkSubText,
    voiceCubeHighlight = Color(.88f, .88f, .90f),
    diff = DuaDiffColors(Color(0xFF7EE787), Color(0x332EA043), Color(0xFFFF7B72), Color(0x33F85149), Color(0xFF79C0FF)),
    syntaxTheme = SyntaxThemes.atom(true),
    voice = DuaVoiceColors(
        background = Color(0xFF050506),
        glowSilver = DuaMatch.DarkSubText, highlight = Color.White,
        statusSurface = Color.White.copy(alpha = .06f), speakingGlow = DuaPalette.ClaudeTerracotta,
        orbSurfaceTop = Color(0xFF21201E), orbSurfaceBottom = Color(0xFF141413),
        orbSilverLow = DuaMatch.DarkMuted, orbSilverHigh = DuaMatch.DarkSubText,
        orbGold = DuaPalette.Gold80, orbCream = Color(0xFFF3E3B8), orbRim = Color.White.copy(alpha = .10f),
    ),
    danger = Color(0xFFE5484D), onDanger = Color.White,
    mediaBackground = Color.Black, mediaOverlay = Color.Black.copy(alpha = .55f), onMedia = Color.White,
    logoHighlight = Color.White, brandBadgeBackground = Color.White,
    sparkle = DuaPalette.Gold80,
    chipSelected = DuaMatch.DarkText,
    onChipSelected = DuaMatch.DarkBackground,
    chipSelectedIcon = DuaPalette.Gold35,
    isDark = true,
)

internal val DuaLightColors = DuaColors(
    userBubble = DuaMatch.LightBubble,
    onUserBubble = DuaMatch.LightText,
    codeBackground = DuaMatch.LightCard,
    codeHeader = DuaMatch.LightBubble,
    codeBorder = DuaMatch.LightBorder,
    onCode = DuaMatch.LightText,
    composer = DuaMatch.LightComposer,
    composerBorder = DuaMatch.LightBorder,
    floatingSurface = Color.White.copy(alpha = .94f),
    floatingBorder = DuaMatch.LightBorder,
    action = DuaMatch.LightText,
    onAction = Color.White,
    voiceCubeStroke = DuaMatch.LightPlaceholder,
    voiceCubeHighlight = Color(.88f, .88f, .90f),
    diff = DuaDiffColors(Color(0xFF116329), Color(0x262DA44E), Color(0xFF82071E), Color(0x26FF8182), Color(0xFF0550AE)),
    syntaxTheme = SyntaxThemes.atom(false),
    voice = DuaVoiceColors(
        background = DuaMatch.LightCard,
        glowSilver = Color(0xFFB4B7BE), highlight = Color.White,
        statusSurface = DuaMatch.LightText.copy(alpha = .05f), speakingGlow = DuaPalette.GeminiBluePrimary,
        orbSurfaceTop = Color(0xFF141518), orbSurfaceBottom = Color(0xFF0B0B0D),
        orbSilverLow = Color(0xFFB4B7BE), orbSilverHigh = Color(0xFFDDDEE2),
        orbGold = DuaPalette.Gold80, orbCream = Color(0xFFF3E3B8), orbRim = Color.Black.copy(alpha = .12f),
    ),
    danger = Color(0xFFE5484D), onDanger = Color.White,
    mediaBackground = Color.Black, mediaOverlay = Color.Black.copy(alpha = .55f), onMedia = Color.White,
    logoHighlight = Color.White, brandBadgeBackground = Color.White,
    sparkle = DuaPalette.Gold40,
    chipSelected = DuaMatch.LightText,
    onChipSelected = DuaMatch.LightCard,
    chipSelectedIcon = DuaPalette.Gold80,
    isDark = false,
)

/** Projects reference palette; screen code never embeds colors. */
internal data class ProjectPalette(val background: Color, val surface: Color, val input: Color, val selected: Color,
    val text: Color, val subText: Color, val date: Color, val border: Color, val danger: Color,
    val purple: Color, val purpleBg: Color, val coral: Color, val coralBg: Color,
    val blue: Color, val blueBg: Color, val teal: Color, val tealBg: Color,
    val blackButton: Color, val onBlackButton: Color, val attachmentIconBackground: Color, val voice: Color, val onVoice: Color)
internal val ProjectPaletteLight = ProjectPalette(Color.White, Color.White, Color(0xFFF0F4F9), Color(0xFFE8EEF5),
    Color(0xFF1F1F1F), Color(0xFF444746), Color(0xFF5E5E5E), Color(0xFFE1E6EF), Color(0xFFC62828),
    Color(0xFF8B5CF6), Color(0xFFF2EAFF), Color(0xFFEF4444), Color(0xFFFDECEC),
    Color(0xFF0B57D0), Color(0xFFD3E8FD), Color(0xFF10B981), Color(0xFFE6F8F3), Color(0xFF1F1F1F), Color.White, Color(0xFFE8EEF5), Color(0xFFD3E8FD), Color(0xFF041E49))
internal val ProjectPaletteDark = ProjectPalette(Color(0xFF191918), Color(0xFF21201E), Color(0xFF2B2A27), Color(0xFF393834),
    Color(0xFFF4F3EE), Color(0xFFDCD9CE), Color(0xFF9C9A92), Color(0xFF393834), Color(0xFFFF8F91),
    Color(0xFFC4ACFF), Color(0xFF302443), Color(0xFFFFA6A6), Color(0xFF43262A),
    Color(0xFF9FC4FF), Color(0xFF20324C), Color(0xFF75D8B4), Color(0xFF193D35), Color(0xFFF4F3EE), Color(0xFF191918), Color(0xFF393834), Color(0xFFD97757), Color(0xFF191918))

internal val ProjectHomeworkBlueLight = Color(0xFF0B57D0)

/**
 * Feature palettes mapped from the shared Gemini Light & Claude Warm Dark schemes.
 */
internal fun homeColors(s: ColorScheme, d: DuaColors) = if (d.isDark) {
    HomeColors(
        roundButton = Color(0xFF21201E), roundButtonIcon = Color(0xFFF4F3EE),
        segmentTrack = Color(0xFF21201E), segmentSelected = Color(0xFF393834),
        segmentSelectedText = Color(0xFFF4F3EE), segmentText = Color(0xFFDCD9CE),
        segmentFloatSelected = Color(0xE621201E),
        promptIcon = Color(0xFFDCD9CE), promptText = Color(0xFFF4F3EE),
        promptRow = Color(0x26FFFFFF),
        tagline = Color(0xFFDCD9CE),
        bar = Color(0xFF21201E), barBorder = Color(0xFF393834), barIcon = Color(0xFFDCD9CE),
        input = Color(0xFFF4F3EE), placeholder = Color(0xFF9C9A92),
        voice = Color(0xFFD97757), onVoice = Color(0xFF191918), modelLabel = Color(0xFF9C9A92),
    )
} else {
    HomeColors(
        roundButton = Color(0xFFF0F4F9), roundButtonIcon = Color(0xFF1F1F1F),
        segmentTrack = Color(0xFFE8EEF5), segmentSelected = Color(0xFFFFFFFF),
        segmentSelectedText = Color(0xFF1F1F1F), segmentText = Color(0xFF444746),
        segmentFloatSelected = Color(0xE6FFFFFF),
        promptIcon = Color(0xFF444746), promptText = Color(0xFF1F1F1F),
        promptRow = Color(0xB3FFFFFF),
        tagline = Color(0xFF444746),
        bar = Color(0xFFFFFFFF), barBorder = Color(0xFFE1E6EF), barIcon = Color(0xFF1F1F1F),
        input = Color(0xFF1F1F1F), placeholder = Color(0xFF5E5E5E),
        voice = Color(0xFFD3E8FD), onVoice = Color(0xFF041E49), modelLabel = Color(0xFF444746),
    )
}

internal fun chatColors(s: ColorScheme, d: DuaColors) = if (d.isDark) {
    ChatColors(
        background = Color(0xFF191918), userBubble = Color(0xFF2B2A27), onUserBubble = Color(0xFFF4F3EE),
        assistantBubble = Color.Transparent, assistantText = Color(0xFFF4F3EE),
        chipFill = Color(0xFF21201E), chipBorder = Color(0xFF393834),
        chipText = Color(0xFFDCD9CE), chipIcon = Color(0xFF9C9A92),
        text = Color(0xFFF4F3EE), mutedText = Color(0xFF9C9A92),
        cardFill = Color(0xFF21201E), divider = Color(0xFF2E2D2A),
        mediaFill = Color(0xFF21201E), shimmer = Color(0xFF2E2D2A),
        accent = s.primary, accentSoft = s.primaryContainer, onAccentSoft = s.onPrimaryContainer,
        errorFill = s.errorContainer, onErrorFill = s.onErrorContainer, errorIcon = s.error,
    )
} else {
    ChatColors(
        background = Color(0xFFFFFFFF), userBubble = Color(0xFFF0F4F9), onUserBubble = Color(0xFF1F1F1F),
        assistantBubble = Color.Transparent, assistantText = Color(0xFF1F1F1F),
        chipFill = Color(0xFFF0F4F9), chipBorder = Color(0xFFE1E6EF),
        chipText = Color(0xFF1F1F1F), chipIcon = Color(0xFF444746),
        text = Color(0xFF1F1F1F), mutedText = Color(0xFF444746),
        cardFill = Color(0xFFF8FAFD), divider = Color(0xFFE1E6EF),
        mediaFill = Color(0xFFF0F4F9), shimmer = Color(0xFFE1E6EF),
        accent = s.primary, accentSoft = s.primaryContainer, onAccentSoft = s.onPrimaryContainer,
        errorFill = s.errorContainer, onErrorFill = s.onErrorContainer, errorIcon = s.error,
    )
}

internal fun drawerColors(s: ColorScheme, d: DuaColors) = DrawerColors(
    container = s.surfaceContainerLow, title = s.onSurface,
    searchCircle = s.surfaceContainerHigh, searchIcon = s.onSurfaceVariant,
    menuText = s.onSurface, chevron = s.onSurfaceVariant, divider = d.floatingBorder,
    chatText = s.onSurface, subText = s.onSurfaceVariant,
    newChat = s.primaryContainer, onNewChat = s.onPrimaryContainer,
    avatar = s.primaryContainer, onAvatar = s.onPrimaryContainer,
    gradientTop = s.surfaceContainerHigh, searchShadow = s.scrim.copy(alpha = .12f),
    avatarRing = s.surfaceContainerHighest,
)

internal fun sheetColors(s: ColorScheme, d: DuaColors) = SheetColors(
    container = s.surfaceContainerLow, handle = s.outline, title = s.onSurface,
    text = s.onSurface, subText = s.onSurfaceVariant, icon = s.onSurfaceVariant,
    fill = s.surfaceContainerHigh, selectedFill = s.primaryContainer,
    divider = d.floatingBorder, accent = s.primary, onAccent = s.onPrimary,
    switchTrackOff = s.surfaceContainerHighest, switchThumbOff = s.onSurfaceVariant,
    destructive = s.error, scrim = s.scrim.copy(alpha = .48f),
)

internal object DuaFixedColors {
    val SuppliedBg = Color(0xFF191918)
    val SuppliedSurface = Color(0xFF21201E)
    val SuppliedBubble = Color(0xFF2B2A27)
    val SuppliedText = Color(0xFFF4F3EE)
    val SuppliedPrimary = Color(0xFFD97757)
    val SuppliedOnPrimary = Color(0xFF191918)
    val SuppliedGlowBlue = Color(0xFF4285F4)
    val SuppliedGlowPurple = Color(0xFF9B72CB)
    val SuppliedGlowRose = Color(0xFFD96570)
    val SwipeDanger = Color(0xFFDD4B39)
    val AuroraBlue = Color(0xFF3B82F6)
    val AuroraViolet = Color(0xFF8B5CF6)
    val AuroraPink = Color(0xFFEC4899)
    val AuroraOrange = Color(0xFFF97316)
}

internal fun settingsColors(s: ColorScheme, d: DuaColors) = SettingsColors(
    background = s.background, circleButton = s.surfaceContainerHigh,
    circleBorder = d.floatingBorder, circleIcon = s.onSurfaceVariant,
    avatar = s.primaryContainer, onAvatar = s.onPrimaryContainer,
    editBadge = s.surfaceContainerHigh, editIcon = s.onSurface,
    name = s.onSurface, card = s.surfaceContainerLow, cardBorder = d.floatingBorder,
    rowIcon = s.onSurfaceVariant, rowText = s.onSurface, value = s.onSurfaceVariant,
    accentValue = s.primary, arrow = s.onSurfaceVariant, chevron = s.onSurfaceVariant,
    divider = d.floatingBorder, upgrade = s.primary, danger = s.error,
)

internal fun pageColors(s: ColorScheme, d: DuaColors) = PageColors(
    background = s.background, card = s.surfaceContainerLow, cardBorder = d.floatingBorder,
    fill = s.surfaceContainerHigh, text = s.onSurface, subText = s.onSurfaceVariant,
    divider = d.floatingBorder, accent = s.primary, onAccent = s.onPrimary,
    accentContainer = s.primaryContainer, onAccentContainer = s.onPrimaryContainer,
    danger = s.error, outline = s.outline,
)

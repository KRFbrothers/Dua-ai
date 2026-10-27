package com.dua.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.snipme.highlights.model.SyntaxTheme
import dev.snipme.highlights.model.SyntaxThemes

/** Reference Agent task surface, with a readable dark counterpart. */
internal val AgentTaskSurfaceLight = Color(0xFFF7F7F8)
internal val AgentTaskSurfaceDark = Color(0xFF1A1B1F)

/**
 * Raw palette. Screens never use these directly: they read [ColorScheme] roles from
 * MaterialTheme or the extra roles in [DuaColors] via `DuaTheme.colors`.
 *
 * The palette is a cool neutral ramp with one restrained teal accent. Text roles meet
 * WCAG AA against every surface they are placed on (body text ≥ 4.5:1).
 */
internal object DuaPalette {
    // Neutrals (cool grey), dark → light.
    val Neutral4 = Color(0xFF0E0F11)
    val Neutral8 = Color(0xFF151619)
    val Neutral10 = Color(0xFF1A1B1F)
    val Neutral13 = Color(0xFF212226)
    val Neutral22 = Color(0xFF36383E)
    val Neutral45 = Color(0xFF6E717A)
    val Neutral72 = Color(0xFFB4B7BE)
    val Neutral88 = Color(0xFFDDDEE2)
    val Neutral90 = Color(0xFFE3E4E8)
    val Neutral93 = Color(0xFFECECEF)
    val Neutral95 = Color(0xFFF1F1F3)
    val Neutral97 = Color(0xFFF6F6F7)
    val Neutral100 = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111214)

    // Gold: the Home tagline sparkle and the icon of a selected mode chip.
    val Gold80 = Color(0xFFE8C46A)
    val Gold40 = Color(0xFFA67C1E)
    val Gold35 = Color(0xFF8A6514)
}

/**
 * Reference-matched surface values, read pixel-by-pixel from the supplied reference screenshots.
 * Kept separate from [DuaPalette] so the original neutral ramp stays intact for every role that
 * was not part of the match.
 *
 * Values only: no third-party logo, wordmark or brand font is referenced here. Dua's own logo
 * and heading assets stay the sole brand identity.
 */
/** Voice orb surface is the same in light and dark themes. */
private val OrbSurfaceTop = Color(0xFF141518)
private val OrbSurfaceBottom = Color(0xFF0B0B0D)

internal object DuaMatch {
    // Dark surfaces.
    val DarkComposer = Color(0xFF20201E)
    val DarkBackground = Color(0xFF151515)
    val DarkBubble = Color(0xFF313131)
    val DarkText = Color(0xFFFAFAF8)
    val DarkMuted = Color(0xFFB4B4B4)
    // Light surfaces.
    val LightComposer = Color(0xFFFFFFFF)
    val LightBackground = Color(0xFFF9F9F9)
    val LightText = Color(0xFF1F1F1F)
    val LightMuted = Color(0xFF444746)
}

/** Material 3 tonal-spot scheme generated from seed #0B7366 (material-color-utilities 0.3.0). */
internal val DuaDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF83D5C6),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF9FF2E2),
    inversePrimary = Color(0xFF006B5E),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCDE8E1),
    tertiary = Color(0xFFACCAE5),
    onTertiary = Color(0xFF133348),
    tertiaryContainer = Color(0xFF2C4A60),
    onTertiaryContainer = Color(0xFFCBE6FF),
    background = DuaMatch.DarkBackground,
    onBackground = DuaMatch.DarkText,
    surface = DuaMatch.DarkBackground,
    onSurface = DuaMatch.DarkText,
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = DuaMatch.DarkMuted,
    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
    surfaceBright = Color(0xFF343B39),
    surfaceDim = Color(0xFF0E1513),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303634),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)

/** Material 3 tonal-spot scheme generated from seed #0B7366 (material-color-utilities 0.3.0). */
internal val DuaLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF006B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9FF2E2),
    onPrimaryContainer = Color(0xFF005047),
    inversePrimary = Color(0xFF83D5C6),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E1),
    onSecondaryContainer = Color(0xFF334B46),
    tertiary = Color(0xFF446179),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBE6FF),
    onTertiaryContainer = Color(0xFF2C4A60),
    background = DuaMatch.LightBackground,
    onBackground = DuaMatch.LightText,
    surface = DuaMatch.LightBackground,
    onSurface = DuaMatch.LightText,
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = DuaMatch.LightMuted,
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
    surfaceBright = Color(0xFFF4FBF8),
    surfaceDim = Color(0xFFD5DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
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
    /** High-emphasis neutral action (send button, selected chip): white on dark, ink on light. */
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
    onUserBubble = DuaPalette.Neutral93,
    codeBackground = DuaPalette.Neutral8,
    codeHeader = DuaPalette.Neutral13,
    codeBorder = DuaPalette.Neutral22,
    onCode = DuaPalette.Neutral90,
    composer = DuaMatch.DarkComposer,
    composerBorder = DuaPalette.Neutral22,
    floatingSurface = DuaPalette.Neutral13.copy(alpha = .94f),
    floatingBorder = DuaPalette.Neutral22.copy(alpha = .35f),
    action = DuaPalette.Neutral93,
    onAction = DuaPalette.Neutral4,
    voiceCubeStroke = DuaPalette.Neutral72,
    voiceCubeHighlight = Color(.88f, .88f, .90f),
    diff = DuaDiffColors(Color(0xFF7EE787), Color(0x332EA043), Color(0xFFFF7B72), Color(0x33F85149), Color(0xFF79C0FF)),
    syntaxTheme = SyntaxThemes.atom(true),
    voice = DuaVoiceColors(
        background = Color(0xFF050506),
        glowSilver = DuaPalette.Neutral88, highlight = DuaPalette.Neutral100,
        statusSurface = DuaPalette.Neutral100.copy(alpha = .06f), speakingGlow = Color(0xFF8B7CF6),
        orbSurfaceTop = OrbSurfaceTop, orbSurfaceBottom = OrbSurfaceBottom,
        orbSilverLow = DuaPalette.Neutral72, orbSilverHigh = DuaPalette.Neutral88,
        orbGold = DuaPalette.Gold80, orbCream = Color(0xFFF3E3B8), orbRim = Color.White.copy(alpha = .10f),
    ),
    danger = Color(0xFFE5484D), onDanger = DuaPalette.Neutral100,
    mediaBackground = Color.Black, mediaOverlay = Color.Black.copy(alpha = .55f), onMedia = DuaPalette.Neutral100,
    logoHighlight = DuaPalette.Neutral100, brandBadgeBackground = DuaPalette.Neutral100,
    sparkle = DuaPalette.Gold80,
    chipSelected = DuaPalette.Neutral90,
    onChipSelected = DuaPalette.Neutral4,
    chipSelectedIcon = DuaPalette.Gold35,
    isDark = true,
)

internal val DuaLightColors = DuaColors(
    userBubble = DuaPalette.Neutral95,
    onUserBubble = DuaPalette.Ink,
    codeBackground = DuaPalette.Neutral97,
    codeHeader = DuaPalette.Neutral93,
    codeBorder = DuaPalette.Neutral90,
    onCode = DuaPalette.Ink,
    composer = DuaMatch.LightComposer,
    composerBorder = DuaPalette.Neutral90,
    floatingSurface = DuaPalette.Neutral100.copy(alpha = .94f),
    floatingBorder = DuaPalette.Neutral90.copy(alpha = .35f),
    action = DuaPalette.Ink,
    onAction = DuaPalette.Neutral100,
    voiceCubeStroke = DuaPalette.Neutral45,
    voiceCubeHighlight = Color(.88f, .88f, .90f),
    diff = DuaDiffColors(Color(0xFF116329), Color(0x262DA44E), Color(0xFF82071E), Color(0x26FF8182), Color(0xFF0550AE)),
    syntaxTheme = SyntaxThemes.atom(false),
    voice = DuaVoiceColors(
        background = DuaPalette.Neutral97,
        glowSilver = DuaPalette.Neutral72, highlight = DuaPalette.Neutral100,
        statusSurface = DuaPalette.Ink.copy(alpha = .05f), speakingGlow = Color(0xFF8B7CF6),
        orbSurfaceTop = OrbSurfaceTop, orbSurfaceBottom = OrbSurfaceBottom,
        orbSilverLow = DuaPalette.Neutral72, orbSilverHigh = DuaPalette.Neutral88,
        orbGold = DuaPalette.Gold80, orbCream = Color(0xFFF3E3B8), orbRim = Color.Black.copy(alpha = .12f),
    ),
    danger = Color(0xFFE5484D), onDanger = DuaPalette.Neutral100,
    mediaBackground = Color.Black, mediaOverlay = Color.Black.copy(alpha = .55f), onMedia = DuaPalette.Neutral100,
    logoHighlight = DuaPalette.Neutral100, brandBadgeBackground = DuaPalette.Neutral100,
    sparkle = DuaPalette.Gold40,
    chipSelected = DuaPalette.Neutral10,
    onChipSelected = DuaPalette.Neutral97,
    chipSelectedIcon = DuaPalette.Gold80,
    isDark = false,
)

/** Projects reference palette; screen code never embeds colors. */
internal data class ProjectPalette(val background: Color, val surface: Color, val input: Color, val selected: Color,
    val text: Color, val subText: Color, val date: Color, val border: Color, val danger: Color,
    val purple: Color, val purpleBg: Color, val coral: Color, val coralBg: Color,
    val blue: Color, val blueBg: Color, val teal: Color, val tealBg: Color,
    val blackButton: Color, val onBlackButton: Color, val attachmentIconBackground: Color, val voice: Color, val onVoice: Color)
internal val ProjectPaletteLight = ProjectPalette(Color.White, Color.White, Color(0xFFF2F2F6), Color(0xFFE5E5EA),
    Color(0xFF0D0D0D), Color(0xFF6E6E80), Color(0xFF8E8E93), Color(0xFFE5E5EA), Color(0xFFC62828),
    Color(0xFF8B5CF6), Color(0xFFF2EAFF), Color(0xFFEF4444), Color(0xFFFDECEC),
    Color(0xFF3B82F6), Color(0xFFEBF3FF), Color(0xFF10B981), Color(0xFFE6F8F3), Color(0xFF0D0D0D), Color.White, Color(0xFFEBEBEF), Color(0xFF8B5CF6), Color.White)
internal val ProjectPaletteDark = ProjectPalette(Color(0xFF101114), Color(0xFF202127), Color(0xFF292A32), Color(0xFF34353F),
    Color(0xFFF5F5F7), Color(0xFFB9BAC7), Color(0xFFA6A7B5), Color(0xFF42434D), Color(0xFFFF8F91),
    Color(0xFFC4ACFF), Color(0xFF302443), Color(0xFFFFA6A6), Color(0xFF43262A),
    Color(0xFF9FC4FF), Color(0xFF20324C), Color(0xFF75D8B4), Color(0xFF193D35), Color(0xFFF5F5F7), Color(0xFF101114), Color(0xFF34353F), Color(0xFF8B5CF6), Color.White)

internal val ProjectHomeworkBlueLight = Color(0xFF2563EB)


/** Feature palettes share the same semantic canvas, contrast and restrained accent. */
internal fun homeColors(s: ColorScheme, d: DuaColors) = HomeColors(
    roundButton = s.surfaceContainer, roundButtonIcon = s.onSurface,
    segmentTrack = s.surfaceContainer, segmentSelected = s.surfaceContainerHighest,
    segmentSelectedText = s.onSurface, segmentText = s.onSurfaceVariant,
    segmentFloatSelected = s.surfaceContainerHighest.copy(alpha = .96f),
    promptIcon = s.onSurfaceVariant, promptText = s.onSurface,
    promptRow = s.surfaceContainerLow.copy(alpha = .96f),
    tagline = if (d.isDark) s.secondary else s.onSurfaceVariant,
    bar = d.composer, barBorder = d.floatingBorder, barIcon = s.onSurfaceVariant,
    input = s.onSurface, placeholder = s.onSurfaceVariant,
    voice = s.primary, onVoice = s.onPrimary, modelLabel = s.onSurfaceVariant,
)

internal fun chatColors(s: ColorScheme, d: DuaColors) = ChatColors(
    background = s.background, userBubble = d.userBubble, onUserBubble = d.onUserBubble,
    assistantBubble = Color.Transparent, assistantText = s.onSurface,
    chipFill = s.surfaceContainer, chipBorder = d.floatingBorder,
    chipText = s.onSurface, chipIcon = s.onSurfaceVariant,
    text = s.onSurface, mutedText = s.onSurfaceVariant,
    cardFill = s.surfaceContainerLow, divider = s.outlineVariant,
    mediaFill = s.surfaceContainer, shimmer = s.surfaceContainerHighest,
    accent = s.primary, accentSoft = s.primaryContainer, onAccentSoft = s.onPrimaryContainer,
    errorFill = s.errorContainer, onErrorFill = s.onErrorContainer, errorIcon = s.error,
)

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

/**
 * Fixed (theme-independent) colours that were previously hardcoded hex in screens.
 * Same value in light and dark, exactly as before.
 */
internal object DuaFixedColors {
    // Swipe-to-delete action.
    val SwipeDanger = Color(0xFFDD4B39)
    // Composer aurora ring.
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

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
    val Neutral0 = Color(0xFF09090B)
    val Neutral4 = Color(0xFF0E0F11)
    val Neutral8 = Color(0xFF151619)
    val Neutral10 = Color(0xFF1A1B1F)
    val Neutral13 = Color(0xFF212226)
    val Neutral16 = Color(0xFF292A2F)
    val Neutral22 = Color(0xFF36383E)
    val Neutral30 = Color(0xFF4A4D54)
    val Neutral45 = Color(0xFF6E717A)
    val Neutral62 = Color(0xFF9EA1A9)
    val Neutral72 = Color(0xFFB4B7BE)
    val Neutral88 = Color(0xFFDDDEE2)
    val Neutral90 = Color(0xFFE3E4E8)
    val Neutral93 = Color(0xFFECECEF)
    val Neutral95 = Color(0xFFF1F1F3)
    val Neutral97 = Color(0xFFF6F6F7)
    val Neutral100 = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111214)
    val InkMuted = Color(0xFF575A61)

    // Gold: the Home tagline sparkle and the icon of a selected mode chip.
    val Gold80 = Color(0xFFE8C46A)
    val Gold40 = Color(0xFFA67C1E)
    val Gold35 = Color(0xFF8A6514)

    // Accent: one restrained teal, used for links, focus, selection and switches only.
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

internal val DuaDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DuaPalette.Teal80,
    onPrimary = DuaPalette.Teal20,
    primaryContainer = DuaPalette.Teal30,
    onPrimaryContainer = DuaPalette.Teal90,
    inversePrimary = DuaPalette.Teal40,
    secondary = DuaPalette.Neutral72,
    onSecondary = DuaPalette.Neutral10,
    secondaryContainer = DuaPalette.Neutral16,
    onSecondaryContainer = DuaPalette.Neutral93,
    tertiary = DuaPalette.Teal80,
    onTertiary = DuaPalette.Teal20,
    tertiaryContainer = DuaPalette.Teal30,
    onTertiaryContainer = DuaPalette.Teal90,
    background = DuaPalette.Neutral4,
    onBackground = DuaPalette.Neutral93,
    surface = DuaPalette.Neutral4,
    onSurface = DuaPalette.Neutral93,
    surfaceVariant = DuaPalette.Neutral16,
    onSurfaceVariant = DuaPalette.Neutral62,
    surfaceTint = Color.Transparent,
    inverseSurface = DuaPalette.Neutral93,
    inverseOnSurface = DuaPalette.Neutral10,
    error = DuaPalette.Red80,
    onError = DuaPalette.Red20,
    errorContainer = DuaPalette.Red30,
    onErrorContainer = DuaPalette.Red90,
    outline = DuaPalette.Neutral30,
    outlineVariant = DuaPalette.Neutral22,
    scrim = Color.Black,
    surfaceBright = DuaPalette.Neutral22,
    surfaceDim = DuaPalette.Neutral0,
    surfaceContainerLowest = DuaPalette.Neutral0,
    surfaceContainerLow = DuaPalette.Neutral8,
    surfaceContainer = DuaPalette.Neutral10,
    surfaceContainerHigh = DuaPalette.Neutral13,
    surfaceContainerHighest = DuaPalette.Neutral16,
)

internal val DuaLightColorScheme: ColorScheme = lightColorScheme(
    primary = DuaPalette.Teal40,
    onPrimary = DuaPalette.Neutral100,
    primaryContainer = DuaPalette.Teal95,
    onPrimaryContainer = DuaPalette.Teal10,
    inversePrimary = DuaPalette.Teal80,
    secondary = DuaPalette.InkMuted,
    onSecondary = DuaPalette.Neutral100,
    secondaryContainer = DuaPalette.Neutral93,
    onSecondaryContainer = DuaPalette.Ink,
    tertiary = DuaPalette.Teal40,
    onTertiary = DuaPalette.Neutral100,
    tertiaryContainer = DuaPalette.Teal95,
    onTertiaryContainer = DuaPalette.Teal10,
    background = DuaPalette.Neutral100,
    onBackground = DuaPalette.Ink,
    surface = DuaPalette.Neutral100,
    onSurface = DuaPalette.Ink,
    surfaceVariant = DuaPalette.Neutral95,
    onSurfaceVariant = DuaPalette.InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = DuaPalette.Neutral13,
    inverseOnSurface = DuaPalette.Neutral93,
    error = DuaPalette.Red40,
    onError = DuaPalette.Neutral100,
    errorContainer = DuaPalette.Red90,
    onErrorContainer = DuaPalette.Red10,
    outline = DuaPalette.Neutral72,
    outlineVariant = DuaPalette.Neutral90,
    scrim = Color.Black,
    surfaceBright = DuaPalette.Neutral100,
    surfaceDim = DuaPalette.Neutral88,
    surfaceContainerLowest = DuaPalette.Neutral100,
    surfaceContainerLow = DuaPalette.Neutral97,
    surfaceContainer = DuaPalette.Neutral95,
    surfaceContainerHigh = DuaPalette.Neutral93,
    surfaceContainerHighest = DuaPalette.Neutral90,
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
    /** Fixed artwork highlights and the Google badge retain their original colors. */
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
    userBubble = DuaPalette.Neutral16,
    onUserBubble = DuaPalette.Neutral93,
    codeBackground = DuaPalette.Neutral8,
    codeHeader = DuaPalette.Neutral13,
    codeBorder = DuaPalette.Neutral22,
    onCode = DuaPalette.Neutral90,
    composer = DuaPalette.Neutral10,
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
        orbSurfaceTop = Color(0xFF141518), orbSurfaceBottom = Color(0xFF0B0B0D),
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
    composer = DuaPalette.Neutral97,
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
        orbSurfaceTop = Color(0xFF141518), orbSurfaceBottom = Color(0xFF0B0B0D),
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
    // Supplied (Gemini-style) chat surface.
    val SuppliedBg = Color(0xFF131314)
    val SuppliedSurface = Color(0xFF1E1F20)
    val SuppliedBubble = Color(0xFF282A2C)
    val SuppliedText = Color(0xFFE3E3E3)
    val SuppliedPrimary = Color(0xFFA8C7FA)
    val SuppliedOnPrimary = Color(0xFF062E6F)
    val SuppliedGlowBlue = Color(0xFF4285F4)
    val SuppliedGlowPurple = Color(0xFF9B72CB)
    val SuppliedGlowRose = Color(0xFFD96570)
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

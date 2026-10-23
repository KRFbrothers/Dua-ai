package com.dua.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.AccentPreset

/** Semantic chat/diff, voice, danger, media and branding roles supplied for the active appearance. */
val LocalDuaColors = staticCompositionLocalOf { DuaDarkColors }

/** Resolves the saved "Color mode" preference (dark, light or system). System follows the device; legacy installations retain their saved choice. */
@Composable
@ReadOnlyComposable
fun isDarkTheme(preferences: LocalPreferences): Boolean =
    preferences.theme == "dark" || preferences.theme != "light" && isSystemInDarkTheme()

/**
 * App theme. The app root passes the DataStore appearance alongside the existing font
 * preference and optional accent override. Provides Material 3 color/type/shape tokens plus
 * the extra [DuaColors] roles.
 */
@Composable
fun DuaTheme(
    preferences: LocalPreferences = LocalPreferences(),
    darkTheme: Boolean = isDarkTheme(preferences),
    accent: AccentPreset? = null,
    updateSystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val typography = remember(preferences.font) { duaTypography(fontFamilyFor(preferences.font)) }
    val preset = accent ?: AccentPreset.defaultFor(darkTheme)
    val scheme = remember(darkTheme, preset) { accentedScheme(if (darkTheme) DuaDarkColorScheme else DuaLightColorScheme, preset, darkTheme) }
    val extra = if (darkTheme) DuaDarkColors else DuaLightColors
    val view = LocalView.current
    if (updateSystemBars && !view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalDuaColors provides extra, LocalDuaAccent provides accentColors(preset, darkTheme)) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = DuaShapes) {
            // Pages draw their own backgrounds; give their bare Text/Icon the theme foreground.
            // Material surfaces and buttons still override this with their own content colors.
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}

/** Accessors for Dua-specific tokens, mirroring `MaterialTheme.colorScheme`. */
object DuaTheme {
    val accent: DuaAccent
        @Composable @ReadOnlyComposable get() = LocalDuaAccent.current
    val colors: DuaColors
        @Composable @ReadOnlyComposable get() = LocalDuaColors.current
    /** Short semantic names mapped 1:1 onto the active Material colour scheme (light/dark follow it). */
    val semantic: DuaSemanticColors
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.let { c ->
            DuaSemanticColors(
                bg = c.background, surface = c.surface, border = c.outline, primary = c.primary,
                highlight = c.primaryContainer, text = c.onSurface, muted = c.onSurfaceVariant, danger = c.error,
            )
        }
}

/** Semantic colour layer: bg, surface, border, primary, highlight, text, muted, danger. */
@Immutable
data class DuaSemanticColors(
    val bg: Color, val surface: Color, val border: Color, val primary: Color,
    val highlight: Color, val text: Color, val muted: Color, val danger: Color,
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

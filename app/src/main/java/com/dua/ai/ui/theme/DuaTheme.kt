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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.AccentPreset

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
}

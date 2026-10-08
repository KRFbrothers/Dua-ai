package com.dua.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dua.ai.settings.LocalPreferences

val LocalDuaColors = staticCompositionLocalOf { DuaDarkColors }

/** Resolves the saved "Color mode" preference (dark, light or system). Dark is the default. */
@Composable
@ReadOnlyComposable
fun isDarkTheme(preferences: LocalPreferences): Boolean =
    preferences.theme == "dark" || preferences.theme != "light" && isSystemInDarkTheme()

/**
 * App theme. Reads the user's appearance settings (color mode and font style from
 * [LocalPreferences]) and provides Material 3 color, type and shape tokens plus
 * the extra [DuaColors] roles.
 */
@Composable
fun DuaTheme(
    preferences: LocalPreferences = LocalPreferences(),
    darkTheme: Boolean = isDarkTheme(preferences),
    content: @Composable () -> Unit,
) {
    val typography = remember(preferences.font) { duaTypography(fontFamilyFor(preferences.font)) }
    val scheme = if (darkTheme) DuaDarkColorScheme else DuaLightColorScheme
    val extra = if (darkTheme) DuaDarkColors else DuaLightColors
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalDuaColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = DuaShapes, content = content)
    }
}

/** Accessors for Dua-specific tokens, mirroring `MaterialTheme.colorScheme`. */
object DuaTheme {
    val colors: DuaColors
        @Composable @ReadOnlyComposable get() = LocalDuaColors.current
}

package com.dua.ai.settings

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

@Composable
fun LocalAppearance(value: LocalPreferences, content: @Composable () -> Unit) {
    val dark = value.theme == "dark" || value.theme == "system" && isSystemInDarkTheme()
    val family = when (value.font) { "serif" -> FontFamily.Serif; "monospace" -> FontFamily.Monospace; "system" -> FontFamily.Default; else -> FontFamily.SansSerif }
    val base = Typography()
    val typography = Typography(displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family), displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family), headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family), titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family), bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family), labelSmall = base.labelSmall.copy(fontFamily = family))
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
        background = Color(0xFF141218), surface = Color(0xFF211F26), surfaceContainerHighest = Color(0xFF36343B), onSurface = Color(0xFFE6E0E9), onBackground = Color(0xFFE6E0E9), onSurfaceVariant = Color(0xFFCAC4D0), outline = Color(0xFF938F99))
    else lightColorScheme(primary = Color(0xFF6750A4), onPrimary = Color.White, secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
        background = Color(0xFFFEF7FF), surface = Color(0xFFF3EDF7), surfaceContainerHighest = Color(0xFFE6E0E9), onSurface = Color(0xFF1D1B20), onBackground = Color(0xFF1D1B20), onSurfaceVariant = Color(0xFF49454F), outline = Color(0xFF79747E))
    val view = LocalView.current
    SideEffect { (view.context as? Activity)?.window?.let { window -> WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

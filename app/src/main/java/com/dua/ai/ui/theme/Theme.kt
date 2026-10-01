package com.dua.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class ExtendedThemeColors(
    val userBubbleBg: Color,
    val userBubbleBorder: Color,
    val botBubbleBg: Color,
    val codeBlockBg: Color,
    val codeBlockBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderSubtle: Color,
    val isDark: Boolean
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedThemeColors(
        userBubbleBg = DarkUserBubble,
        userBubbleBorder = DarkUserBubbleBorder,
        botBubbleBg = DarkBotBubble,
        codeBlockBg = DarkCodeBlockBg,
        codeBlockBorder = DarkCodeBlockBorder,
        textPrimary = DarkTextPrimary,
        textSecondary = DarkTextSecondary,
        textTertiary = DarkTextTertiary,
        borderSubtle = DarkBorderSubtle,
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = GeminiBlueDark,
    onPrimary = Color.White,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = DarkTextPrimary,
    secondary = GeminiSparkle,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle
)

private val LightColorScheme = lightColorScheme(
    primary = GeminiBlue,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = LightTextPrimary,
    secondary = GeminiPurple,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle
)

@Composable
fun DuaAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        ExtendedThemeColors(
            userBubbleBg = DarkUserBubble,
            userBubbleBorder = DarkUserBubbleBorder,
            botBubbleBg = DarkBotBubble,
            codeBlockBg = DarkCodeBlockBg,
            codeBlockBorder = DarkCodeBlockBorder,
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            textTertiary = DarkTextTertiary,
            borderSubtle = DarkBorderSubtle,
            isDark = true
        )
    } else {
        ExtendedThemeColors(
            userBubbleBg = LightUserBubble,
            userBubbleBorder = LightUserBubbleBorder,
            botBubbleBg = LightBotBubble,
            codeBlockBg = LightCodeBlockBg,
            codeBlockBorder = LightCodeBlockBorder,
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textTertiary = LightTextTertiary,
            borderSubtle = LightBorderSubtle,
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DuaTypography,
            content = content
        )
    }
}

package com.dua.ai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

data class WelcomeColors(val background: Color, val text: Color, val border: Color, val action: Color, val onAction: Color, val email: Color)
val WelcomeLightColors = WelcomeColors(Color(0xFFFFFFFF), Color(0xFF0D0D0D), Color(0xFFD9D9E3), Color(0xFF0D0D0D), Color.White, Color(0xFFBDBDBD))
val WelcomeDarkColors = WelcomeColors(Color(0xFF0D0D0D), Color(0xFFF9FAFB), Color(0xFF444450), Color(0xFFF9FAFB), Color(0xFF0D0D0D), Color(0xFF5E5E68))
object WelcomeTheme {
    val colors: WelcomeColors @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) WelcomeDarkColors else WelcomeLightColors
}
object WelcomeSizes {
    val logo = 64.dp
    val paddingH = 24.dp
    val paddingV = 20.dp
    val gap = 12.dp
    val button = 56.dp
    val avatar = 34.dp
    val badge = 14.dp
    val googleIcon = 20.dp
    val pill = RoundedCornerShape(28.dp)
}

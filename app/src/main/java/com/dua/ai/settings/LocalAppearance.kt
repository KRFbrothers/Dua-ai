package com.dua.ai.settings

import androidx.compose.runtime.Composable
import com.dua.ai.ui.theme.DuaTheme

/** Applies the saved appearance settings. Kept as the entry point used by existing screens. */
@Composable
fun LocalAppearance(value: LocalPreferences, content: @Composable () -> Unit) = DuaTheme(preferences = value, content = content)

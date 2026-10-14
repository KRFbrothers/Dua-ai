package com.dua.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.SettingsTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.dua.ai.ui.theme.Spacing

/** Standard top app bar for secondary pages: back arrow, title, optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuaTopBar(title: String, onBack: (() -> Unit)?, backEnabled: Boolean = true, backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = com.dua.ai.ui.theme.SettingsNameStyle, color = com.dua.ai.ui.theme.PageTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack, enabled = backEnabled,
                modifier = Modifier.size(SettingsSizes.touch).padding((SettingsSizes.touch - SettingsSizes.circleButton) / 2)
                    .clip(SettingsShapes.circle).background(SettingsTheme.colors.circleButton)
                    .border(SettingsSizes.border, SettingsTheme.colors.circleBorder, SettingsShapes.circle)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel, tint = SettingsTheme.colors.circleIcon)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/**
 * Scaffold for settings-style pages: top bar plus a scrolling column with the
 * standard page padding and gaps. Handles navigation-bar and keyboard insets.
 */
@Composable
fun DuaPageScaffold(
    title: String,
    onBack: (() -> Unit)?,
    backEnabled: Boolean = true,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    com.dua.ai.ui.theme.DuaPageTheme {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        DuaTopBar(title, onBack, backEnabled, backLabel, actions)
        Column(Modifier.weight(1f).fillMaxWidth().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = Spacing.lg).padding(top = Spacing.xs, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
    }
}

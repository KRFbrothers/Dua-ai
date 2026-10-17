package com.dua.ai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.theme.*

/** Flat rows keep long account names and scaled text above their trailing controls. */
@Composable
internal fun BackupRow(title: String, subtitle: String? = null, enabled: Boolean = true,
    onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)
        .then(if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick))
        .padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = BackupRowTitleStyle, color = PageTheme.colors.text)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = BackupBodyStyle, color = PageTheme.colors.subText)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun BackupPageScaffold(onBack: () -> Unit, backEnabled: Boolean, content: @Composable ColumnScope.() -> Unit) {
    DuaPageTheme {
        Column(Modifier.fillMaxSize().background(PageTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().heightIn(min = Sizes.topBarHeight).padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = backEnabled, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PageTheme.colors.text)
                }
                Text("Chat backup", style = BackupHeadingStyle, color = PageTheme.colors.text,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm).semantics { heading() })
            }
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                    .imePadding().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
            }
        }
    }
}

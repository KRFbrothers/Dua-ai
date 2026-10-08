package com.dua.ai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** A titled group of settings rows on one rounded surface. */
@Composable
fun SettingsSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm).semantics { heading() })
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = DuaShape.card, modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsDivider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun RowScope.RowContent(icon: ImageVector?, title: String, subtitle: String?, titleColor: Color, trailing: @Composable () -> Unit) {
    if (icon != null) Icon(icon, contentDescription = null, tint = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else titleColor,
        modifier = Modifier.size(Sizes.icon))
    Column(Modifier.weight(1f).padding(start = if (icon != null) Spacing.lg else 0.dp, end = Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    trailing()
}

/** Navigation row: icon, title, optional subtitle and a chevron. */
@Composable
fun SettingsRow(icon: ImageVector?, title: String, subtitle: String? = null, titleColor: Color = Color.Unspecified,
    showChevron: Boolean = true, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        RowContent(icon, title, subtitle, titleColor) {
            if (onClick != null && showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Switch row. The whole row toggles, so the touch target is the full width. */
@Composable
fun SettingsSwitchRow(icon: ImageVector?, title: String, subtitle: String? = null, checked: Boolean,
    enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        RowContent(icon, title, subtitle, Color.Unspecified) { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    }
}

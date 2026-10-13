package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.HomeActionLabelStyle
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/**
 * One of the Home action buttons above the composer (Dabba: rounded tonal pills with an icon).
 * Each is a real mode toggle (Search, Reasoning, Run code…), so tapping it changes the next reply.
 */
@Composable
internal fun HomeActionButton(toggle: ModeToggle, modifier: Modifier = Modifier) {
    val colors = DuaTheme.colors
    val bg = if (toggle.on) colors.chipSelected else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (toggle.on) colors.onChipSelected else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .heightIn(min = Sizes.touchTarget)
            .clip(DuaShape.homeAction)
            .background(bg)
            .border(1.dp, if (toggle.on) bg else colors.composerBorder, DuaShape.homeAction)
            .toggleable(value = toggle.on, role = Role.Switch, onValueChange = toggle.onChange)
            .semantics(mergeDescendants = true) { contentDescription = toggle.detail }
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(toggle.icon, contentDescription = null, tint = if (toggle.on) colors.chipSelectedIcon else fg, modifier = Modifier.size(Sizes.iconSmall))
        Text(toggle.label, style = HomeActionLabelStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.PillLabelStyle
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

@Composable
fun ChatTopBar(
    newChatEnabled: Boolean = true,
    showComputer: Boolean = true,
    onMenu: () -> Unit = {},
    onComputer: () -> Unit = {},
    onNewChat: () -> Unit = {},
    projectName: String = "Personal",
    onProjects: () -> Unit = {},
    onSearch: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton(Icons.Outlined.Menu, "Open chat history", onMenu)
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onProjects)
                .semantics { contentDescription = "Projects, $projectName" }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Dua AI", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    projectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Outlined.ExpandMore, null, modifier = Modifier.size(16.dp))
            }
        }
        if (showComputer) ComputerPill(onComputer)
        onSearch?.let { BarButton(Icons.Outlined.Search, "Find in chat", it) }
        BarButton(Icons.Outlined.EditNote, "New chat", onNewChat, newChatEnabled)
    }
}

/** Dabba's top-bar pill: icon + "Computer" on a rounded tonal surface, 48dp tall for touch. */
@Composable
private fun ComputerPill(onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = Sizes.touchTarget)
            .clip(DuaShape.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Computer, coming soon" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(DuaShape.pill).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(Icons.Outlined.DesktopWindows, null, modifier = Modifier.size(Sizes.iconTiny), tint = MaterialTheme.colorScheme.onSurface)
            Text("Computer", style = PillLabelStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

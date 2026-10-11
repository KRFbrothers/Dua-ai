package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Quiet navigation chrome; the project selector stays in the header rather than a second row. */
@Composable
fun ChatTopBar(newChatEnabled: Boolean, showComputer: Boolean, onMenu: () -> Unit, onComputer: () -> Unit, onNewChat: () -> Unit,
    projectName: String = "Personal", onProjects: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically) {
        RoundBarButton(Icons.Outlined.Menu, "Open chat history", enabled = true, onClick = onMenu)
        Column(Modifier.weight(1f).heightIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button, onClick = onProjects)
            .semantics { contentDescription = "Projects, $projectName" }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            Text("Dua AI", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(projectName, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = Spacing.xxs).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showComputer) {
            RoundBarButton(Icons.Outlined.DesktopWindows, "Computer, coming soon", enabled = true, onClick = onComputer)
        }
        RoundBarButton(Icons.Outlined.EditNote, "New chat", enabled = newChatEnabled, onClick = onNewChat)
    }
}

@Composable
private fun RoundBarButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

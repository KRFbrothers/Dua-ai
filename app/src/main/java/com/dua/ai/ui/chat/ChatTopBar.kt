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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        if (showComputer) BarButton(Icons.Outlined.DesktopWindows, "Computer, coming soon", onComputer)
        onSearch?.let { BarButton(Icons.Outlined.Search, "Find in chat", it) }
        BarButton(Icons.Outlined.EditNote, "New chat", onNewChat, newChatEnabled)
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

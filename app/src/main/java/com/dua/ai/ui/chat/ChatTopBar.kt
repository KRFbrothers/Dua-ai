package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Round history-menu button · "Computer" pill only on a new chat · round new-chat button. The model picker lives in the composer's model pill. */
@Composable
fun ChatTopBar(newChatEnabled: Boolean, showComputer: Boolean, onMenu: () -> Unit, onComputer: () -> Unit, onNewChat: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(Sizes.topBarHeight).padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically) {
        RoundBarButton(Icons.Outlined.Menu, "Open chat history", enabled = true, onClick = onMenu)
        Spacer(Modifier.weight(1f))
        if (showComputer) {
            Surface(onClick = onComputer, shape = DuaShape.chip, color = DuaTheme.colors.composer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.sm).semantics { contentDescription = "Computer, coming soon"; role = Role.Button }) {
                Row(Modifier.height(Sizes.topBarButton).padding(start = Spacing.lg, end = Spacing.lg + Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DesktopWindows, contentDescription = null, modifier = Modifier.size(Sizes.icon), tint = MaterialTheme.colorScheme.onSurface)
                    Text("Computer", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        RoundBarButton(Icons.Outlined.EditNote, "New chat", enabled = newChatEnabled, onClick = onNewChat)
    }
}

@Composable
private fun RoundBarButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    // 44 dp visual inside a 48 dp touch target.
    Box(Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
        Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = DuaTheme.colors.composer,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f),
            modifier = Modifier.size(Sizes.topBarButton).semantics { contentDescription = description; role = Role.Button }) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) }
        }
    }
}

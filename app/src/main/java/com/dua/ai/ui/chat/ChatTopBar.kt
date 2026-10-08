package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** History menu · current model (opens the model picker) · new chat. */
@Composable
fun ChatTopBar(model: String, newChatEnabled: Boolean, onMenu: () -> Unit, onModel: () -> Unit, onNewChat: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, contentDescription = "Open chat history") }
        Spacer(Modifier.weight(1f))
        Surface(onClick = onModel, shape = DuaShape.chip, color = MaterialTheme.colorScheme.background,
            modifier = Modifier.semantics { contentDescription = "Model: $model. Change model"; role = Role.Button }) {
            Row(Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(model, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xxs).size(Sizes.iconSmall))
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onNewChat, enabled = newChatEnabled) { Icon(Icons.Outlined.EditNote, contentDescription = "New chat") }
    }
}

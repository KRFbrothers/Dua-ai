package com.dua.ai.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.history.SavedChat
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import java.time.ZoneId

/**
 * History drawer: new chat, search, chats grouped by date, and Settings.
 * Long-press a chat (or use the accessibility action) to delete it.
 */
@Composable
fun HistoryDrawer(
    chats: List<SavedChat>,
    newChatEnabled: Boolean,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    now: Long? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    val reference = now ?: remember(chats) { System.currentTimeMillis() }
    val groups = remember(chats, query, reference, zone) { HistoryGrouping.group(HistoryGrouping.filter(chats, query), reference, zone) }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(Sizes.drawerWidth)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xs, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("Dua", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onNewChat, enabled = newChatEnabled) { Icon(Icons.Outlined.EditNote, contentDescription = "New chat") }
            }
            SearchField(query, { query = it.take(200) }, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)) {
                if (groups.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        Text(if (query.isNotBlank()) "No chats found" else "No chats yet", style = MaterialTheme.typography.titleSmall)
                        Text(if (query.isNotBlank()) "Try a different search." else "Your conversations will appear here.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
                groups.forEach { (bucket, items) ->
                    item(key = "header-${bucket.name}") {
                        Text(bucket.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.md, top = Spacing.lg, bottom = Spacing.xs).semantics { heading() })
                    }
                    items(items, key = { it.id }) { chat -> HistoryRow(chat, onOpen = { onOpenChat(chat.id) }, onDelete = { onDeleteChat(chat.id) }) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(Spacing.sm).clip(DuaShape.card)
                .combinedClickableCompat(onClick = onOpenSettings).heightIn(min = 52.dp).padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Settings", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.lg))
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = Sizes.touchTarget).padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            Box(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                if (value.isEmpty()) Text("Search chats", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(value, onValueChange, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search chats" })
            }
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
        }
    }
}

@Composable
private fun HistoryRow(chat: SavedChat, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clip(DuaShape.card)
            .combinedClickableCompat(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = "Chat options")
            .semantics { customActions = listOf(CustomAccessibilityAction("Delete chat") { onDelete(); true }) }
            .heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Text(chat.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = { menu = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, onLongClickLabel: String? = null) =
    combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)

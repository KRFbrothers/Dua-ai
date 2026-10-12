package com.dua.ai.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dua.ai.history.SavedChat
import com.dua.ai.history.ChatTitles
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class SearchResults(val chats: List<SavedChat>, val query: String, val matches: List<HistoryMatch>)

/**
 * Approved sidebar / expanded Aura: fixed navigation and footer, with project-scoped search and a scrolling recent-chat list.
 * Long-press a chat (or use an accessibility action) to rename or remove its cached copy.
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
    projectId: String? = null,
    onProjects: () -> Unit = {},
    onDestination: (com.dua.ai.ui.navigation.DuaDestination) -> Unit = {},
    profileName: String = "",
    activeChatId: String? = null,
    drawerOpen: Boolean = true,
    onRenameChat: (String, String) -> String? = { _, _ -> "Rename is unavailable." },
    serverDeletionEnabled: Boolean = false,
    onDeleteServerChat: (String) -> Unit = {},
) {
    var aura by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = aura && drawerOpen) { aura = false }
    var query by rememberSaveable(projectId) { mutableStateOf("") }
    var renamingId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var renameTitle by rememberSaveable(projectId) { mutableStateOf("") }
    var renameError by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    val scoped = remember(chats, projectId) { if (projectId == null) chats else chats.filter { it.projectId == projectId } }
    val needle = query.trim()
    var results by remember(projectId) { mutableStateOf<SearchResults?>(null) }
    LaunchedEffect(scoped, needle) {
        results = null
        if (needle.isEmpty()) return@LaunchedEffect
        delay(150)
        val matches = withContext(Dispatchers.Default) { HistorySearch.search(scoped, needle) { ensureActive() } }
        results = SearchResults(scoped, needle, matches)
    }
    val matches = if (needle.isEmpty()) null else results?.takeIf { it.chats === scoped && it.query == needle }?.matches
    val searching = needle.isNotEmpty() && matches == null
    val filtered = remember(scoped, needle, matches) { if (needle.isEmpty()) scoped else matches.orEmpty().map { it.chat } }
    val snippets = remember(matches) { matches.orEmpty().associateBy { it.chat.id } }
    val reference = now ?: remember(chats) { System.currentTimeMillis() }
    val groups = remember(filtered, reference, zone) { HistoryGrouping.group(filtered, reference, zone) }
    SidebarPanel(aura, { aura = it }, query, { query = it.take(HistorySearch.MAX_QUERY_CHARS) },
        newChatEnabled, onNewChat, onOpenSettings, onProjects, onDestination, profileName) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("recent-chats"), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)) {
                if (searching) item { Text("Searching chats…", modifier = Modifier.padding(Spacing.lg)) }
                else if (groups.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        Text(if (query.isNotBlank()) "No chats found" else "No chats yet", style = MaterialTheme.typography.titleSmall)
                        Text(if (query.isNotBlank()) "Try another phrase, or sync to refresh this project's history." else "Your conversations will appear here.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
                groups.forEach { (bucket, items) ->
                    item(key = "header-${bucket.name}") {
                        Text(bucket.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.md, top = Spacing.lg, bottom = Spacing.xs).semantics { heading() })
                    }
                    items(items, key = { it.id }) { chat -> HistoryRow(chat, snippets[chat.id], onOpen = { onOpenChat(chat.id) }, onDelete = { onDeleteChat(chat.id) },
                        onRename = { renamingId = chat.id; renameTitle = chat.title; renameError = null },
                        serverDeletionEnabled = serverDeletionEnabled, onDeleteServer = { onDeleteServerChat(chat.id) }, active = chat.id == activeChatId) }
                }
            }
    }
    renamingId?.let { id -> RenameChatDialog(renameTitle, renameError,
        onChange = { renameTitle = it; renameError = null }, onDismiss = { renamingId = null },
        onSave = { renameError = onRenameChat(id, renameTitle); if (renameError == null) renamingId = null }) }
}

@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainerLow, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = Sizes.touchTarget).padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            Box(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                if (value.isEmpty()) Text("Search chats", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(value, onValueChange, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search chats" })
            }
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
        }
    }
}

@Composable
private fun HistoryRow(chat: SavedChat, match: HistoryMatch?, onOpen: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit,
    serverDeletionEnabled: Boolean, onDeleteServer: () -> Unit, active: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    fun beginRename() { menu = false; onRename() }
    Box {
        Row(Modifier.fillMaxWidth().clip(DuaShape.card)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickableCompat(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = "Chat options")
            .semantics { customActions = listOf(
                CustomAccessibilityAction("Rename chat") { beginRename(); true },
                CustomAccessibilityAction("Remove cached chat") { onDelete(); true }) +
                if (serverDeletionEnabled) listOf(CustomAccessibilityAction("Delete chat from server") { onDeleteServer(); true }) else emptyList() }
            .heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(chat.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                match?.snippet?.let { snippet ->
                    val highlight = MaterialTheme.colorScheme.primary
                    Text(buildAnnotatedString {
                        append(snippet)
                        addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = highlight), match.matchStart, match.matchEnd)
                    }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { beginRename() })
            DropdownMenuItem(text = { Text("Pin") }, onClick = { menu = false; unavailable = "Pin" })
            if (serverDeletionEnabled) DropdownMenuItem(text = { Text("Delete chat") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                onClick = { menu = false; onDeleteServer() })
            DropdownMenuItem(text = { Text("Remove cached chat") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = { menu = false; onDelete() })
        }
    }
    unavailable?.let { title ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { unavailable = null }, title = { Text(title) },
            text = { Text("Coming soon") }, confirmButton = { androidx.compose.material3.TextButton(onClick = { unavailable = null }) { Text("Close") } })
    }
}

@Composable
internal fun RenameChatDialog(title: String, error: String?, onChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(horizontal = Spacing.xl).widthIn(max = 400.dp).fillMaxWidth(),
            shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Rename chat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Chat name", style = MaterialTheme.typography.labelLarge)
                Surface(shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    BasicTextField(title, onChange, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(Spacing.md)
                            .semantics { contentDescription = "Chat name" })
                }
                Text(error ?: "${title.trim().length}/${ChatTitles.MAX_LENGTH}", style = MaterialTheme.typography.bodySmall,
                    color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(enabled = ChatTitles.valid(title.trim()), onClick = onSave) { Text("Save") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, onLongClickLabel: String? = null) =
    combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)

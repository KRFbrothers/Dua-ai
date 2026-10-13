package com.dua.ai.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.history.SavedChat
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.*

/** Search box for chat history (also used by [SidebarPanel]). */
@Composable
fun SearchField(query: String = "", onQueryChange: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange, singleLine = true,
        placeholder = { Text("Search chats") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = DrawerShapes.newChat,
        modifier = modifier.fillMaxWidth().testTag("drawer-search-field"),
    )
}

/**
 * ChatGPT-style sidebar copied from the reference shell's DrawerContent: "Dua AI" + search circle, menu rows,
 * divider, recent chats, then the purple "Chat" pill with profile and voice circles docked at the bottom.
 * Long-press a chat for Rename / Pin / Delete.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HistoryDrawer(
    chats: List<SavedChat> = emptyList(),
    currentChatId: String? = null,
    onSelectChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onSearch: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onDeleteChat: (String) -> Unit = {},
    onRenameChat: (String, String) -> String? = { _, _ -> null },
    serverDeletionEnabled: Boolean = false,
    onDeleteServerChat: (String) -> Unit = {},
    onOpenSettings: () -> Unit = onNavigateSettings,
    onProjects: () -> Unit = {},
    onDestination: (DuaDestination) -> Unit = {},
    pinEnabled: Boolean = false,
    onPinChat: (String, Boolean) -> String? = { _, _ -> null },
    profileName: String = "",
    newChatEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = DrawerTheme.colors
    var auraExpanded by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<SavedChat?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val visible = remember(chats, query) {
        HistoryGrouping.filter(chats, query).sortedWith(compareByDescending<SavedChat> { it.pinned }.thenByDescending { it.updatedAt })
    }

    ModalDrawerSheet(drawerContainerColor = colors.container, modifier = modifier.width(DrawerSizes.width).testTag("history-drawer")) {
        Column(
            Modifier.fillMaxSize().background(colors.container).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = DrawerSizes.paddingH, vertical = DrawerSizes.paddingV),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Header: "Dua AI" + search circle (42 dp visual in a 48 dp slot; padding trimmed by the 3 dp overhang).
                Row(
                    Modifier.fillMaxWidth().padding(top = DrawerSizes.headerTop - (DrawerSizes.touch - DrawerSizes.searchCircle) / 2,
                        bottom = DrawerSizes.headerBottom - (DrawerSizes.touch - DrawerSizes.searchCircle) / 2),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Dua AI", style = DrawerTitleStyle, color = colors.title, modifier = Modifier.semantics { heading() })
                    Box(
                        Modifier.offset(x = (DrawerSizes.touch - DrawerSizes.searchCircle) / 2).size(DrawerSizes.touch)
                            .clickable(role = Role.Button, onClickLabel = if (searching) "Close search" else "Search chats") {
                                searching = !searching; if (!searching) query = ""; onSearch()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(DrawerSizes.searchCircle).clip(DrawerShapes.circle).background(colors.searchCircle), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Search, contentDescription = "Search chats", tint = colors.searchIcon, modifier = Modifier.size(DrawerSizes.searchIcon))
                        }
                    }
                }
                AnimatedVisibility(searching) {
                    SearchField(query, { query = it }, Modifier.padding(bottom = DrawerSizes.menuPaddingV))
                }

                DrawerMenuRow(Icons.Outlined.Image, "Images") { onDestination(DuaDestination.Images) }
                DrawerMenuRow(Icons.Outlined.CollectionsBookmark, "Library") { onDestination(DuaDestination.Library) }
                DrawerMenuRow(Icons.Outlined.Folder, "Projects", onProjects)
                DrawerMenuRow(Icons.Outlined.CloudQueue, "Computer") { onDestination(DuaDestination.Computer) }
                DrawerMenuRow(Icons.Outlined.Schedule, "AI Agent") { onDestination(DuaDestination.Agent) }

                // Expandable Aura (the reference's "Explore" slot): Plugins and Work.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clip(DrawerShapes.row)
                        .clickable(role = Role.Button, onClickLabel = if (auraExpanded) "Collapse Aura" else "Expand Aura") { auraExpanded = !auraExpanded }
                        .semantics { stateDescription = if (auraExpanded) "Expanded" else "Collapsed" }
                        .padding(vertical = DrawerSizes.menuPaddingV),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.GridView, null, tint = colors.menuText, modifier = Modifier.size(DrawerSizes.menuIcon))
                    Spacer(Modifier.width(DrawerSizes.menuGap))
                    Text("Aura", style = DrawerMenuStyle, color = colors.menuText, modifier = Modifier.weight(1f))
                    Icon(if (auraExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                        tint = colors.chevron, modifier = Modifier.size(DrawerSizes.chevron))
                }
                AnimatedVisibility(auraExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.fillMaxWidth().padding(start = DrawerSizes.subIndent)) {
                        DrawerSubRow(Icons.Outlined.Extension, "Plugins") { onDestination(DuaDestination.Plugins) }
                        DrawerSubRow(Icons.Outlined.WorkOutline, "Work") { onDestination(DuaDestination.Work) }
                    }
                }

                HorizontalDivider(color = colors.divider, thickness = DrawerSizes.dividerThickness, modifier = Modifier.padding(vertical = DrawerSizes.dividerPaddingV))

                Column(Modifier.testTag("drawer-recent-chats")) {
                    if (visible.isEmpty()) Text(if (query.isBlank()) "No chats yet" else "No matching chats", style = DrawerChatStyle,
                        color = colors.subText, modifier = Modifier.padding(vertical = DrawerSizes.chatPaddingV))
                    visible.forEach { chat ->
                        ChatRow(chat, chat.id == currentChatId, serverDeletionEnabled, pinEnabled,
                            onSelect = { onSelectChat(chat.id) },
                            onRename = { renaming = chat },
                            onPin = { onPinChat(chat.id, !chat.pinned)?.let { notice = it } },
                            onDelete = { if (serverDeletionEnabled) onDeleteServerChat(chat.id) else onDeleteChat(chat.id) })
                    }
                }
                notice?.let { Text(it, style = DrawerChatStyle, color = colors.subText, modifier = Modifier.padding(vertical = DrawerSizes.chatPaddingV)) }
            }

            // Bottom dock: purple "Chat" pill, profile initial, voice.
            Row(
                Modifier.fillMaxWidth().padding(top = DrawerSizes.dockTop),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                val newChatSource = remember { MutableInteractionSource() }
                Box(
                    Modifier.heightIn(min = DrawerSizes.touch)
                        .clickable(newChatSource, indication = null, enabled = newChatEnabled, role = Role.Button, onClickLabel = "Start a new chat", onClick = onNewChat)
                        .testTag("drawer-new-chat"),
                    contentAlignment = Alignment.Center,
                ) {
                    // The pill keeps the reference 40 dp height; the surrounding 48 dp slot takes the touch.
                    Row(
                        Modifier.clip(DrawerShapes.newChat).background(colors.newChat).indication(newChatSource, ripple())
                            .padding(horizontal = DrawerSizes.newChatPaddingH, vertical = DrawerSizes.newChatPaddingV),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DrawerSizes.newChatGap),
                    ) {
                        Icon(Icons.Default.Edit, null, tint = colors.onNewChat, modifier = Modifier.size(DrawerSizes.newChatIcon))
                        Text("Chat", style = DrawerNewChatStyle, color = colors.onNewChat)
                    }
                }
                Row(Modifier.offset(x = (DrawerSizes.touch - DrawerSizes.avatar) / 2), horizontalArrangement = Arrangement.spacedBy(DrawerSizes.dockGap),
                    verticalAlignment = Alignment.CenterVertically) {
                    DockCircle(colors.avatar, "Profile & Settings", onOpenSettings) {
                        val initial = profileName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                        if (initial == null) Icon(Icons.Outlined.PersonOutline, null, tint = colors.onAvatar, modifier = Modifier.size(DrawerSizes.voiceIcon))
                        else Text(initial.toString(), style = DrawerAvatarStyle, color = colors.onAvatar)
                    }
                    DockCircle(colors.voice, "Voice", onVoiceClick) {
                        Icon(Icons.Default.GraphicEq, null, tint = colors.onVoice, modifier = Modifier.size(DrawerSizes.voiceIcon))
                    }
                }
            }
        }
    }

    renaming?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        var error by remember(chat.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(title, { title = it; error = null }, singleLine = true, label = { Text("Title") },
                    isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.testTag("rename-field"))
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    val result = onRenameChat(chat.id, title.trim())
                    if (result == null) renaming = null else error = result
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRow(chat: SavedChat, current: Boolean, serverDeletion: Boolean, pinEnabled: Boolean,
    onSelect: () -> Unit, onRename: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    val colors = DrawerTheme.colors
    var menu by remember(chat.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch)
                .combinedClickable(onClick = onSelect, onClickLabel = "Open chat", onLongClick = { menu = true }, onLongClickLabel = "Chat options")
                .semantics { selected = current }
                .padding(vertical = DrawerSizes.chatPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(chat.title.ifBlank { "New chat" }, style = DrawerChatStyle, color = colors.chatText, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (chat.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = colors.subText, modifier = Modifier.size(DrawerSizes.pinIcon))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { menu = false; onRename() })
            if (pinEnabled) DropdownMenuItem(text = { Text(if (chat.pinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text(if (serverDeletion) "Delete chat" else "Remove cached chat") },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun DockCircle(color: androidx.compose.ui.graphics.Color, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(DrawerSizes.touch).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(DrawerSizes.avatar).clip(DrawerShapes.circle).background(color), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun DrawerMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = DrawerTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clickable(role = Role.Button, onClick = onClick).padding(vertical = DrawerSizes.menuPaddingV),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DrawerSizes.menuGap),
    ) {
        Icon(icon, null, tint = colors.menuText, modifier = Modifier.size(DrawerSizes.menuIcon))
        Text(label, style = DrawerMenuStyle, color = colors.menuText)
    }
}

@Composable
private fun DrawerSubRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = DrawerTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DrawerSizes.subGap),
    ) {
        Icon(icon, null, tint = colors.chevron, modifier = Modifier.size(DrawerSizes.subIcon))
        Text(label, style = DrawerSubStyle, color = colors.chatText)
    }
}

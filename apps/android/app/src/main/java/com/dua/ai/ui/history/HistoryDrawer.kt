package com.dua.ai.ui.history

import com.dua.ai.ui.theme.DuaFixedColors
import com.dua.ai.ui.theme.ChatTheme

import com.dua.ai.ui.chat.DuaDialog
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.SheetTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import com.dua.ai.history.HistoryGrouping
import com.dua.ai.history.HistorySearch
import com.dua.ai.history.SavedChat
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.components.AccountAvatar
import com.dua.ai.ui.theme.*
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Search box for chat history. */
@Composable
fun SearchField(query: String = "", onQueryChange: (String) -> Unit = {}, modifier: Modifier = Modifier,
    requestFocus: Boolean = true) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(requestFocus) {
        if (requestFocus) focusRequester.requestFocus()
        else if (fieldFocused) { focusManager.clearFocus(); keyboard?.hide() }
    }
    OutlinedTextField(
        value = query, onValueChange = onQueryChange, singleLine = true,
        placeholder = { Text("Search chats") },
        leadingIcon = { Icon(ReferenceIcons.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { onQueryChange(""); focusRequester.requestFocus(); keyboard?.show() }) {
                Icon(Icons.Outlined.Close, "Clear chat search")
            }
        }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); keyboard?.hide() }),
        shape = DrawerShapes.newChat, colors = com.dua.ai.ui.components.pageTextFieldColors(),
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester).testTag("drawer-search-field")
            .onFocusChanged { fieldFocused = it.isFocused }
            .semantics { contentDescription = "Search chat history" },
    )
}

/**
 * Drawer: brand/search, destinations and real chats, with fixed New chat and profile actions.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun HistoryDrawer(
    chats: List<SavedChat> = emptyList(),
    currentChatId: String? = null,
    onSelectChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    profileName: String = "",
    profilePhotoUrl: String = "",
    onDeleteChat: (String) -> Unit = {},
    onRenameChat: (String, String) -> String? = { _, _ -> null },
    serverDeletionEnabled: Boolean = false,
    onDeleteServerChat: (String) -> Unit = {},
    onProjects: () -> Unit = {},
    onDestination: (DuaDestination) -> Unit = {},
    pinEnabled: Boolean = false,
    onPinChat: (String, Boolean) -> String? = { _, _ -> null },
    newChatEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    /** Wide windows: always-visible side panel (no drawer back handling or scrim). */
    permanent: Boolean = false,
) {
    val colors = DrawerTheme.colors
    val scope = rememberCoroutineScope()
    val motionAllowed = rememberMotionAllowed()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pinnedOnly by rememberSaveable { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedChat?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val browseScroll = rememberScrollState()
    val searchScroll = rememberScrollState()
    LaunchedEffect(query, pinnedOnly) { searchScroll.scrollTo(0) }
    // Telegram-style swipe delete waits 5 s for Undo (UndoView) before the real delete runs.
    var pendingDelete by remember { mutableStateOf<SavedChat?>(null) }
    val commitDelete by androidx.compose.runtime.rememberUpdatedState { chat: SavedChat ->
        if (serverDeletionEnabled) onDeleteServerChat(chat.id) else onDeleteChat(chat.id)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { pendingDelete?.let { commitDelete(it) } } }
    fun swipeDelete(chat: SavedChat) { pendingDelete?.let { commitDelete(it) }; pendingDelete = chat }
    val visibleAll = remember(chats, query, pinnedOnly) {
        HistoryGrouping.filter(HistoryGrouping.recent(chats), query).filter { !pinnedOnly || it.pinned }.sortedWith(compareByDescending<SavedChat> { it.pinned }.thenByDescending { it.updatedAt })
    }
    val visible = visibleAll.filter { it.id != pendingDelete?.id }

    HistorySheet(permanent, drawerState, colors.container, modifier.width(DrawerSizes.width).testTag("history-drawer")) {
        Column(Modifier.fillMaxSize().background(colors.container)
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = DrawerSizes.paddingH, vertical = DrawerSizes.paddingV)) {
            Row(Modifier.fillMaxWidth().padding(bottom = DrawerSizes.headerBottom),
                verticalAlignment = Alignment.CenterVertically) {
                DrawerBrandMark(Modifier.size(DrawerSizes.brandIcon))
                Text("Dua AI", style = DrawerTitleStyle, color = colors.title,
                    modifier = Modifier.weight(1f).padding(start = DrawerSizes.menuGap).semantics { heading() })
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) { query = ""; focusManager.clearFocus(); keyboard?.hide() }
                    onSearch()
                },
                    modifier = Modifier.size(DrawerSizes.touch)) {
                    Icon(if (searching) Icons.Outlined.Close else ReferenceIcons.Search,
                        contentDescription = if (searching) "Close chat search" else "Search chats", tint = colors.searchIcon)
                }
                if (!permanent) IconButton(onClick = { scope.launch { drawerState.close() } }, modifier = Modifier.size(DrawerSizes.touch)) {
                    Icon(Icons.Outlined.Close, "Close chat history", tint = colors.searchIcon)
                }
            }
            AnimatedVisibility(searching,
                enter = expandVertically(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap()) + fadeIn(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap()),
                exit = shrinkVertically(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap()) + fadeOut(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap())) {
                SearchField(query, { query = it.take(HistorySearch.MAX_QUERY_CHARS) }, Modifier.padding(bottom = DrawerSizes.menuPaddingV),
                    requestFocus = searching && (permanent || drawerState.isOpen))
            }
            // The body scrolls clear of the measured footer even at 2x text or with the IME open.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(if (searching) searchScroll else browseScroll).testTag("drawer-body")) {
                if (!searching) {
                    Spacer(Modifier.height(DrawerSizes.menuPaddingV))
                    DrawerMenuRow(ReferenceIcons.Chat, "New chat", enabled = newChatEnabled,
                        selected = currentChatId.isNullOrBlank(), modifier = Modifier.testTag("drawer-new-chat")
                            .semantics { contentDescription = "Start a new chat" }, onClick = onNewChat)
                    DrawerMenuRow(Icons.Outlined.GridView, "Library") { onDestination(DuaDestination.Library) }
                    DrawerMenuRow(ReferenceIcons.Images, "Images") { onDestination(DuaDestination.Images) }
                    DrawerMenuRow(ReferenceIcons.Projects, "Projects", onClick = onProjects)
                    Row(Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clip(DrawerShapes.row)
                        .clickable(role = Role.Button, onClickLabel = if (moreExpanded) "Collapse More" else "Expand More") { moreExpanded = !moreExpanded }
                        .semantics { stateDescription = if (moreExpanded) "Expanded" else "Collapsed" }
                        .padding(horizontal = DrawerSizes.actionPaddingH, vertical = DrawerSizes.menuPaddingV),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MoreHoriz, null, tint = colors.menuText, modifier = Modifier.size(DrawerSizes.menuIcon))
                        Spacer(Modifier.width(DrawerSizes.menuGap))
                        Text("More", style = DrawerMenuStyle, color = colors.menuText, modifier = Modifier.weight(1f))
                        Icon(if (moreExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                            tint = colors.chevron, modifier = Modifier.size(DrawerSizes.chevron))
                    }
                    AnimatedVisibility(moreExpanded,
                        enter = expandVertically(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap()) + fadeIn(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap()),
                        exit = shrinkVertically(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap()) + fadeOut(animationSpec = if (motionAllowed) DuaMotion.gentle() else snap())) {
                        Column(Modifier.fillMaxWidth().padding(start = DrawerSizes.subIndent)) {
                            DrawerSubRow(Icons.Outlined.DesktopWindows, "Computer") { onDestination(DuaDestination.Computer) }
                            DrawerSubRow(ReferenceIcons.Plugins, "Plugins") { onDestination(DuaDestination.Plugins) }
                            DrawerSubRow(Icons.Outlined.SmartToy, "AI Agent") { onDestination(DuaDestination.Agent) }
                            DrawerSubRow(ReferenceIcons.Work, "Work") { onDestination(DuaDestination.Work) }
                        }
                    }
                    HorizontalDivider(color = colors.divider, thickness = DrawerSizes.dividerThickness,
                        modifier = Modifier.padding(vertical = DrawerSizes.dividerPaddingV))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).padding(start = DrawerSizes.actionPaddingH),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pinnedOnly) "Pinned" else "All", style = DrawerChatStyle, color = colors.subText, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { filterMenu = true }, modifier = Modifier.size(DrawerSizes.touch)) {
                            Icon(Icons.Outlined.FilterAlt, "Filter chats", tint = colors.subText)
                        }
                        com.dua.ai.ui.chat.DuaDropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false },
                            containerColor = colors.container) {
                            com.dua.ai.ui.chat.DuaDropdownItem("All chats", onClick = { pinnedOnly = false; filterMenu = false }, selected = !pinnedOnly)
                            com.dua.ai.ui.chat.DuaDropdownItem("Pinned chats", onClick = { pinnedOnly = true; filterMenu = false }, selected = pinnedOnly)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().testTag("drawer-recent-chats")) {
                    if (visible.isEmpty()) Text(when {
                        query.isNotBlank() -> "No matching chats"
                        pinnedOnly -> "No pinned chats"
                        else -> "No chats yet"
                    }, style = DrawerChatStyle, color = colors.subText,
                        modifier = Modifier.padding(horizontal = DrawerSizes.actionPaddingH, vertical = DrawerSizes.chatPaddingV))
                    visible.forEach { chat ->
                        key(chat.id) {
                            val c = ChatTheme.colors
                            DialogSwipeRow(
                                start = if (pinEnabled) DialogSwipeAction(if (chat.pinned) "Unpin" else "Pin", SwipePinIcon,
                                    c.chipFill, c.accent, c.chipText, slideOut = false) { onPinChat(chat.id, !chat.pinned)?.let { notice = it } } else null,
                                end = DialogSwipeAction("Delete", SwipeDeleteIcon, c.chipFill, DuaFixedColors.SwipeDanger,
                                    c.chipText, slideOut = true) { swipeDelete(chat) },
                            ) {
                                ChatRow(chat, chat.id == currentChatId, serverDeletionEnabled, pinEnabled, motionAllowed,
                                    onSelect = { onSelectChat(chat.id) }, onRename = { renaming = chat },
                                    onPin = { onPinChat(chat.id, !chat.pinned)?.let { notice = it } },
                                    onDelete = { if (serverDeletionEnabled) onDeleteServerChat(chat.id) else onDeleteChat(chat.id) })
                            }
                        }
                    }
                }
                pendingDelete?.let { chat ->
                    ChatUndoBar("Chat deleted", chat.id, onUndo = { pendingDelete = null },
                        onTimeout = { if (pendingDelete?.id == chat.id) { pendingDelete = null; commitDelete(chat) } },
                        modifier = Modifier.padding(vertical = DrawerSizes.chatPaddingV))
                }
                notice?.let { Text(it, style = DrawerChatStyle, color = colors.subText, modifier = Modifier.padding(vertical = DrawerSizes.chatPaddingV)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = DrawerSizes.dockTop).testTag("drawer-bottom-actions"),
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = DrawerSizes.touch).testTag("drawer-profile")
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .semantics(mergeDescendants = true) { contentDescription = "Open drawer profile and settings" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DrawerSizes.menuGap)) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                        AccountAvatar(email = null, displayName = profileName, avatarUrl = profilePhotoUrl,
                            size = DrawerSizes.avatar, tag = "drawer-account-avatar")
                    }
                    Text(profileName.ifBlank { "Your account" }, style = DrawerChatStyle, color = colors.chatText,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(DrawerSizes.touch)) {
                    Icon(Icons.Outlined.Settings, "Open settings", tint = colors.menuText)
                }
            }
        }
    }

    renaming?.let { chat -> RenameChatDialog(chat, onRenameChat, onDismiss = { renaming = null }) }

}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRow(chat: SavedChat, current: Boolean, serverDeletion: Boolean, pinEnabled: Boolean, motionAllowed: Boolean,
    onSelect: () -> Unit, onRename: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    val colors = DrawerTheme.colors
    var menu by remember(chat.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clip(DrawerShapes.row)
                .background(if (current) ChatTheme.colors.userBubble else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(onClick = onSelect, onClickLabel = "Open chat", onLongClick = { menu = true }, onLongClickLabel = "Chat options")
                .semantics { selected = current }
                .padding(start = DrawerSizes.actionPaddingH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Auto titles arrive after the first reply; cross-fade instead of jumping.
            androidx.compose.animation.Crossfade(chat.title.ifBlank { "New chat" }, Modifier.weight(1f),
                animationSpec = if (motionAllowed) DuaMotion.gentle() else snap(), label = "chat-title") { shown ->
                Text(shown, style = DrawerChatStyle, color = colors.chatText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (pinEnabled) IconButton(onClick = onPin, modifier = Modifier.size(DrawerSizes.touch)) {
                Icon(Icons.Outlined.PushPin, "${if (chat.pinned) "Unpin" else "Pin"} ${chat.title}",
                    tint = if (chat.pinned) colors.menuText else colors.subText, modifier = Modifier.size(DrawerSizes.pinIcon))
            } else if (chat.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = colors.subText,
                modifier = Modifier.padding(end = DrawerSizes.actionPaddingH).size(DrawerSizes.pinIcon))
        }
        if (menu) ChatOptionsSheet(chat.title.ifBlank { "New chat" }, chat.pinned, pinEnabled, serverDeletion, onDismiss = { menu = false },
            onRename = { menu = false; onRename() }, onPin = { menu = false; onPin() }, onDelete = { menu = false; onDelete() })
    }
}

@Composable
private fun DrawerMenuRow(icon: ImageVector, label: String, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = DrawerTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clip(DrawerShapes.row)
            .background(if (selected) ChatTheme.colors.userBubble else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = DrawerSizes.actionPaddingH, vertical = DrawerSizes.menuPaddingV),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DrawerSizes.menuGap),
    ) {
        Icon(icon, null, tint = colors.menuText, modifier = Modifier.size(DrawerSizes.menuIcon))
        Text(label, style = DrawerMenuStyle, color = colors.menuText, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DrawerSubRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = DrawerTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clip(DrawerShapes.row).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DrawerSizes.menuGap),
    ) {
        Icon(icon, null, tint = colors.menuText, modifier = Modifier.size(DrawerSizes.menuIcon))
        Text(label, style = DrawerMenuStyle, color = colors.menuText, modifier = Modifier.weight(1f))
    }
}

/** Long-press chat menu in the drawer, as a Dabba sheet (same rows and labels as before). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatOptionsSheet(title: String, pinned: Boolean, pinEnabled: Boolean, serverDeletion: Boolean, onDismiss: () -> Unit,
    onRename: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    com.dua.ai.ui.chat.DuaSheet(onDismiss = onDismiss) { ChatOptionsContent(title, pinned, pinEnabled, serverDeletion, onRename, onPin, onDelete) }
}

@Composable
internal fun ChatOptionsContent(title: String, pinned: Boolean, pinEnabled: Boolean, serverDeletion: Boolean,
    onRename: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = com.dua.ai.ui.theme.SheetSizes.paddingH).padding(bottom = com.dua.ai.ui.theme.SheetSizes.paddingBottom)) {
        com.dua.ai.ui.chat.SheetTitle(title)
        com.dua.ai.ui.chat.SheetGroup {
            com.dua.ai.ui.chat.SheetActionRow("Rename", Icons.Outlined.Edit, onClick = onRename)
            if (pinEnabled) {
                com.dua.ai.ui.chat.SheetDivider()
                com.dua.ai.ui.chat.SheetActionRow(if (pinned) "Unpin" else "Pin", Icons.Outlined.PushPin, onClick = onPin)
            }
            com.dua.ai.ui.chat.SheetDivider()
            com.dua.ai.ui.chat.SheetActionRow(if (serverDeletion) "Delete chat" else "Remove cached chat", Icons.Outlined.Delete, onClick = onDelete, destructive = true)
        }
    }
}

/** Loads the signed-in account's https profile photo (Google photoUrl); null keeps the initial fallback. */
@Composable
internal fun rememberProfilePhoto(url: String): ImageBitmap? {
    if (url.isBlank()) return null
    if (androidx.compose.ui.platform.LocalInspectionMode.current) return null
    val photo by produceState<ImageBitmap?>(null, url) { value = withContext(Dispatchers.IO) { ProfilePhotoCache.load(url) } }
    return photo
}

internal object ProfilePhotoCache {
    private const val MAX_BYTES = 2_000_000
    private const val THUMBNAIL_SIZE = 128
    private val cache = android.util.LruCache<String, ImageBitmap>(4)
    fun load(url: String): ImageBitmap? {
        if (!url.startsWith("https://")) return null
        cache.get(url)?.let { return it }
        return runCatching {
            val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000; instanceFollowRedirects = true }
            try {
                if (connection.responseCode !in 200..299 || connection.contentLengthLong > MAX_BYTES) return null
                val bytes = connection.inputStream.use { readBoundedPhoto(it) } ?: return null
                decodeThumbnail(bytes)
            } finally { connection.disconnect() }
        }.getOrNull()?.also { cache.put(url, it) }
    }

    /** Enforce the limit during reading, even when Content-Length is missing or incorrect. */
    internal fun readBoundedPhoto(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > MAX_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** Bound pixel allocation before decoding; the cache holds only avatar-sized bitmaps. */
    internal fun decodeThumbnail(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > THUMBNAIL_SIZE || bounds.outHeight / sample > THUMBNAIL_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}

/** Modal sheet inside the drawer on phones; a permanent sheet beside the chat on expanded windows. */
@Composable
private fun HistorySheet(permanent: Boolean, drawerState: DrawerState, container: androidx.compose.ui.graphics.Color, modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = if (permanent) RectangleShape else DuaShape.drawer
    val panel = modifier.shadow(6.dp, shape, clip = false)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f), shape)
    if (permanent) androidx.compose.foundation.layout.Box(Modifier.testTag("history-panel")) {
        androidx.compose.material3.PermanentDrawerSheet(modifier = panel, drawerShape = shape, drawerContainerColor = container, content = content)
    }
    else ModalDrawerSheet(drawerState = drawerState, drawerShape = shape, drawerContainerColor = container, modifier = panel, content = content)
}

/** Shared rename dialog: the active chat identity and validation stay with the caller. */
@Composable
internal fun RenameChatDialog(chat: SavedChat, onRenameChat: (String, String) -> String?, onDismiss: () -> Unit) {
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        var error by remember(chat.id) { mutableStateOf<String?>(null) }
        DuaDialog(
            onDismissRequest = { onDismiss() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = Spacing.lg).widthIn(max = SheetSizes.dialogMaxWidth).fillMaxWidth(),
            title = "Rename chat",
            text = {
                OutlinedTextField(title, { title = it; error = null }, singleLine = true, label = { Text("Title") }, shape = SheetShapes.badge,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SheetTheme.colors.accent, focusedLabelColor = SheetTheme.colors.accent,
                        cursorColor = SheetTheme.colors.accent, unfocusedBorderColor = SheetTheme.colors.divider,
                        focusedTextColor = SheetTheme.colors.text, unfocusedTextColor = SheetTheme.colors.text),
                    isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("rename-field")
                        .semantics { contentDescription = "Chat title" })

            },
            confirmButton = {
                SheetTextButton("Save", {
                    val result = onRenameChat(chat.id, title.trim())
                    if (result == null) onDismiss() else error = result
                }, enabled = title.isNotBlank())
            },
            dismissButton = { SheetTextButton("Cancel", { onDismiss() }) },
        )
}

/** Header options reuse the same chat actions and rename dialog as the history list. */
@Composable
internal fun ChatOptionsButton(chat: SavedChat, pinEnabled: Boolean, serverDeletion: Boolean,
    onRename: () -> Unit, onPinChat: (String, Boolean) -> String?, onDelete: () -> Unit) {
    var expanded by remember(chat.id) { mutableStateOf(false) }
    var notice by remember(chat.id) { mutableStateOf<String?>(null) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(DrawerSizes.touch)) {
            Icon(Icons.Outlined.MoreHoriz, "Chat options", tint = ChatTheme.colors.text)
        }
        // Dispose the popup before opening a dialog, so two focus-owning windows
        // cannot coexist during the menu's exit transition.
        if (expanded) com.dua.ai.ui.chat.DuaDropdownMenu(true, onDismissRequest = { expanded = false }) {
            if (pinEnabled) com.dua.ai.ui.chat.DuaDropdownItem(if (chat.pinned) "Unpin" else "Pin",
                icon = Icons.Outlined.PushPin, onClick = { expanded = false; notice = onPinChat(chat.id, !chat.pinned) })
            com.dua.ai.ui.chat.DuaDropdownItem("Rename", icon = Icons.Outlined.Edit,
                onClick = { expanded = false; onRename() })
            com.dua.ai.ui.chat.DuaDropdownItem(if (serverDeletion) "Delete chat" else "Remove cached chat",
                icon = Icons.Outlined.Delete, onClick = { expanded = false; onDelete() })
        }
    }
    notice?.let { message -> DuaDialog(onDismissRequest = { notice = null }, title = "Chat options",
        text = { Text(message) }, confirmButton = { SheetTextButton("Close", { notice = null }) }) }
}

package com.dua.ai.ui.history

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.history.SavedChat
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.theme.*
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Search box for chat history. */
@Composable
fun SearchField(query: String = "", onQueryChange: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange, singleLine = true,
        placeholder = { Text("Search chats") },
        leadingIcon = { Icon(ReferenceIcons.Search, contentDescription = null) },
        shape = DrawerShapes.newChat, colors = com.dua.ai.ui.components.pageTextFieldColors(),
        modifier = modifier.fillMaxWidth().testTag("drawer-search-field"),
    )
}

/**
 * Sidebar copied from the reference shell's DrawerContent: "Dua AI" + search circle, menu rows,
 * divider, recent chats, then the purple "Chat" pill with the profile circle docked at the bottom.
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
    profilePhotoUrl: String = "",
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
        HistoryGrouping.filter(HistoryGrouping.recent(chats), query).sortedWith(compareByDescending<SavedChat> { it.pinned }.thenByDescending { it.updatedAt })
    }

    ModalDrawerSheet(drawerShape = RectangleShape, drawerContainerColor = colors.container,
        modifier = modifier.width(DrawerSizes.width).testTag("history-drawer")) {
        Box(
            Modifier.fillMaxSize().background(colors.container)
                .background(Brush.verticalGradient(0f to colors.gradientTop, 1f to colors.container, endY = with(androidx.compose.ui.platform.LocalDensity.current) { DrawerSizes.gradientHeight.toPx() }))
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = DrawerSizes.paddingH, vertical = DrawerSizes.paddingV),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = DrawerSizes.dockReserve)) {
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
                        Box(Modifier.size(DrawerSizes.searchCircle).shadow(DrawerSizes.searchElevation, DrawerShapes.circle, ambientColor = colors.searchShadow, spotColor = colors.searchShadow)
                            .clip(DrawerShapes.circle).background(colors.searchCircle), contentAlignment = Alignment.Center) {
                            Icon(ReferenceIcons.Search, contentDescription = "Search chats", tint = colors.searchIcon, modifier = Modifier.size(DrawerSizes.searchIcon))
                        }
                    }
                }
                AnimatedVisibility(searching) {
                    SearchField(query, { query = it }, Modifier.padding(bottom = DrawerSizes.menuPaddingV))
                }

                DrawerMenuRow(ReferenceIcons.Images, "Images") { onDestination(DuaDestination.Images) }
                DrawerMenuRow(ReferenceIcons.Library, "Library") { onDestination(DuaDestination.Library) }
                DrawerMenuRow(ReferenceIcons.Projects, "Projects", onProjects)
                DrawerMenuRow(ReferenceIcons.Computer, "Computer") { onDestination(DuaDestination.Computer) }
                DrawerMenuRow(ReferenceIcons.Agent, "AI Agent") { onDestination(DuaDestination.Agent) }

                // Expandable Aura (the reference's "Explore" slot): Plugins and Work.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = DrawerSizes.touch).clip(DrawerShapes.row)
                        .clickable(role = Role.Button, onClickLabel = if (auraExpanded) "Collapse Aura" else "Expand Aura") { auraExpanded = !auraExpanded }
                        .semantics { stateDescription = if (auraExpanded) "Expanded" else "Collapsed" }
                        .padding(vertical = DrawerSizes.menuPaddingV),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ReferenceIcons.Aura, null, tint = colors.menuText, modifier = Modifier.size(DrawerSizes.menuIcon))
                    Spacer(Modifier.width(DrawerSizes.menuGap))
                    Text("Aura", style = DrawerMenuStyle, color = colors.menuText, modifier = Modifier.weight(1f))
                    Icon(if (auraExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                        tint = colors.chevron, modifier = Modifier.size(DrawerSizes.chevron))
                }
                AnimatedVisibility(auraExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.fillMaxWidth().padding(start = DrawerSizes.subIndent)) {
                        DrawerSubRow(ReferenceIcons.Plugins, "Plugins") { onDestination(DuaDestination.Plugins) }
                        DrawerSubRow(ReferenceIcons.Work, "Work") { onDestination(DuaDestination.Work) }
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

            // Fade the recent chats out behind the dock, like the reference.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(DrawerSizes.bottomFade)
                .background(Brush.verticalGradient(listOf(colors.container.copy(alpha = 0f), colors.container))))

            // Bottom dock: purple "Chat" pill and profile photo / initial. Voice mode lives in the composer.
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(top = DrawerSizes.dockTop),
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
                        Icon(ReferenceIcons.Chat, null, tint = colors.onNewChat, modifier = Modifier.size(DrawerSizes.newChatIcon))
                        Text("Chat", style = DrawerNewChatStyle, color = colors.onNewChat)
                    }
                }
                Row(Modifier.offset(x = (DrawerSizes.touch - DrawerSizes.avatar) / 2), horizontalArrangement = Arrangement.spacedBy(DrawerSizes.dockGap),
                    verticalAlignment = Alignment.CenterVertically) {
                    DockCircle(colors.avatar, "Profile & Settings", onOpenSettings) {
                        val photo = rememberProfilePhoto(profilePhotoUrl)
                        val initial = profileName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                        if (photo != null) Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().testTag("drawer-avatar-photo"))
                        else if (initial == null) Icon(Icons.Outlined.PersonOutline, null, tint = colors.onAvatar, modifier = Modifier.size(DrawerSizes.voiceIcon))
                        else Text(initial.toString(), style = DrawerAvatarStyle, color = colors.onAvatar)
                    }
                }
            }
        }
    }

    renaming?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        var error by remember(chat.id) { mutableStateOf<String?>(null) }
        DuaDialog(
            onDismissRequest = { renaming = null },
            title = "Rename chat",
            text = {
                OutlinedTextField(title, { title = it; error = null }, singleLine = true, label = { Text("Title") }, shape = SheetShapes.badge,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SheetTheme.colors.accent, focusedLabelColor = SheetTheme.colors.accent,
                        cursorColor = SheetTheme.colors.accent, unfocusedBorderColor = SheetTheme.colors.divider,
                        focusedTextColor = SheetTheme.colors.text, unfocusedTextColor = SheetTheme.colors.text),
                    isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.testTag("rename-field"))
            },
            confirmButton = {
                SheetTextButton("Save", {
                    val result = onRenameChat(chat.id, title.trim())
                    if (result == null) renaming = null else error = result
                }, enabled = title.isNotBlank())
            },
            dismissButton = { SheetTextButton("Cancel", { renaming = null }) },
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
        if (menu) ChatOptionsSheet(chat.title.ifBlank { "New chat" }, chat.pinned, pinEnabled, serverDeletion, onDismiss = { menu = false },
            onRename = { menu = false; onRename() }, onPin = { menu = false; onPin() }, onDelete = { menu = false; onDelete() })
    }
}

@Composable
private fun DockCircle(color: androidx.compose.ui.graphics.Color, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(DrawerSizes.touch).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(DrawerSizes.avatar).clip(DrawerShapes.circle).background(color)
            .border(DrawerSizes.avatarRing, DrawerTheme.colors.avatarRing, DrawerShapes.circle), contentAlignment = Alignment.Center) { content() }
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
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DrawerSizes.menuGap),
    ) {
        Icon(icon, null, tint = colors.menuText, modifier = Modifier.size(DrawerSizes.menuIcon))
        Text(label, style = DrawerMenuStyle, color = colors.menuText)
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

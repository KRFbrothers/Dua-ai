package com.dua.ai.ui.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.library.*
import com.dua.ai.ui.chat.MediaBitmap
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.components.ShimmerBox
import com.dua.ai.ui.theme.*

/** One real inventory, presented as documents and a three-column media grid. */
@Composable
fun LibraryContent(items: List<LibraryItem>, busy: Boolean, notice: String, onBack: () -> Unit, onUpload: () -> Unit, onOpen: (LibraryItem) -> Unit,
    onRefresh: () -> Unit = {}, loading: Boolean = busy, loadFailed: Boolean = false) = DuaPageTheme {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val shown = remember(items, query, filter) { items.filter { (filter == "All" || it.kind.name == filter) && it.matches(query) } }
    val documents = remember(shown) { shown.filter { it.kind != LibraryKind.Images } }
    val mediaRows = remember(shown) { shown.filter { it.kind == LibraryKind.Images }.chunked(3) }
    val c = PageTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding().imePadding().testTag("library-feature")) {
        FeatureTopBar("Library", onBack, onUpload, newSessionEnabled = !busy, actionLabel = "Upload files")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("All", "Files", "Images", "Code").forEach { value ->
                FilterChip(filter == value, { filter = value }, label = { Text(value) }, modifier = Modifier.heightIn(min = Sizes.touchTarget))
            }
        }
        if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal = Spacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium, color = c.subText)
        TextButton(onClick = onRefresh, enabled = !busy && !loading, modifier = Modifier.padding(horizontal = Spacing.sm).heightIn(min = Sizes.touchTarget)) {
            Text(if (loading) "Refreshing files…" else if (loadFailed) "Retry cloud files" else "Refresh cloud files")
        }
        LazyColumn(Modifier.weight(1f).testTag("library-inventory"), contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when {
                loading && items.isEmpty() -> item(key = "loading", contentType = "loading") {
                    Column(Modifier.testTag("library-loading").semantics { contentDescription = "Loading library" }, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        repeat(3) { ShimmerBox(Modifier.fillMaxWidth().height(Sizes.touchTarget * 2).clip(SettingsShapes.card)) }
                    }
                }
                shown.isEmpty() -> item(key = "empty", contentType = "empty") {
                    Text(when {
                        query.isNotBlank() || items.isNotEmpty() -> "No matching library items."
                        loadFailed -> "Couldn't load your library. Retry to reconnect."
                        else -> "Your files, images and code snippets will appear here."
                    }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("library-empty"))
                }
                else -> {
                    if (documents.isNotEmpty()) {
                        item(key = "documents-heading", contentType = "heading") { LibraryHeading("Documents") }
                        items(documents, key = { it.id }, contentType = { "document" }) { item ->
                            Surface(shape = SettingsShapes.card, color = c.card, border = BorderStroke(1.dp, c.cardBorder),
                                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).testTag("library-item-${item.id}")
                                    .clickable(enabled = !busy, role = Role.Button, onClickLabel = "Open ${item.name}") { onOpen(item) }) {
                                Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    Icon(if (item.kind == LibraryKind.Code) Icons.Outlined.Code else Icons.Outlined.Description, null, tint = c.subText)
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text(item.status.ifBlank { item.content.take(180).ifBlank { item.file?.mimeType ?: item.kind.name } }, maxLines = 3,
                                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = c.subText)
                                    }
                                }
                            }
                        }
                    }
                    if (mediaRows.isNotEmpty()) {
                        item(key = "media-heading", contentType = "heading") { LibraryHeading("Media") }
                        items(mediaRows, key = { "media:" + it.joinToString { item -> item.id } }, contentType = { "media-row" }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                row.forEach { item ->
                                    Column(Modifier.weight(1f).testTag("library-item-${item.id}").clickable(enabled = !busy, role = Role.Button,
                                        onClickLabel = "Open ${item.name}") { onOpen(item) }) {
                                        val tile = Modifier.fillMaxWidth().aspectRatio(1f).clip(SheetShapes.badge)
                                        if (item.media != null) MediaBitmap(item.media, tile, maxPx = 384, imageDescription = item.name)
                                        else Box(tile.background(c.card), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, null, tint = c.subText) }
                                        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = Spacing.xs))
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search library...") }, singleLine = true,
            shape = RoundedCornerShape(Spacing.xxl), modifier = Modifier.fillMaxWidth().padding(Spacing.lg).heightIn(min = Sizes.touchTarget)
                .testTag("library-search").semantics { contentDescription = "Search library" })
    }
}

@Composable
private fun LibraryHeading(title: String) = Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })

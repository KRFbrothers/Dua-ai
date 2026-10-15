package com.dua.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dua.ai.library.LibraryItem
import com.dua.ai.ui.chat.MediaBitmap
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.theme.*

val ImageStarterPrompts = listOf("Minimalist app icon", "Futuristic Mumbai skyline", "Watercolor landscape")

@Composable
fun ImagesContent(items: List<LibraryItem>, draft: String, aspect: String, aspects: List<String>, busy: Boolean,
    available: Boolean, error: String, notice: String, onBack: () -> Unit, onDraft: (String) -> Unit,
    onAspect: (String) -> Unit, onGenerate: () -> Unit, onOpen: (LibraryItem) -> Unit, onSetup: () -> Unit,
    generating: Boolean = false, onStop: () -> Unit = {}, onRefresh: () -> Unit = {}) = DuaPageTheme {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(PageTheme.colors.background).navigationBarsPadding().imePadding().testTag("images-feature")) {
        FeatureTopBar("Images", onBack, {}, trailing = {
            Box {
                TextButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text(aspect) }
                DropdownMenu(menu, { menu = false }) {
                    aspects.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onAspect(value); menu = false }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) }
                    DropdownMenuItem(text = { Text("Refresh images") }, onClick = { onRefresh(); menu = false }, modifier = Modifier.heightIn(min = Sizes.touchTarget))
                }
            }
        })
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (items.isEmpty()) {
                item(span = { GridItemSpan(2) }) { Text("Create with Dua AI", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = Spacing.xxl)) }
                ImageStarterPrompts.forEach { prompt -> item(span = { GridItemSpan(2) }) {
                    SuggestionChip(onClick = { onDraft(prompt) }, label = { Text(prompt) }, modifier = Modifier.heightIn(min = Sizes.touchTarget))
                } }
            }
            items(items, key = { it.id }) { item ->
                Column {
                    MediaBitmap(item.media!!, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Spacing.lg))
                        .clickable(role = Role.Button, onClickLabel = "Preview ${item.name}") { onOpen(item) }, maxPx = 512,
                        imageDescription = item.content.ifBlank { item.name }.take(200), background = PageTheme.colors.fill)
                    Text(item.content.ifBlank { item.name }, maxLines = 2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
        if (generating) Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(Sizes.icon)); Text("Creating image…", Modifier.weight(1f).padding(Spacing.sm), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onStop, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Stop") }
        }
        if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal = Spacing.lg), style = MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error, Modifier.padding(horizontal = Spacing.lg), color = PageTheme.colors.danger, style = MaterialTheme.typography.bodyMedium)
        if (!available) TextButton(onClick = onSetup, modifier = Modifier.heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.sm)) { Text("Set up image generation") }
        Surface(shape = RoundedCornerShape(Spacing.xxl), color = PageTheme.colors.card, border = androidx.compose.foundation.BorderStroke(1.dp, PageTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TextField(draft, onDraft, placeholder = { Text("Describe an image to create...") }, maxLines = 4,
                    modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = PageTheme.colors.card, unfocusedContainerColor = PageTheme.colors.card,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                IconButton(onClick = onGenerate, enabled = available && !busy && draft.isNotBlank(), modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Create image")
                }
            }
        }
    }
}

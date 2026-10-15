package com.dua.ai.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dua.ai.library.*
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.theme.*

@Composable
fun LibraryContent(items: List<LibraryItem>, busy: Boolean, notice: String, onBack: () -> Unit, onUpload: () -> Unit, onOpen: (LibraryItem) -> Unit,
    onRefresh: () -> Unit = {}) = DuaPageTheme {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val shown = remember(items, query, filter) { items.filter { (filter == "All" || it.kind.name == filter) && it.matches(query) } }
    Column(Modifier.fillMaxSize().background(PageTheme.colors.background).navigationBarsPadding().imePadding().testTag("library-feature")) {
        FeatureTopBar("Library", onBack, onUpload, newSessionEnabled = !busy, actionLabel = "Upload files")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("All", "Files", "Images", "Code").forEach { value ->
                FilterChip(filter == value, { filter = value }, label = { Text(value) }, modifier = Modifier.heightIn(min = Sizes.touchTarget))
            }
        }
        if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal = Spacing.lg), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.padding(horizontal = Spacing.sm).heightIn(min = Sizes.touchTarget)) { Text("Refresh cloud files") }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (shown.isEmpty()) item { Text(if (items.isEmpty()) "Your files, images and code snippets will appear here." else "No matching library items.", style = MaterialTheme.typography.bodyLarge) }
            items(shown, key = { it.id }) { item ->
                Surface(shape = RoundedCornerShape(Spacing.lg), color = PageTheme.colors.card, border = BorderStroke(1.dp, PageTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).clickable(role = Role.Button, enabled = !busy) { onOpen(item) }) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(item.status.ifBlank { item.content.take(180).ifBlank { item.file?.mimeType ?: item.kind.name } }, maxLines = 3, style = MaterialTheme.typography.bodyMedium, color = PageTheme.colors.subText)
                    }
                }
            }
        }
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search library...") }, singleLine = true,
            shape = RoundedCornerShape(Spacing.xxl), modifier = Modifier.fillMaxWidth().padding(Spacing.lg).heightIn(min = Sizes.touchTarget))
    }
}

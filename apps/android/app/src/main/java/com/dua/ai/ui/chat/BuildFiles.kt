package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.model.BuildInfo
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

private const val COLLAPSED_CHIPS = 4

/** "Read 3 files · Dua-ai", "Reading Dua-ai…" while the server is still looking. */
internal fun buildFilesLabel(info: BuildInfo, live: Boolean): String {
    val count = info.files.size
    val files = if (count == 1) "1 file" else "$count files"
    return when {
        live && count == 0 -> "Reading ${info.shortName}…"
        live -> "Reading · $files · ${info.shortName}"
        count == 0 -> "No files read · ${info.shortName}"
        else -> "Read $files · ${info.shortName}"
    }
}

/**
 * Build mode: the repository files the server read for this reply, as chips above the answer.
 * Tapping a chip opens the file on GitHub (read-only). Long lists collapse to [COLLAPSED_CHIPS] plus "+N".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildFiles(messageId: String, info: BuildInfo, live: Boolean, modifier: Modifier = Modifier, initiallyExpanded: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val opener = remember(context) { SafeUriHandler(context) }
    var expanded by rememberSaveable(messageId) { mutableStateOf(initiallyExpanded) }
    val label = buildFilesLabel(info, live)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 32.dp).semantics(mergeDescendants = true) { heading(); contentDescription = "$label in ${info.repository}, read-only" },
            verticalAlignment = Alignment.CenterVertically) {
            if (live) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(Sizes.iconTiny))
            else Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconTiny))
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.sm))
        }
        if (info.files.isNotEmpty()) {
            val shown = if (expanded) info.files else info.files.take(COLLAPSED_CHIPS)
            FlowRow(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                shown.forEach { file ->
                    Surface(onClick = { opener.openUri(info.fileUrl(file)) }, shape = DuaShape.chip, color = scheme.surfaceContainerHigh,
                        modifier = Modifier.clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = "Open ${file.path} on GitHub" + if (file.truncated) ", only the start was read" else ""
                        }) {
                        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(Sizes.iconTiny))
                            Text(file.name + if (file.truncated) " (part)" else "", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = Spacing.xs).widthIn(max = 200.dp))
                        }
                    }
                }
                if (info.files.size > COLLAPSED_CHIPS) Surface(onClick = { expanded = !expanded }, shape = DuaShape.chip, color = scheme.secondaryContainer,
                    modifier = Modifier.clearAndSetSemantics { role = Role.Button; contentDescription = if (expanded) "Show fewer files" else "Show all ${info.files.size} files" }) {
                    Row(Modifier.heightIn(min = 32.dp).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (expanded) "Show less" else "+${info.files.size - COLLAPSED_CHIPS} more", style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

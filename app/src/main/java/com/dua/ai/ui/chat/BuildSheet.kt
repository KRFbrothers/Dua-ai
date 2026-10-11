package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.setup.BuildRepositories
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Build mode picker: choose the repository this chat is about, or see why Build mode is unavailable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSheet(current: String?, hint: String, repositories: BuildRepositories, enabled: Boolean, onSelect: (String) -> Unit, onTurnOff: () -> Unit,
    onRetry: () -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        BuildSheetContent(current, hint, repositories, enabled, onSelect, onTurnOff, onRetry, onOpenSetup, onDismiss)
    }
}

@Composable
internal fun BuildSheetContent(current: String?, hint: String, repositories: BuildRepositories, enabled: Boolean, onSelect: (String) -> Unit, onTurnOff: () -> Unit,
    onRetry: () -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(Sizes.iconSmall))
            Text("Build mode", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.sm).semantics { heading() })
        }
        Text("Dua reads the repository you pick and answers with explanations and diffs. Creating a pull request requires a separate review and confirmation.",
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.sm).padding(bottom = Spacing.sm))
        when {
            hint.isNotEmpty() -> Surface(color = scheme.surfaceContainerHighest, shape = DuaShape.card) {
                Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
                    Column(Modifier.padding(start = Spacing.md)) {
                        Text("Build mode is unavailable", style = MaterialTheme.typography.labelLarge)
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
            }
            repositories.loading -> Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(Sizes.iconSmall))
                Text("Loading your repositories…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.md))
            }
            repositories.repositories.isEmpty() -> Column(Modifier.padding(horizontal = Spacing.sm)) {
                Text(repositories.error.ifBlank { "Your repositories appear here." }, style = MaterialTheme.typography.bodyMedium, color = if (repositories.error.isBlank()) scheme.onSurfaceVariant else scheme.error)
                if (repositories.error.isNotBlank()) TextButton(onClick = onRetry) { Text("Retry") }
            }
            else -> {
                Text("Choose a repository", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.sm))
                repositories.repositories.forEach { repo ->
                    val selected = repo.fullName == current
                    Surface(color = if (selected) scheme.surfaceContainerHighest else Color.Transparent, shape = DuaShape.card) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelect(repo.fullName) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = scheme.secondaryContainer, shape = DuaShape.codeBlock) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Icon(if (repo.private) Icons.Outlined.Lock else Icons.Outlined.Folder, contentDescription = if (repo.private) "Private" else null,
                                        tint = scheme.onSecondaryContainer, modifier = Modifier.size(Sizes.iconSmall))
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                                Text(repo.fullName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (repo.description.isNotBlank()) Text(repo.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = scheme.primary)
                        }
                    }
                }
            }
        }
        Text("Works with Dua and Gemini. The server reads files with your connector token; repository reads use the read-only connector. A separate write token allows confirmed draft pull requests. Repository commands remain unavailable.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (hint.isNotEmpty()) TextButton(onClick = onOpenSetup) { Text("Feature setup") }
            else if (current != null) TextButton(onClick = onTurnOff, enabled = enabled) { Text("Turn off Build") }
            else Box(Modifier)
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

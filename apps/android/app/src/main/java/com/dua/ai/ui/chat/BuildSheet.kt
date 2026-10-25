package com.dua.ai.ui.chat

import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetSubStyle
import com.dua.ai.ui.theme.SheetRowStyle
import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
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
    DuaSheet(onDismiss = onDismiss) {
        BuildSheetContent(current, hint, repositories, enabled, onSelect, onTurnOff, onRetry, onOpenSetup, onDismiss)
    }
}

@Composable
internal fun BuildSheetContent(current: String?, hint: String, repositories: BuildRepositories, enabled: Boolean, onSelect: (String) -> Unit, onTurnOff: () -> Unit,
    onRetry: () -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    val c = SheetTheme.colors
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SheetSizes.paddingH).padding(bottom = SheetSizes.paddingBottom),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = c.accent, modifier = Modifier.size(SheetSizes.rowIcon).padding(bottom = SheetSizes.titleBottom))
            SheetTitle("Build mode", Modifier.padding(start = SheetSizes.tileGap))
        }
        Text("Dua reads the repository you pick and answers with explanations and diffs. Creating a pull request requires a separate review and confirmation.",
            style = SheetBodyStyle, color = c.subText, modifier = Modifier.padding(bottom = SheetSizes.sectionGap))
        when {
            hint.isNotEmpty() -> SheetGroup {
                Row(Modifier.fillMaxWidth().padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = c.icon, modifier = Modifier.size(SheetSizes.rowIcon))
                    Column(Modifier.padding(start = SheetSizes.rowGap)) {
                        Text("Build mode is unavailable", style = SheetRowStyle, color = c.text)
                        Text(hint, style = SheetSubStyle, color = c.subText, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
            }
            repositories.loading -> Row(Modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin).padding(horizontal = SheetSizes.rowPaddingH), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(SheetSizes.rowIcon))
                Text("Loading your repositories…", style = SheetBodyStyle, color = c.text, modifier = Modifier.padding(start = SheetSizes.rowGap))
            }
            repositories.repositories.isEmpty() -> Column {
                Text(repositories.error.ifBlank { "Your repositories appear here." }, style = SheetBodyStyle, color = if (repositories.error.isBlank()) c.subText else c.destructive)
                if (repositories.error.isNotBlank()) SheetTextButton("Retry", onClick = onRetry)
            }
            else -> {
                Text("Choose a repository", style = SheetNoteStyle, color = c.subText, modifier = Modifier.padding(bottom = Spacing.xs))
                SheetGroup {
                repositories.repositories.forEachIndexed { index, repo ->
                    val selected = repo.fullName == current
                    if (index > 0) SheetDivider()
                    Surface(color = if (selected) c.selectedFill else c.fill) {
                        Row(Modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin)
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelect(repo.fullName) }
                            .padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (repo.private) Icons.Outlined.Lock else Icons.Outlined.Folder, contentDescription = if (repo.private) "Private" else null,
                                tint = c.icon, modifier = Modifier.size(SheetSizes.rowIcon))
                            Column(Modifier.weight(1f).padding(start = SheetSizes.rowGap)) {
                                Text(repo.fullName, style = SheetRowStyle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (repo.description.isNotBlank()) Text(repo.description, style = SheetSubStyle, color = c.subText,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = c.accent)
                        }
                    }
                }
                }
            }
        }
        Text("Works with Dua and Gemini. The server reads files with your connector token; repository reads use the read-only connector. A separate write token allows confirmed draft pull requests. Repository commands remain unavailable.",
            style = SheetNoteStyle, color = c.subText, modifier = Modifier.padding(top = SheetSizes.sectionGap))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (hint.isNotEmpty()) SheetTextButton("Feature setup", onClick = onOpenSetup)
            else if (current != null) SheetTextButton("Turn off Build", onClick = onTurnOff, enabled = enabled, destructive = true)
            else Box(Modifier)
            SheetTextButton("Done", onClick = onDismiss)
        }
    }
}

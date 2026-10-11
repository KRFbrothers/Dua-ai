package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsSheet(projects: List<ChatProject>, selected: String, notice: String, enabled: Boolean,
    onSelect: (String) -> Unit, onSave: (String?, String, String, Boolean) -> Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProjectsContent(projects, selected, notice, enabled, onSelect, onSave)
    }
}

@Composable
internal fun ProjectsContent(projects: List<ChatProject>, selected: String, notice: String, enabled: Boolean,
    onSelect: (String) -> Unit, onSave: (String?, String, String, Boolean) -> Boolean) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    fun edit(project: ChatProject?) {
        editingId = project?.id; name = project?.name.orEmpty(); instructions = project?.instructions.orEmpty()
        archived = project?.archived ?: false; editing = true
    }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(if (editing) "Project settings" else "Projects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("Each project has its own chats and instructions. Sync changes before using new instructions. Personal memory is only included in Personal replies.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium)
        if (editing) {
            OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Project name") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instructions, { if (it.length <= 4000) instructions = it }, label = { Text("Custom instructions") }, minLines = 4, maxLines = 8,
                supportingText = { Text("${instructions.length}/4000 · Tone, preferred language, coding or business context") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            if (editingId != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Archived", modifier = Modifier.weight(1f))
                Switch(archived, { archived = it }, enabled = enabled)
            }
            Text("Archiving keeps chats and blocks new replies. Keep at least one project active.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = { if (onSave(editingId, name, instructions, archived)) editing = false }, enabled = enabled && name.isNotBlank()) { Text("Save project") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else {
            projects.forEach { project ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSelect(project.id) }, enabled = enabled && !project.archived, modifier = Modifier.weight(1f)) {
                        Text("${if (project.id == selected) "✓ " else ""}${project.name}${if (project.archived) " (archived)" else ""}")
                    }
                    TextButton(onClick = { edit(project) }, enabled = enabled) { Text("Edit") }
                }
            }
            TextButton(onClick = { edit(null) }, enabled = enabled && projects.size < ProjectCodec.MAX_PROJECTS) { Text("New project") }
            Text("Selecting another project starts a new chat there. Its history is available from the menu. Attached files stay with their chat.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

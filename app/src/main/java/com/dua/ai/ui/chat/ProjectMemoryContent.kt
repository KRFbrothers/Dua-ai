package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dua.ai.projects.ChatProject
import com.dua.ai.ui.theme.Spacing

/** Edits one project at a time; saving uses the latest snapshot, preserving tasks/instructions. */
@Composable
internal fun ProjectMemoryContent(project: ChatProject, notice: String, enabled: Boolean, disabledReason: String,
    onSave: (String, Boolean, List<String>) -> Boolean, onBack: () -> Unit) {
    var editing by rememberSaveable(project.id) { mutableStateOf(false) }
    var editIndex by rememberSaveable(project.id) { mutableStateOf<Int?>(null) }
    var text by rememberSaveable(project.id) { mutableStateOf("") }
    var original by rememberSaveable(project.id) { mutableStateOf("") }
    var deleteIndex by rememberSaveable(project.id) { mutableStateOf<Int?>(null) }
    var deleteOriginal by rememberSaveable(project.id) { mutableStateOf("") }
    val notes = project.memory.notes
    // If sync changes the edited row, keep the typed text but block overwriting another note.
    val editChanged = editing && editIndex?.let { notes.getOrNull(it) != original } == true
    val deleteChanged = deleteIndex?.let { notes.getOrNull(it) != deleteOriginal } == true
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(onClick = onBack) { Text("Back to projects") }
        Text("${project.name} memory", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("Notes apply only to this project's text chat, Build and hands-free Voice replies after sync. They are not shared with other projects.",
            style = MaterialTheme.typography.bodyMedium)
        if (disabledReason.isNotBlank()) Text(disabledReason, style = MaterialTheme.typography.bodyMedium)
        if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Use memory in this project", modifier = Modifier.weight(1f))
            Switch(project.memory.enabled, { onSave(project.id, it, notes) }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Use memory in ${project.name}" })
        }
        Text("${notes.size}/10 notes · 200 characters each. Turning memory off keeps the notes.", style = MaterialTheme.typography.bodySmall)
        notes.forEachIndexed { index, note ->
            Column(Modifier.fillMaxWidth()) {
                Text(note, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = { editIndex = index; text = note; original = note; editing = true }, enabled = enabled) {
                        Text("Edit note ${index + 1}")
                    }
                    TextButton(onClick = { deleteIndex = index; deleteOriginal = note }, enabled = enabled) { Text("Delete note ${index + 1}") }
                }
            }
        }
        if (notes.isEmpty()) Text("No notes saved. Add preferences or facts you want this project to remember.")
        if (editing) {
            OutlinedTextField(text, { if (it.length <= 200) text = it }, label = { Text("Memory note") }, minLines = 2, maxLines = 5,
                supportingText = { Text("${text.length}/200") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            if (editChanged) Text("This note changed during sync. Cancel and reopen it before saving.")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = {
                    val updated = if (editIndex == null) notes + text.trim() else notes.mapIndexed { i, note -> if (i == editIndex) text.trim() else note }
                    if (onSave(project.id, project.memory.enabled, updated)) editing = false
                }, enabled = enabled && text.isNotBlank() && !editChanged && (editIndex != null || notes.size < 10)) { Text("Save note") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else OutlinedButton(onClick = { editIndex = null; text = ""; original = ""; editing = true }, enabled = enabled && notes.size < 10) { Text("Add note") }
    }
    deleteIndex?.let { index ->
        AlertDialog(onDismissRequest = { deleteIndex = null }, title = { Text("Delete memory note?") },
            text = { Text(if (deleteChanged) "This note changed during sync. Cancel and review it again." else "Delete this note from ${project.name}? Sync afterwards to remove it from the server too.") },
            confirmButton = { TextButton(onClick = {
                if (onSave(project.id, project.memory.enabled, notes.filterIndexed { i, _ -> i != index })) deleteIndex = null
            }, enabled = enabled && !deleteChanged) { Text("Delete note") } },
            dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("Cancel") } })
    }
}

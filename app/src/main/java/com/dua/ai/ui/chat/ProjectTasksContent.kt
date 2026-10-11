package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.ui.theme.Spacing

@Composable
internal fun ProjectTasksContent(project: ChatProject, notice: String, enabled: Boolean, disabledReason: String,
    onSave: (String, String?, String) -> Boolean, onToggle: (String, String) -> Boolean,
    onDelete: (String, String) -> Boolean, onBack: () -> Unit) {
    var editing by rememberSaveable(project.id) { mutableStateOf(false) }
    var editingId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var title by rememberSaveable(project.id) { mutableStateOf("") }
    var deleteId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)
        .padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("${project.name} · Tasks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("${project.tasks.count { !it.completed }} open · ${project.tasks.count { it.completed }} completed. Sync changes to save them on the server.")
        if (notice.isNotBlank()) Text(notice)
        if (disabledReason.isNotBlank()) Text(disabledReason)
        if (editing) {
            OutlinedTextField(title, { if (it.length <= ProjectCodec.MAX_TASK_TITLE) title = it }, label = { Text("Task title") },
                supportingText = { Text("${title.length}/${ProjectCodec.MAX_TASK_TITLE}") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            Row {
                Button(onClick = { if (onSave(project.id, editingId, title)) editing = false }, enabled = enabled && title.isNotBlank()) { Text("Save task") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else {
            if (project.tasks.isEmpty()) Text("No tasks yet.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(project.tasks.sortedBy { it.completed }, key = { it.id }) { task ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(task.completed, { onToggle(project.id, task.id) }, enabled = enabled,
                                modifier = Modifier.semantics { contentDescription = "${if (task.completed) "Reopen" else "Complete"} task: ${task.title}" })
                            Text(task.title, modifier = Modifier.weight(1f))
                        }
                        Row {
                            TextButton(onClick = { editingId = task.id; title = task.title; editing = true }, enabled = enabled) {
                                Text("Edit", modifier = Modifier.semantics { contentDescription = "Edit task: ${task.title}" })
                            }
                            TextButton(onClick = { deleteId = task.id }, enabled = enabled) {
                                Text("Delete", modifier = Modifier.semantics { contentDescription = "Delete task: ${task.title}" })
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            Button(onClick = { editingId = null; title = ""; editing = true }, enabled = enabled && project.tasks.size < ProjectCodec.MAX_TASKS) { Text("Add task") }
            if (project.tasks.size == ProjectCodec.MAX_TASKS) Text("100-task limit reached. Delete a finished task to make space.")
        }
        TextButton(onClick = onBack) { Text("Back to projects") }
    }
    project.tasks.find { it.id == deleteId }?.let { task ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete task?") }, text = { Text(task.title) },
            confirmButton = { TextButton(enabled = enabled, onClick = { if (onDelete(project.id, task.id)) deleteId = null }) { Text("Delete task") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
    }
}

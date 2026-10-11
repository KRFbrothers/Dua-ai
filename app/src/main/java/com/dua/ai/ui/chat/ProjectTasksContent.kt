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
    onSave: (String, String?, String, Long?) -> Boolean, onToggle: (String, String) -> Boolean,
    onDelete: (String, String) -> Boolean, onBack: () -> Unit, dueDatesEnabled: Boolean = false) {
    var editing by rememberSaveable(project.id) { mutableStateOf(false) }
    var editingId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var title by rememberSaveable(project.id) { mutableStateOf("") }
    var deleteId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var dueDate by rememberSaveable(project.id) { mutableStateOf("") }
    var dueTime by rememberSaveable(project.id) { mutableStateOf("") }
    var dateError by rememberSaveable(project.id) { mutableStateOf("") }
    var editingDueAt by rememberSaveable(project.id) { mutableStateOf<Long?>(null) }
    var editingZone by rememberSaveable(project.id) { mutableStateOf(java.time.ZoneId.systemDefault().id) }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)
        .padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("${project.name} · Tasks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("${project.tasks.count { !it.completed }} open · ${project.tasks.count { it.completed }} completed. Sync changes to save them on the server.")
        if (notice.isNotBlank()) Text(notice)
        if (disabledReason.isNotBlank()) Text(disabledReason)
        if (editing) {
            OutlinedTextField(title, { if (it.length <= ProjectCodec.MAX_TASK_TITLE) title = it }, label = { Text("Task title") },
                supportingText = { Text("${title.length}/${ProjectCodec.MAX_TASK_TITLE}") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dueDate, { if (it.length <= 10) { dueDate = it; dateError = "" } }, label = { Text("Due date (YYYY-MM-DD)") },
                singleLine = true, enabled = enabled && dueDatesEnabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dueTime, { if (it.length <= 5) { dueTime = it; dateError = "" } }, label = { Text("Time (HH:mm)") },
                supportingText = { Text("Optional · 24-hour time · $editingZone") }, singleLine = true, enabled = enabled && dueDatesEnabled, modifier = Modifier.fillMaxWidth())
            if (dueDate.isNotBlank() || dueTime.isNotBlank()) TextButton(onClick = { dueDate = ""; dueTime = ""; dateError = "" }, enabled = enabled && dueDatesEnabled) { Text("Remove due date") }
            if (!dueDatesEnabled) Text("Refresh setup after updating the backend to add due dates.")
            Text("Enable reminders for this phone in Settings → Notifications. Android may delay them during battery saving.", style = MaterialTheme.typography.bodySmall)
            if (dateError.isNotBlank()) Text(dateError, color = MaterialTheme.colorScheme.error)
            Row {
                Button(onClick = {
                    try {
                        val zone = java.time.ZoneId.of(editingZone)
                        val originalDate = editingDueAt?.let { com.dua.ai.tasks.TaskDates.display(it, zone) }
                        val dueAt = if (originalDate != null && originalDate == "$dueDate $dueTime") editingDueAt else com.dua.ai.tasks.TaskDates.parse(dueDate, dueTime, zone)
                        if (onSave(project.id, editingId, title, dueAt)) editing = false
                    } catch (failure: IllegalArgumentException) { dateError = failure.message ?: "Check the due date and time." }
                }, enabled = enabled && title.isNotBlank()) { Text("Save task") }
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
                        task.dueAt?.let { Text("Due: ${com.dua.ai.tasks.TaskDates.display(it)} · ${java.time.ZoneId.systemDefault().id}", style = MaterialTheme.typography.bodySmall) }
                        Row {
                            TextButton(onClick = {
                                editingId = task.id; title = task.title; editing = true; dateError = ""
                                editingDueAt = task.dueAt; editingZone = java.time.ZoneId.systemDefault().id
                                val date = task.dueAt?.let { com.dua.ai.tasks.TaskDates.display(it, java.time.ZoneId.of(editingZone)) }.orEmpty().split(" ")
                                dueDate = date.firstOrNull().orEmpty(); dueTime = date.getOrNull(1).orEmpty()
                            }, enabled = enabled) {
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
            Button(onClick = { editingId = null; title = ""; dueDate = ""; dueTime = ""; dateError = ""; editingDueAt = null; editingZone = java.time.ZoneId.systemDefault().id; editing = true }, enabled = enabled && project.tasks.size < ProjectCodec.MAX_TASKS) { Text("Add task") }
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

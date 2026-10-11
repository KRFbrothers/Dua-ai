package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Today
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsSheet(projects: List<ChatProject>, selected: String, notice: String, enabled: Boolean,
    onSelect: (String) -> Unit, onSave: (String?, String, String, Boolean) -> Boolean, onDismiss: () -> Unit,
    tasksEnabled: Boolean = false, onSaveTask: (String, String?, String, Long?) -> Boolean = { _, _, _, _ -> false },
    onToggleTask: (String, String) -> Boolean = { _, _ -> false }, onDeleteTask: (String, String) -> Boolean = { _, _ -> false }, dueDatesEnabled: Boolean = false,
    memoryEnabled: Boolean = false, onSaveMemory: (String, Boolean, List<String>) -> Boolean = { _, _, _ -> false }) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProjectsContent(projects, selected, notice, enabled, onSelect, onSave, tasksEnabled, onSaveTask, onToggleTask, onDeleteTask, dueDatesEnabled, memoryEnabled, onSaveMemory)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectsContent(projects: List<ChatProject>, selected: String, notice: String, enabled: Boolean,
    onSelect: (String) -> Unit, onSave: (String?, String, String, Boolean) -> Boolean,
    tasksEnabled: Boolean = false, onSaveTask: (String, String?, String, Long?) -> Boolean = { _, _, _, _ -> false },
    onToggleTask: (String, String) -> Boolean = { _, _ -> false }, onDeleteTask: (String, String) -> Boolean = { _, _ -> false }, dueDatesEnabled: Boolean = false,
    memoryEnabled: Boolean = false, onSaveMemory: (String, Boolean, List<String>) -> Boolean = { _, _, _ -> false }) {
    var memoryProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    projects.find { it.id == memoryProjectId }?.let { project ->
        key("memory", project.id) {
            ProjectMemoryContent(project, notice, enabled && memoryEnabled && !project.archived,
                if (project.archived) "Restore this project to edit its memory." else if (!memoryEnabled) "Refresh setup after deploying project memory to edit notes." else "",
                onSaveMemory, onBack = { memoryProjectId = null })
        }
        return
    }
    var tasksProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var plannerOpen by rememberSaveable { mutableStateOf(false) }
    val plannerState = rememberSaveableStateHolder()
    projects.find { it.id == tasksProjectId }?.let { project ->
        key("tasks", project.id) {
            ProjectTasksContent(project, notice, enabled && tasksEnabled && !project.archived,
                if (project.archived) "Restore this project to edit its tasks." else if (!tasksEnabled) "Refresh setup after deploying the Tasks backend to edit tasks." else "",
                onSaveTask, onToggleTask, onDeleteTask, onBack = { tasksProjectId = null }, dueDatesEnabled = dueDatesEnabled,
                backLabel = if (plannerOpen) "Back to planner" else "Back to projects")
        }
        return
    }
    if (plannerOpen) {
        plannerState.SaveableStateProvider("daily-planner") {
            DailyPlannerScreen(projects, notice, enabled, tasksEnabled, onToggleTask,
                onOpenTasks = { tasksProjectId = it }, onBack = { plannerOpen = false })
        }
        return
    }
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
        Text(if (editing) "Instructions and memory stay within this project. Sync edits before using them in replies."
            else "Separate spaces for your chats, instructions and memory.",
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
            OutlinedButton(onClick = { plannerOpen = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Icon(Icons.Outlined.Today, null, Modifier.size(18.dp))
                Text("Daily planner", Modifier.padding(start = Spacing.sm))
            }
            projects.forEach { project ->
                val current = project.id == selected
                Surface(shape = RoundedCornerShape(16.dp),
                    color = if (current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onSelect(project.id) }, enabled = enabled && !project.archived,
                                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = Spacing.sm),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${project.name}${if (project.archived) " (archived)" else ""}",
                                        Modifier.weight(1f).padding(start = Spacing.sm), style = MaterialTheme.typography.titleSmall)
                                    if (current) Icon(Icons.Outlined.Check, "Selected project", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            TextButton(onClick = { edit(project) }, enabled = enabled,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Edit") }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (project.id != ProjectCodec.PERSONAL) TextButton(onClick = { memoryProjectId = project.id },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Memory · ${project.name}") }
                            TextButton(onClick = { tasksProjectId = project.id },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Tasks (${project.tasks.count { !it.completed }})") }
                        }
                    }
                }
            }
            TextButton(onClick = { edit(null) }, enabled = enabled && projects.size < ProjectCodec.MAX_PROJECTS,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text("New project", Modifier.padding(start = Spacing.sm))
            }
            Text("Switching projects starts a new chat. Personal notes are in Profile → Memory.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

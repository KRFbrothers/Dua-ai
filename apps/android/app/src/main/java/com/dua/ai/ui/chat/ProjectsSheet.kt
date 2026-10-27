package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
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
import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetRowStyle
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectsContent(projects: List<ChatProject>, selected: String, notice: String, enabled: Boolean,
    onSelect: (String) -> Unit, onSave: (String?, String, String, Boolean) -> Boolean,
    tasksEnabled: Boolean = false, onSaveTask: (String, String?, String, Long?) -> Boolean = { _, _, _, _ -> false },
    onToggleTask: (String, String) -> Boolean = { _, _ -> false }, onDeleteTask: (String, String) -> Boolean = { _, _ -> false }, dueDatesEnabled: Boolean = false,
    memoryEnabled: Boolean = false, onSaveMemory: (String, Boolean, List<String>) -> Boolean = { _, _, _ -> false },
    showTitle: Boolean = true, startCreating: Boolean = false) {
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
    var editing by rememberSaveable { mutableStateOf(startCreating) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    fun edit(project: ChatProject?) {
        editingId = project?.id; name = project?.name.orEmpty(); instructions = project?.instructions.orEmpty()
        archived = project?.archived ?: false; editing = true
    }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = SheetSizes.paddingH).padding(bottom = SheetSizes.paddingBottom), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val c = SheetTheme.colors
        if (showTitle) SheetTitle(if (editing) "Project settings" else "Projects")
        Text(if (editing) "Instructions and memory stay within this project. Sync edits before using them in replies."
            else "Separate spaces for your chats, instructions and memory.",
            style = SheetBodyStyle, color = c.subText)
        if (notice.isNotBlank()) Text(notice, style = SheetBodyStyle, color = c.text)
        if (editing) {
            OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Project name") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instructions, { if (it.length <= 4000) instructions = it }, label = { Text("Custom instructions") }, minLines = 4, maxLines = 8,
                supportingText = { Text("${instructions.length}/4000 · Tone, preferred language, coding or business context") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            if (editingId != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Archived", style = SheetRowStyle, color = c.text, modifier = Modifier.weight(1f))
                Switch(archived, { archived = it }, enabled = enabled, colors = sheetSwitchColors())
            }
            Text("Archiving keeps chats and blocks new replies. Keep at least one project active.", style = SheetNoteStyle, color = c.subText)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SheetFilledButton("Save project", onClick = { if (onSave(editingId, name, instructions, archived)) editing = false }, enabled = enabled && name.isNotBlank())
                SheetTextButton("Cancel", onClick = { editing = false })
            }
        } else {
            SheetTonalButton("Daily planner", onClick = { plannerOpen = true }, icon = Icons.Outlined.Today)
            projects.forEach { project ->
                val current = project.id == selected
                Surface(shape = SheetShapes.group, color = if (current) c.selectedFill else c.fill, contentColor = c.text, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onSelect(project.id) }, enabled = enabled && !project.archived,
                                modifier = Modifier.weight(1f).heightIn(min = SheetSizes.touch), contentPadding = PaddingValues(horizontal = Spacing.sm),
                                colors = ButtonDefaults.textButtonColors(contentColor = c.text)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(SheetSizes.rowIcon), tint = c.icon)
                                    Text("${project.name}${if (project.archived) " (archived)" else ""}",
                                        Modifier.weight(1f).padding(start = SheetSizes.tileGap), style = SheetRowStyle)
                                    if (current) Icon(Icons.Outlined.Check, "Selected project", Modifier.size(SheetSizes.rowIcon), tint = c.accent)
                                }
                            }
                            SheetTextButton("Edit", onClick = { edit(project) }, enabled = enabled)
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (project.id != ProjectCodec.PERSONAL) SheetTextButton("Memory · ${project.name}", onClick = { memoryProjectId = project.id })
                            SheetTextButton("Tasks (${project.tasks.count { !it.completed }})", onClick = { tasksProjectId = project.id })
                        }
                    }
                }
            }
            SheetGroup { SheetActionRow("New project", Icons.Outlined.Add, onClick = { edit(null) }, enabled = enabled && projects.size < ProjectCodec.MAX_PROJECTS) }
            Text("Switching projects starts a new chat. Personal notes are in Profile → Memory.",
                style = SheetNoteStyle, color = c.subText)
        }
    }
}

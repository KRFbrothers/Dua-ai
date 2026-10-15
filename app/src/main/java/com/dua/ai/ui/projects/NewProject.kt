package com.dua.ai.ui.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.dua.ai.projects.ChatProject
import com.dua.ai.ui.theme.*

@Composable internal fun NewProjectScreen(project: ChatProject? = null, notice: String, busy: Boolean, onBack: () -> Unit,
    onSave: (String, String, String, String) -> Boolean, onTools: (() -> Unit)? = null) {
    val c = ProjectTheme.colors
    var name by rememberSaveable(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var instructions by rememberSaveable(project?.id) { mutableStateOf(project?.instructions.orEmpty()) }
    var icon by rememberSaveable(project?.id) { mutableStateOf(project?.details?.icon ?: "folder") }
    var color by rememberSaveable(project?.id) { mutableStateOf(project?.details?.color ?: "purple") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var choosingBadge by rememberSaveable { mutableStateOf(false) }
    if (settings) {
        ProjectInstructionsEditor(instructions, { instructions = it; settings = false }, { settings = false })
        return
    }
    Column(Modifier.fillMaxSize()) {
        ProjectHeader(if (project == null) "New Project" else "Edit project", onBack) {
            Box(Modifier.heightIn(min = Sizes.touchTarget).clickable(role = Role.Button, onClick = { settings = true }), contentAlignment = Alignment.Center) {
                Surface(shape = ProjectShapes.settings, color = c.surface, shadowElevation = ProjectSizes.elevation) {
                    Text("Settings", Modifier.heightIn(min = ProjectSizes.settingsHeight).wrapContentHeight().padding(horizontal = Spacing.md),
                        style = MaterialTheme.typography.labelLarge.merge(ProjectTabSelectedStyle), color = c.text)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.xl)) {
            Surface(shape = ProjectShapes.name, color = c.input) {
                Row(Modifier.fillMaxWidth().heightIn(min = ProjectSizes.inputHeight).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { choosingBadge = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                            Icon(if (icon == "folder") ProjectIcons.Emoji else projectIcon(icon), "Choose project icon and color", tint = projectBadgeColors(color).second)
                        }
                        DropdownMenu(choosingBadge, { choosingBadge = false }, shape = ProjectShapes.popup, containerColor = c.surface) {
                            listOf(Triple("Folder", "folder", "purple"), Triple("Homework", "book", "blue"), Triple("Investing", "money", "teal"), Triple("Writing", "writing", "purple"), Triple("Coral folder", "folder", "coral")).forEach { (label, value, tint) ->
                                ProjectPopupItem(label, projectIcon(value)) { icon = value; color = tint; choosingBadge = false }
                            }
                        }
                    }
                    BasicTextField(name, { if (it.length <= 80) name = it }, Modifier.weight(1f).padding(end = Spacing.sm)
                        .semantics { contentDescription = "Project name" }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.merge(ProjectInputStyle).copy(color = c.text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), decorationBox = { input ->
                            Box { if (name.isEmpty()) Text("E.g. Party planning", style = MaterialTheme.typography.bodyLarge.merge(ProjectInputStyle), color = c.subText); input() }
                        })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(Triple("Homework", "book", "blue"), Triple("Investing", "money", "teal"), Triple("Writing", "writing", "purple")).forEach { (label, glyph, tint) ->
                    OutlinedButton(onClick = { name = label; icon = glyph; color = tint }, shape = ProjectShapes.chip,
                        border = BorderStroke(ProjectSizes.border, c.border), modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Icon(projectIcon(glyph), null, Modifier.size(ProjectSizes.icon), tint = if (glyph == "book" && !DuaTheme.colors.isDark) ProjectHomeworkBlueLight else projectBadgeColors(tint).second)
                        Text(label, Modifier.padding(start = Spacing.sm), style = MaterialTheme.typography.labelLarge.merge(ProjectTabStyle), color = c.text)
                    }
                }
            }
            Text("Projects give Dua AI shared context across chats and files, all in one place.",
                style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.subText)
            onTools?.let { tools -> TextButton(onClick = tools, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Tasks, memory & planner") } }
        }
        ProjectNotice(notice)
        ProjectPrimaryButton(if (project == null) "Create Project" else "Save project", enabled = !busy && name.isNotBlank(),
            onClick = { onSave(name, instructions, icon, color) }, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).navigationBarsPadding().imePadding())
    }
}

@Composable internal fun ProjectInstructionsEditor(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var instructions by rememberSaveable { mutableStateOf(initial) }
    val c = ProjectTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        ProjectHeader("Project instructions", onDismiss) {
            TextButton(onClick = { onSave(instructions) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Save", color = c.text) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg)) {
            Text("Instructions", style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.subText)
            Surface(shape = ProjectShapes.name, color = c.input) {
                BasicTextField(instructions, { if (it.length <= 4000) instructions = it },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md).semantics { contentDescription = "Project instructions" },
                    minLines = 5, maxLines = 20, textStyle = MaterialTheme.typography.bodyLarge.merge(ProjectChatRowStyle).copy(color = c.text))
            }
            Text("${instructions.length}/4000", style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.subText)
        }
    }
    androidx.activity.compose.BackHandler { onDismiss() }
}

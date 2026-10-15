package com.dua.ai.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.files.FileRef
import com.dua.ai.history.SavedChat
import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectSourceUpload
import com.dua.ai.ui.theme.*

internal enum class ProjectAction { NewChat, Pin, Edit, Instructions, AddHome, Delete }

@Composable internal fun ProjectDetailHeader(project: ChatProject, mode: String, onMode: (String) -> Unit,
    onBack: () -> Unit, onAction: (ProjectAction) -> Unit) {
    val c = ProjectTheme.colors
    var modes by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = ProjectSizes.headerPadding, vertical = Spacing.sm)
        .testTag("project-detail-header"), verticalAlignment = Alignment.CenterVertically) {
        ProjectCircle("Back", ProjectIcons.Back, onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(project.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium.merge(ProjectDetailTitleStyle),
                color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val menuWidth = minOf(maxWidth, ProjectSizes.modeMenuWidth)
                val menuOffset = androidx.compose.ui.unit.DpOffset((maxWidth - menuWidth) / 2, Spacing.xs)
                TextButton(onClick = { modes = true }, modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics { contentDescription = "Project mode, $mode" }) {
                    Text(mode, style = MaterialTheme.typography.labelLarge.merge(ProjectTabStyle), color = c.subText)
                    Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.subText)
                }
                DropdownMenu(modes, { modes = false }, modifier = Modifier.width(menuWidth), offset = menuOffset, shape = ProjectShapes.chip, containerColor = c.surface) {
                    listOf("Chat", "Work").forEach { option -> DropdownMenuItem(text = { Text(option, style = MaterialTheme.typography.labelLarge.merge(ProjectTabStyle)) },
                        leadingIcon = { if (option == mode) Icon(Icons.Outlined.Check, "Selected") else Spacer(Modifier.size(ProjectSizes.icon)) },
                        onClick = { modes = false; onMode(option) }, modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics { selected = option == mode }) }
                }
            }
        }
        Box {
            ProjectCircle("Project options", ProjectIcons.More) { menu = true }
            DropdownMenu(menu, { menu = false }, shape = ProjectShapes.popup, containerColor = c.surface) {
                val items = listOf(Triple("New project chat", ProjectIcons.NewChat, ProjectAction.NewChat),
                    Triple(if (project.details?.pinned == true) "Unpin project" else "Pin project", ProjectIcons.Pin, ProjectAction.Pin),
                    Triple("Edit project", ProjectIcons.Writing, ProjectAction.Edit), Triple("Edit instructions", ProjectIcons.Settings, ProjectAction.Instructions),
                    Triple("Add to home", ProjectIcons.Home, ProjectAction.AddHome), Triple("Delete project", ProjectIcons.Delete, ProjectAction.Delete))
                items.forEach { (label, icon, action) -> ProjectPopupItem(label, icon, danger = action == ProjectAction.Delete) { menu = false; onAction(action) } }
            }
        }
    }
}

@Composable internal fun ProjectAttachmentMenu(expanded: Boolean, onDismiss: () -> Unit,
    onCamera: () -> Unit, onPhotos: () -> Unit, onFiles: () -> Unit, onPlugins: () -> Unit) {
    val c = ProjectTheme.colors
    DropdownMenu(expanded, onDismiss, shape = ProjectShapes.attachments, containerColor = c.surface) {
        listOf(Triple("Camera", ProjectIcons.Camera, onCamera), Triple("Photos", ProjectIcons.Photos, onPhotos),
            Triple("Files", ProjectIcons.File, onFiles), Triple("Plugins", ProjectIcons.Plugins, onPlugins)).forEach { (label, icon, action) ->
            DropdownMenuItem(text = { Text(label, style = MaterialTheme.typography.bodyLarge.merge(ProjectChatRowStyle), color = c.text) },
                leadingIcon = { Surface(Modifier.size(ProjectSizes.smallCircle), androidx.compose.foundation.shape.CircleShape, color = c.attachmentIconBackground) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(ProjectSizes.icon), tint = c.text) }
                } }, onClick = { onDismiss(); action() }, modifier = Modifier.heightIn(min = Sizes.touchTarget))
        }
    }
}

@Composable internal fun ProjectDetailScreen(project: ChatProject, chats: List<SavedChat>, draft: String, uploads: List<ProjectSourceUpload>,
    busy: Boolean, notice: String, mode: String, onMode: (String) -> Unit, onBack: () -> Unit, onAction: (ProjectAction) -> Unit,
    onOpenChat: (String) -> Unit, onAddSources: () -> Unit, onRemoveSource: (FileRef) -> Unit,
    onRetryUpload: (String) -> Unit, onDismissUpload: (String) -> Unit, onOpenSource: (FileRef) -> Unit,
    onDraft: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit, onVoice: () -> Unit,
    onCamera: () -> Unit, onPhotos: () -> Unit, onFiles: () -> Unit, onPlugins: () -> Unit,
    readOnly: Boolean = false, attachmentPreview: @Composable () -> Unit = {}) {
    var tab by rememberSaveable(project.id) { mutableStateOf("Chats") }
    var attachments by remember { mutableStateOf(false) }
    val c = ProjectTheme.colors
    Column(Modifier.fillMaxSize().testTag("project-detail")) {
        ProjectDetailHeader(project, mode, onMode, onBack, onAction)
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("Chats", "Sources").forEach { name -> ProjectChip(name, tab == name, { tab = name }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                mode == "Work" -> Text("Work is coming soon", Modifier.align(Alignment.Center).padding(Spacing.lg), color = c.subText, style = MaterialTheme.typography.bodyLarge.merge(ProjectRowStyle))
                tab == "Chats" -> {
                    val actual = chats.filter { it.projectId == project.id }.sortedByDescending { it.updatedAt }
                    if (actual.isEmpty()) Text("No chats yet", Modifier.align(Alignment.Center), color = c.subText, style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle))
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(actual, key = { it.id }) { chat ->
                            Column(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).clickable(enabled = !busy, role = Role.Button) { onOpenChat(chat.id) }
                                .padding(vertical = Spacing.sm)) {
                                Text(chat.title, color = c.text, style = MaterialTheme.typography.bodyLarge.merge(ProjectChatRowStyle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val last = chat.messages.lastOrNull()?.text?.replace('\n', ' ') ?: chat.draft
                                if (last.isNotBlank()) Text(last, color = c.subText, style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                else -> ProjectSources(project.sources, uploads.filter { it.projectId == project.id }, busy || readOnly, onAddSources, onOpenSource,
                    onRemoveSource, onRetryUpload, onDismissUpload)
            }
        }
        ProjectNotice(if (readOnly) "This project is archived. Restore it in project settings to start a new chat." else notice)
        if (mode == "Chat") {
            attachmentPreview()
            ProjectComposer(draft, !busy && !readOnly, onDraft, onSend, onAttach = { attachments = true }, onMic, onVoice,
                attachmentPopup = { ProjectAttachmentMenu(attachments, { attachments = false }, onCamera, onPhotos, onFiles, onPlugins) })
        }
    }
}

@Composable private fun ProjectSources(sources: List<FileRef>, uploads: List<ProjectSourceUpload>, busy: Boolean,
    onAdd: () -> Unit, onOpen: (FileRef) -> Unit, onRemove: (FileRef) -> Unit, onRetry: (String) -> Unit, onDismiss: (String) -> Unit) {
    val c = ProjectTheme.colors
    Column(Modifier.fillMaxSize()) {
        if (sources.isEmpty() && uploads.none { !it.complete }) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport).padding(Spacing.xl),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            ProjectSourcesIllustration()
            Text("Give Dua AI more context", style = MaterialTheme.typography.titleMedium.merge(ProjectEmptyTitleStyle), color = c.text, textAlign = TextAlign.Center)
            Text("Upload Sources to give Dua AI deeper context about your project.", Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
                style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.subText, textAlign = TextAlign.Center)
            ProjectPrimaryButton("Add", enabled = !busy, onClick = onAdd)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
            items(sources, key = { it.path }) { source ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onOpen(source) }, modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget)) {
                        Icon(ProjectIcons.File, null, tint = c.subText)
                        Text(source.name, Modifier.padding(start = Spacing.sm), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton({ onRemove(source) }, enabled = !busy, modifier = Modifier.size(Sizes.touchTarget)) { Icon(ProjectIcons.Delete, "Remove ${source.name}", tint = c.danger) }
                }
            }
            items(uploads.filterNot { it.complete }, key = { it.id }) { upload ->
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                    Text(upload.name, style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.text)
                    if (upload.error != null) {
                        Text(upload.error, style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.danger)
                        Row { TextButton({ onRetry(upload.id) }) { Text("Retry") }; TextButton({ onDismiss(upload.id) }) { Text("Dismiss") } }
                    } else LinearProgressIndicator(progress = { upload.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm))
                }
            }
            item { ProjectPrimaryButton("Add", enabled = !busy, onClick = onAdd) }
        }
    }
}

/** Decorative source cards do not advertise connected Notion/Drive integrations. */
@Composable private fun ProjectSourcesIllustration() {
    val c = ProjectTheme.colors
    val gold = DuaTheme.colors.sparkle
    Box(Modifier.size(ProjectSizes.fanWidth, ProjectSizes.fanHeight).semantics { contentDescription = "Project source files illustration" }, contentAlignment = Alignment.Center) {
        listOf(-1, 1, 0).forEach { card ->
            Surface(Modifier.offset(x = ProjectSizes.fanCardWidth * (card * 0.7f)).size(ProjectSizes.fanCardWidth, ProjectSizes.fanCardHeight)
                .graphicsLayer { rotationZ = card * 8f }, shape = ProjectShapes.badge, color = c.surface, shadowElevation = ProjectSizes.searchElevation) {
                Box(contentAlignment = Alignment.Center) {
                    when (card) {
                        -1 -> Text("N", style = MaterialTheme.typography.headlineLarge, color = c.text)
                        0 -> androidx.compose.foundation.Canvas(Modifier.size(ProjectSizes.smallCircle)) {
                            val path = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * .35f, 0f); lineTo(size.width * .65f, 0f); lineTo(size.width, size.height * .65f); lineTo(size.width * .7f, size.height * .65f); close() }
                            drawPath(path, c.teal)
                            val blue = androidx.compose.ui.graphics.Path().apply { moveTo(size.width, size.height * .65f); lineTo(size.width * .85f, size.height * .94f); lineTo(size.width * .15f, size.height * .94f); lineTo(size.width * .3f, size.height * .65f); close() }
                            drawPath(blue, c.blue)
                            val yellow = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * .35f, 0f); lineTo(size.width * .5f, size.height * .28f); lineTo(size.width * .15f, size.height * .94f); lineTo(0f, size.height * .65f); close() }
                            drawPath(yellow, gold)
                        }
                        else -> Icon(ProjectIcons.File, null, Modifier.size(ProjectSizes.smallCircle), tint = c.text)
                    }
                }
            }
        }
    }
}

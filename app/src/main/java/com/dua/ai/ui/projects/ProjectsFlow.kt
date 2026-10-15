package com.dua.ai.ui.projects

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.dua.ai.attachments.CameraCaptureStore
import com.dua.ai.files.FileRef
import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.ui.chat.ProjectsContent
import com.dua.ai.ui.theme.*
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

internal enum class ProjectPage { List, New, Detail, Edit, Tools }

/** Real cached/server project definitions and chats; no preview/demo rows enter this route. */
@Composable
internal fun ProjectsFeatureFlow(viewModel: ChatViewModel, onBack: () -> Unit, onOpenChat: () -> Unit,
    onPlugins: () -> Unit, onMic: () -> Unit, onVoice: () -> Unit,
    requestedProject: String? = null, requestedPage: String? = null, onRequestConsumed: () -> Unit = {}) {
    val preferences by viewModel.localSettings.collectAsState()
    val chats by viewModel.savedChats.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val pendingUploads by viewModel.uploads.collectAsState()
    val reading by viewModel.readingFiles.collectAsState()
    val attachmentError by viewModel.attachmentError.collectAsState()
    val busyData by viewModel.dataBusy.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val sourceUploads by viewModel.projectSourceUploads.collectAsState()
    val projectNotice by viewModel.projectNotice.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    var page by rememberSaveable { mutableStateOf(ProjectPage.List) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf("Chat") }
    var localNotice by rememberSaveable { mutableStateOf("") }
    var instructions by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var removingSource by remember { mutableStateOf<FileRef?>(null) }
    val project = preferences.projects.find { it.id == selectedId && it.details?.deleted != true }
    val busy = typing || busyData
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captures = remember { CameraCaptureStore(context.cacheDir) }
    var capture by rememberSaveable { mutableStateOf("") }
    var pickingSources by rememberSaveable { mutableStateOf(false) }
    var sourceProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    fun accept(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        if (pickingSources) sourceProjectId?.let { viewModel.uploadProjectSources(it, uris) }
        else viewModel.selectAttachments(uris)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { accept(it) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { accept(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = capture; capture = ""
        if (name.isNotBlank()) {
            if (ok) {
                val file = captures.find(name)
                if (pickingSources) selectedId?.let { viewModel.uploadProjectSources(it, listOf(FileProvider.getUriForFile(context, "${context.packageName}.camera", file))) }
                else viewModel.selectCameraFile(file)
            } else runCatching { captures.discard(name) }
        }
    }
    fun camera() {
        pickingSources = false
        try {
            val file = captures.create(); capture = file.name
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.camera", file))
        } catch (_: Exception) { localNotice = "Camera is unavailable. Choose Photos or Files." }
    }
    fun openProject(id: String) {
        if (busy) return
        val selected = viewModel.localSettings.value.projects.find { it.id == id && it.details?.deleted != true } ?: return
        if (!selected.archived) {
            viewModel.selectProject(id)
            if (viewModel.projectId.value != id) return
            // Browsing a project prepares a fresh draft without modifying its saved conversations.
            viewModel.startNewChat(); viewModel.selectAuto()
        }
        selectedId = id; mode = "Chat"; localNotice = ""; page = ProjectPage.Detail
    }
    LaunchedEffect(requestedProject, requestedPage) {
        if (requestedPage == "list") { page = ProjectPage.List; selectedId = null; onRequestConsumed() }
        else if (requestedProject != null && preferences.projects.any { it.id == requestedProject && it.details?.deleted != true }) {
            selectedId = requestedProject
            page = if (requestedPage == "edit") ProjectPage.Edit else ProjectPage.Detail
            if (requestedPage == "instructions") instructions = true
            if (requestedPage == "work") mode = "Work"
            onRequestConsumed()
        }
    }
    fun back() {
        if (busyData) return
        localNotice = ""
        when (page) {
            ProjectPage.List -> onBack()
            ProjectPage.New -> page = ProjectPage.List
            ProjectPage.Edit, ProjectPage.Tools -> page = ProjectPage.Detail
            ProjectPage.Detail -> page = ProjectPage.List
        }
    }
    BackHandler(enabled = page != ProjectPage.List && !busyData) { back() }
    fun action(action: ProjectAction) {
        val current = project ?: return
        when (action) {
            ProjectAction.NewChat -> { if (!busy && !current.archived) { viewModel.startNewChat(); viewModel.selectAuto(); onOpenChat() } }
            ProjectAction.Pin -> viewModel.pinProject(current.id)
            ProjectAction.Edit -> page = ProjectPage.Edit
            ProjectAction.Instructions -> instructions = true
            ProjectAction.AddHome -> localNotice = ProjectShortcuts.pin(context, current)
            ProjectAction.Delete -> deleting = true
        }
    }
    if (instructions && project != null) {
        ProjectInstructionsEditor(project.instructions, {
        if (viewModel.updateProjectPresentation(project.id, project.name, it, project.details?.icon ?: "folder", project.details?.color ?: "purple")) instructions = false
    }, { instructions = false })
        return
    }
    Column(Modifier.fillMaxSize().background(ProjectTheme.colors.background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        when {
            page == ProjectPage.List -> ProjectsList(preferences.projects, chats, busy, localNotice, ::back, onNew = { page = ProjectPage.New }, onOpen = ::openProject)
            page == ProjectPage.New -> NewProjectScreen(notice = localNotice.ifBlank { projectNotice }, busy = busy, onBack = ::back,
                onSave = { name, instructions, icon, color ->
                    val id = viewModel.createProject(name, instructions, icon, color)
                    if (id != null) openProject(id)
                    id != null
                })
            project == null -> { ProjectHeader("Projects", ::back) {}; ProjectNotice("This project is no longer available.") }
            page == ProjectPage.Edit -> NewProjectScreen(project, localNotice.ifBlank { projectNotice }, busy, ::back,
                onSave = { name, instructions, icon, color -> viewModel.updateProjectPresentation(project.id, name, instructions, icon, color).also { if (it) page = ProjectPage.Detail } },
                onTools = { page = ProjectPage.Tools })
            page == ProjectPage.Tools -> {
                ProjectHeader(project.name, ::back) {}
                ProjectsContent(listOf(project), project.id, projectNotice, !busy, {}, viewModel::saveProject,
                    tasksEnabled = integrations.projectTasksEnabled, onSaveTask = viewModel::saveTask, onToggleTask = viewModel::toggleTask,
                    onDeleteTask = viewModel::deleteTask, dueDatesEnabled = integrations.taskDueDatesEnabled, memoryEnabled = integrations.projectMemoryEnabled,
                    onSaveMemory = viewModel::saveProjectMemory)
            }
            else -> ProjectDetailScreen(project, chats, draft, sourceUploads, busy, localNotice.ifBlank { projectNotice }, mode, { mode = it }, ::back, ::action,
                onOpenChat = { id -> if (!busy && chats.any { it.id == id && it.projectId == project.id }) { viewModel.openChat(id); onOpenChat() } },
                onAddSources = { pickingSources = true; sourceProjectId = project.id; files.launch(arrayOf("*/*")) },
                onRemoveSource = { removingSource = it }, onRetryUpload = viewModel::retryProjectSourceUpload, onDismissUpload = viewModel::dismissProjectSourceUpload,
                onOpenSource = { ref -> scope.launch { try { val url = viewModel.storedFileUrl(ref); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (_: Exception) { localNotice = "Source could not be opened. Check your connection and try again." } } },
                onDraft = viewModel::updateDraft, onSend = { if (!busy) { viewModel.sendMessage(draft); onOpenChat() } }, onMic = onMic, onVoice = onVoice, readOnly = project.archived,
                onCamera = ::camera, onPhotos = { pickingSources = false; photos.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFiles = { pickingSources = false; files.launch((com.dua.ai.attachments.AttachmentReader.types + com.dua.ai.files.StoredFileTypes.PICKER_EXTRA)) }, onPlugins = onPlugins,
                attachmentPreview = {
                    if (reading || attachmentError.isNotBlank()) ProjectNotice(if (reading) "Reading files…" else attachmentError)
                    if (attachments.isNotEmpty() || pendingUploads.isNotEmpty()) {
                        ProjectNotice((attachments.map { it.name } + pendingUploads.map { it.name }).joinToString(", "))
                        TextButton(viewModel::clearAttachments) { Text("Remove attachments") }
                    }
                })
        }
    }
    if (deleting && project != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete project?") },
        text = { Text("Remove “${project.name}” from Projects? Its chats and stored files stay available in History and Data controls.") },
        confirmButton = { TextButton({ if (viewModel.deleteProject(project.id)) { deleting = false; page = ProjectPage.List; selectedId = null } }) { Text("Delete project", color = ProjectTheme.colors.danger) } },
        dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } })
    removingSource?.let { ref -> AlertDialog(onDismissRequest = { removingSource = null }, title = { Text("Remove source?") },
        text = { Text("Remove this file from project context? The stored file is kept in Data controls.") },
        confirmButton = { TextButton({ if (project != null && viewModel.removeProjectSource(project.id, ref.path)) removingSource = null }) { Text("Remove") } },
        dismissButton = { TextButton({ removingSource = null }) { Text("Cancel") } }) }
}

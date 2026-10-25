package com.dua.ai.viewmodel

import android.app.Application
import android.net.Uri
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.files.FileRef
import com.dua.ai.files.SignedUpload
import com.dua.ai.files.StoredFileTypes
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ChatViewModel state and actions the Projects feature reads and drives. Implemented by ChatViewModel. */
internal interface ProjectsHost {
    val viewModelScope: CoroutineScope
    fun <T : Application> getApplication(): T
    val account: com.dua.ai.account.AccountRepository
    val localSettings: StateFlow<com.dua.ai.settings.LocalPreferences>
    val localPreferences: com.dua.ai.settings.DevicePreferences
    val fileClient: com.dua.ai.files.StoredFileClient
    val separateChatEngine: Boolean
    val sourceRequests: MutableMap<String, Pair<String, Uri>>
    val _projectSourceUploads: MutableStateFlow<List<com.dua.ai.projects.ProjectSourceUpload>>
    val _projectId: MutableStateFlow<String>
    val _projectNotice: MutableStateFlow<String>
    val _savedChats: MutableStateFlow<List<com.dua.ai.history.SavedChat>>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _readingFiles: MutableStateFlow<Boolean>
    val _uploads: MutableStateFlow<List<com.dua.ai.files.PendingUpload>>
    val _attachments: MutableStateFlow<List<com.dua.ai.attachments.ChatAttachment>>
    val _recordingSince: MutableStateFlow<Long?>
    fun projectMemory(): String
    fun syncBlocked(): Boolean
    fun startNewChat()
    fun selectAuto()
    fun maxStoredBytes(): Long
    fun copyCapped(uri: Uri, limit: Long): File?
    fun deleteStoredPaths(paths: Collection<String>)
    suspend fun storedFileUrl(ref: FileRef): String
}

internal data class EngineProjectContext(val memory: String, val attachments: List<com.dua.ai.attachments.ChatAttachment>, val ownerId: String? = null)

/** Projects, project tasks, project memory and project sources, moved out of ChatViewModel.kt unchanged. */
internal class ChatProjects(host: ProjectsHost) : ProjectsHost by host {

    fun selectProject(id: String) {
        if (syncBlocked() || _readingFiles.value || _uploads.value.isNotEmpty() || _attachments.value.isNotEmpty() || _recordingSince.value != null) {
            _projectNotice.value = "Finish the current action and send or remove pending attachments before switching projects."; return
        }
        if (localSettings.value.projects.none { it.id == id && !it.archived }) return
        if (id == _projectId.value) return
        startNewChat()
        _projectId.value = id
        _projectNotice.value = ""
    }

    private fun commitProjects(projects: List<com.dua.ai.projects.ChatProject>): Boolean {
        if (syncBlocked()) { _projectNotice.value = "Finish the current action before editing projects."; return false }
        return try {
            localPreferences.requireReadable()
            com.dua.ai.projects.ProjectCodec.validate(projects)
            com.dua.ai.account.CloudSnapshotCodec.encode(CloudSnapshot(_savedChats.value, localSettings.value.memory, projects))
            localPreferences.update(localSettings.value.copy(projects = projects))
            _projectNotice.value = "Project saved. Changes follow your account through Cloud Sync."
            true
        } catch (failure: Exception) { _projectNotice.value = failure.message ?: "Project could not be saved."; false }
    }

    fun saveProject(id: String?, name: String, instructions: String, archived: Boolean): Boolean {
        val existing = localSettings.value.projects.find { it.id == id }
        if (id != null && existing == null) { _projectNotice.value = "Project changed. Reopen the editor."; return false }
        val now = System.currentTimeMillis()
        val original = existing ?: com.dua.ai.projects.ChatProject(UUID.randomUUID().toString(), name.trim(),
            details = com.dua.ai.projects.ProjectDetails(now, now))
        val updated = original.copy(name = name.trim(), instructions = instructions.trim(), archived = archived,
            details = original.details?.copy(updatedAt = now))
        return commitProjects(if (existing == null) localSettings.value.projects + updated else localSettings.value.projects.map { if (it.id == id) updated else it })
    }

    fun createProject(name: String, instructions: String, icon: String, color: String): String? {
        val now = System.currentTimeMillis()
        val project = com.dua.ai.projects.ChatProject(UUID.randomUUID().toString(), name.trim(), instructions.trim(),
            details = com.dua.ai.projects.ProjectDetails(now, now, icon, color))
        return project.id.takeIf { commitProjects(localSettings.value.projects + project) }
    }
    fun updateProjectPresentation(id: String, name: String, instructions: String, icon: String, color: String): Boolean =
        changeProject(id) { p -> p.copy(name = name.trim(), instructions = instructions.trim(),
            details = projectDetails(p).copy(icon = icon, color = color, updatedAt = System.currentTimeMillis())) }
    private fun projectDetails(project: com.dua.ai.projects.ChatProject) = project.details ?:
        com.dua.ai.projects.ProjectDetails(_savedChats.value.filter { it.projectId == project.id }.minOfOrNull { it.updatedAt } ?: System.currentTimeMillis(), System.currentTimeMillis())
    private fun changeProject(id: String, change: (com.dua.ai.projects.ChatProject) -> com.dua.ai.projects.ChatProject): Boolean {
        val project = localSettings.value.projects.find { it.id == id && it.details?.deleted != true }
            ?: run { _projectNotice.value = "Project is no longer available."; return false }
        return try { commitProjects(localSettings.value.projects.map { if (it.id == id) change(project) else it }) }
        catch (failure: Exception) { _projectNotice.value = failure.message ?: "Project could not be saved."; false }
    }
    fun pinProject(id: String): Boolean = changeProject(id) { p ->
        val details = projectDetails(p); p.copy(details = details.copy(pinned = !details.pinned, updatedAt = System.currentTimeMillis()))
    }
    /** Retain the authoritative definition and chats/files; hidden tombstones cannot be resurrected by stale clients. */
    fun deleteProject(id: String): Boolean {
        if (id == com.dua.ai.projects.ProjectCodec.PERSONAL) return false
        val saved = changeProject(id) { p -> p.copy(archived = true, details = projectDetails(p).copy(deleted = true, pinned = false, updatedAt = System.currentTimeMillis())) }
        if (saved && _projectId.value == id) { startNewChat(); selectAuto() }
        return saved
    }
    fun removeProjectSource(projectId: String, path: String): Boolean = changeProject(projectId) { p ->
        p.copy(sources = p.sources.filterNot { it.path == path }, details = projectDetails(p).copy(updatedAt = System.currentTimeMillis()))
    }
    fun retryProjectSourceUpload(id: String) { sourceRequests[id]?.let { (projectId, uri) -> dismissProjectSourceUpload(id); uploadProjectSources(projectId, listOf(uri)) } }
    fun dismissProjectSourceUpload(id: String) { sourceRequests.remove(id); _projectSourceUploads.update { it.filterNot { upload -> upload.id == id } } }
    fun uploadProjectSources(projectId: String, uris: List<Uri>) {
        if (syncBlocked() || account.state.value.user == null || !_integrations.value.filesEnabled) {
            _projectNotice.value = "Sign in and finish private file storage setup to add project sources."; return
        }
        val project = localSettings.value.projects.find { it.id == projectId && !it.archived } ?: return
        if (_projectSourceUploads.value.any { it.projectId == projectId && !it.complete && it.error == null }) return
        val count = com.dua.ai.projects.ProjectCodec.MAX_SOURCES - project.sources.size
        if (count <= 0) { _projectNotice.value = "Keep at most 3 project sources. Remove one before adding another."; return }
        val ownerId = account.state.value.user!!.id
        val resolver = getApplication<Application>().contentResolver
        uris.take(count).forEach { uri ->
            val uploadId = UUID.randomUUID().toString()
            sourceRequests[uploadId] = projectId to uri
            _projectSourceUploads.update { it + com.dua.ai.projects.ProjectSourceUpload(uploadId, projectId, "Source") }
            viewModelScope.launch {
                var uploadedPath: String? = null
                fun update(change: (com.dua.ai.projects.ProjectSourceUpload) -> com.dua.ai.projects.ProjectSourceUpload) {
                    _projectSourceUploads.update { list -> list.map { if (it.id == uploadId) change(it) else it } }
                }
                var temp: File? = null
                try {
                    val info = withContext(Dispatchers.IO) {
                        var name = "Source"; var size: Long? = null
                        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) { name = cursor.getString(0).orEmpty().take(200); if (!cursor.isNull(1)) size = cursor.getLong(1) }
                        }
                        Triple(name, resolver.getType(uri).orEmpty(), size)
                    }
                    val type = StoredFileTypes.storedType(info.first, info.second) ?: if (com.dua.ai.attachments.TextFiles.isText(info.first, info.second)) "text/plain" else error("Choose a supported image, PDF, text or document file.")
                    val limit = if (separateChatEngine) com.dua.ai.attachments.AttachmentReader.MAX_BYTES.toLong() else maxStoredBytes()
                    temp = withContext(Dispatchers.IO) { copyCapped(uri, limit) } ?: error("Source exceeds the file size limit.")
                    val size = temp!!.length()
                    if (separateChatEngine && com.dua.ai.attachments.TextFiles.isText(info.first, type) && size > com.dua.ai.attachments.TextFiles.MAX_BYTES) error("Cloud Run text sources must be 256 KB or smaller.")
                    if (separateChatEngine && type !in setOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain", "text/markdown", "application/json", "text/csv"))
                        error("Use PDF, images, CSV or UTF-8 text with the Cloud Run engine.")
                    if (separateChatEngine && project.sources.sumOf { it.size } + size > com.dua.ai.attachments.AttachmentReader.MAX_BYTES) error("Cloud Run project sources must fit within 2 MB total.")
                    update { it.copy(name = info.first, total = size) }
                    val signed = SignedUpload.parse(account.request("/api/files/sign", "POST", org.json.JSONObject().put("name", info.first).put("mimeType", type).put("size", size), authenticated = true))
                    check(signed.path.substringBefore('/') == ownerId.lowercase()) { "Source upload belongs to another account." }
                    uploadedPath = signed.path
                    withContext(Dispatchers.IO) { fileClient.upload(signed, size, { temp!!.inputStream() }) { sent -> update { it.copy(sent = sent) } } }
                    check(account.state.value.user?.id == ownerId) { "Account changed. Source was not added." }
                    val ref = FileRef(signed.path, info.first, signed.mimeType.ifBlank { type }, size)
                    check(changeProject(projectId) { p -> check(!p.archived); if (separateChatEngine) check(p.sources.sumOf { it.size } + ref.size <= com.dua.ai.attachments.AttachmentReader.MAX_BYTES) { "Cloud Run project sources must fit within 2 MB total." }; p.copy(sources = p.sources + ref, details = projectDetails(p).copy(updatedAt = System.currentTimeMillis())) }) { _projectNotice.value }
                    uploadedPath = null
                    sourceRequests.remove(uploadId)
                    update { it.copy(sent = size, complete = true) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { update { it.copy(error = failure.message ?: "Source upload failed. Try again.") } }
                finally { temp?.delete(); uploadedPath?.let { if (account.state.value.user?.id == ownerId) deleteStoredPaths(listOf(it)) } }
            }
        }
    }

    internal suspend fun engineProjectContext(projectId: String): EngineProjectContext {
        if (!separateChatEngine) return EngineProjectContext(projectMemory(), emptyList())
        val settings = localSettings.value
        val id = projectId
        val cached = settings.projects.find { it.id == id } ?: error("Restore project settings from cloud history before chatting.")
        // Plain Home chat does not acquire an unrelated base-backend provider/configuration gate.
        if (id == com.dua.ai.projects.ProjectCodec.PERSONAL && !settings.memoryEnabled && cached.instructions.isBlank() && cached.sources.isEmpty())
            return EngineProjectContext("", emptyList())
        val ownerId = account.state.value.user?.id ?: error("Sign in to load this project's server context.")
        val remote = account.cloud()
        check(remote.ownerId == ownerId && account.state.value.user?.id == ownerId) { "Account changed. Reopen the project and retry." }
        val context = com.dua.ai.projects.ProjectRequestContextPolicy.resolve(id, cached, remote.snapshot, settings.memoryEnabled)
        val result = mutableListOf<com.dua.ai.attachments.ChatAttachment>()
        var bytes = 0L
        for (source in context.sources) {
            check(source.path.substringBefore('/') == ownerId.lowercase()) { "A project source belongs to another account." }
            bytes += source.size
            check(bytes <= com.dua.ai.attachments.AttachmentReader.MAX_BYTES) { "Project sources exceed Cloud Run's 2 MB context limit. Remove a source and retry." }
            val url = storedFileUrl(source)
            result += fileClient.readAttachment(url, source.name, source.mimeType)
        }
        check(account.state.value.user?.id == ownerId) { "Account changed. Reopen the project and retry." }
        return EngineProjectContext(context.memory, result, ownerId)
    }

    private fun changeTasks(projectId: String, change: (List<com.dua.ai.projects.ProjectTask>) -> List<com.dua.ai.projects.ProjectTask>): Boolean {
        if (syncBlocked()) { _projectNotice.value = "Finish the current action before editing tasks."; return false }
        if (!_integrations.value.projectTasksEnabled) { _projectNotice.value = "Refresh setup after deploying the Tasks backend before editing tasks."; return false }
        return try {
            localPreferences.requireReadable()
            val settings = localSettings.value
            val project = settings.projects.find { it.id == projectId } ?: error("Project changed. Reopen Tasks.")
            check(!project.archived) { "Restore this project before editing tasks." }
            val updated = project.copy(tasks = change(project.tasks))
            val next = settings.projects.map { if (it.id == projectId) updated else it }
            com.dua.ai.account.CloudSnapshotCodec.encode(CloudSnapshot(_savedChats.value, settings.memory, next))
            localPreferences.update(settings.copy(projects = next))
            _projectNotice.value = "Task saved to the cache. Sync to save it on the server."
            true
        } catch (failure: Exception) { _projectNotice.value = failure.message ?: "Task could not be saved."; false }
    }

    fun saveTask(projectId: String, taskId: String?, title: String, dueAt: Long? = null): Boolean = changeTasks(projectId) { tasks ->
        check(dueAt == null || _integrations.value.taskDueDatesEnabled) { "Refresh setup after updating the backend before saving due dates." }
        val existing = taskId?.let { id -> tasks.find { it.id == id } ?: error("Task changed. Reopen the editor.") }
        val task = existing?.copy(title = title.trim(), dueAt = dueAt) ?: com.dua.ai.projects.ProjectTask(UUID.randomUUID().toString(), title.trim(), dueAt = dueAt)
        if (existing == null) tasks + task else tasks.map { if (it.id == taskId) task else it }
    }
    fun toggleTask(projectId: String, taskId: String): Boolean = changeTasks(projectId) { tasks ->
        check(tasks.any { it.id == taskId }) { "Task changed. Reopen Tasks." }
        tasks.map { if (it.id == taskId) it.copy(completed = !it.completed) else it }
    }
    fun deleteTask(projectId: String, taskId: String): Boolean = changeTasks(projectId) { tasks ->
        check(tasks.any { it.id == taskId }) { "Task changed. Reopen Tasks." }
        tasks.filterNot { it.id == taskId }
    }

    fun saveProjectMemory(projectId: String, enabled: Boolean, notes: List<String>): Boolean {
        if (syncBlocked()) { _projectNotice.value = "Finish the current action before editing memory."; return false }
        if (!_integrations.value.projectMemoryEnabled) { _projectNotice.value = "Refresh setup after deploying the project memory backend."; return false }
        return try {
            localPreferences.requireReadable()
            val settings = localSettings.value
            val project = settings.projects.find { it.id == projectId } ?: error("Project changed. Reopen Memory.")
            check(projectId != com.dua.ai.projects.ProjectCodec.PERSONAL) { "Use Settings → Memory for Personal notes." }
            check(!project.archived) { "Restore this project before editing memory." }
            val memory = com.dua.ai.projects.ProjectMemory(enabled, notes.map { it.trim() })
            val next = settings.projects.map { if (it.id == projectId) it.copy(memory = memory) else it }
            com.dua.ai.account.CloudSnapshotCodec.encode(CloudSnapshot(_savedChats.value, settings.memory, next))
            localPreferences.update(settings.copy(projects = next))
            _projectNotice.value = "Memory saved to the cache. Sync to apply it on the server."
            true
        } catch (failure: Exception) { _projectNotice.value = failure.message ?: "Memory could not be saved."; false }
    }

    internal fun projectUnavailable(): String? {
        val project = localSettings.value.projects.find { it.id == _projectId.value }
            ?: return "This project's settings are missing. Restore them from the server before chatting."
        if (project.archived) return "This project is archived. Restore it in Projects before chatting."
        if (project.memory != com.dua.ai.projects.ProjectMemory() && !_integrations.value.projectMemoryEnabled) return "Refresh Feature setup after deploying the project memory backend before chatting here."
        if (!_integrations.value.projectsEnabled && (project.id != "personal" || project.instructions.isNotBlank())) return "Deploy the Projects backend and refresh setup before chatting in this project."
        return null
    }
}

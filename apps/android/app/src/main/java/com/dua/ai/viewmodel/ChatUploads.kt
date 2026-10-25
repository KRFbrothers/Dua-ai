package com.dua.ai.viewmodel

import android.app.Application
import android.net.Uri
import com.dua.ai.attachments.AttachmentReader
import com.dua.ai.attachments.CameraCaptureStore
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.attachments.PhotoAttachmentReader
import com.dua.ai.attachments.PhotoPolicy
import com.dua.ai.files.AudioRecorder
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.files.SignedUpload
import com.dua.ai.files.StorageUsage
import com.dua.ai.files.StoredFileTypes
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ChatViewModel state and actions the uploads, recording and storage features read and drive. Implemented by ChatViewModel. */
internal interface UploadsHost {
    val viewModelScope: CoroutineScope
    fun <T : Application> getApplication(): T
    val account: com.dua.ai.account.AccountRepository
    val api: com.dua.ai.network.DuaApiClient
    val fileClient: com.dua.ai.files.StoredFileClient
    val pendingDeletes: com.dua.ai.files.PendingFileDeletes
    val recorder: AudioRecorder
    val mediaCache: com.dua.ai.imagine.MediaCache
    val uploadJobs: MutableMap<String, Job>
    var libraryJob: Job?
    var flushingDeletes: Boolean
    var storageUsageGeneration: Long
    var selectionVersion: Int
    var preparingSelections: Int
    val _cloudLibrary: MutableStateFlow<com.dua.ai.library.CloudLibraryState>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _uploads: MutableStateFlow<List<PendingUpload>>
    val _isTyping: MutableStateFlow<Boolean>
    val _attachmentError: MutableStateFlow<String>
    val _recordingSince: MutableStateFlow<Long?>
    val _imagine: MutableStateFlow<Boolean>
    val _storageUsage: MutableStateFlow<StorageUsage?>
    val _storageUsageStatus: MutableStateFlow<com.dua.ai.files.StorageUsageStatus>
    val _dataBusy: MutableStateFlow<Boolean>
    val _dataStatus: MutableStateFlow<String>
    val _messages: MutableStateFlow<List<ChatMessage>>
    val _savedChats: MutableStateFlow<List<SavedChat>>
    val _attachments: MutableStateFlow<List<ChatAttachment>>
    val _readingFiles: MutableStateFlow<Boolean>
    val _preparingFiles: MutableStateFlow<Boolean>
    fun updateUpload(id: String, transform: (PendingUpload) -> PendingUpload)
    fun deleteStoredPaths(paths: Collection<String>)
    fun storedFilesAvailable(): Boolean
    fun storedFilesHint(): String
    fun maxStoredBytes(): Long
    fun saveHistory()
    fun refreshSyncBusy()
    fun describe(uris: List<Uri>): List<PickedFile>
    suspend fun uploadPicked(files: List<PickedFile>): List<Uri>
}

/** Stored-file uploads, library listing, pending deletes, voice recording, storage usage and inline attachment loading, moved out of ChatViewModel.kt unchanged. */
internal class ChatUploads(host: UploadsHost) : UploadsHost by host {

    fun loadLibraryFiles() {
        libraryJob?.cancel()
        val owner = account.state.value.user?.id
        _cloudLibrary.value = com.dua.ai.library.CloudLibraryState(ownerId = owner)
        if (owner == null || !_integrations.value.filesEnabled) return
        _cloudLibrary.value = _cloudLibrary.value.copy(loading = true)
        libraryJob = viewModelScope.launch {
            try {
                val json = account.request("/api/files/list", authenticated = true)
                val files = com.dua.ai.library.CloudLibrary.files(json, owner)
                if (account.state.value.user?.id == owner) _cloudLibrary.value = com.dua.ai.library.CloudLibraryState(owner, files,
                    notice = if (json.optBoolean("truncated")) "Cloud inventory is limited to 10,000 files." else "")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (account.state.value.user?.id == owner) _cloudLibrary.value = com.dua.ai.library.CloudLibraryState(owner,
                    notice = "Cloud inventory could not be loaded. Refresh after updating the backend; chat and project files remain available.")
            }
        }
    }
    internal fun startUpload(name: String, type: String, size: Long, open: () -> java.io.InputStream, cleanup: () -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        _uploads.update { it + PendingUpload(id, name, type, size) }
        uploadJobs[id] = viewModelScope.launch {
            try {
                val signed = SignedUpload.parse(account.request("/api/files/sign", "POST",
                    org.json.JSONObject().put("name", name).put("mimeType", type).put("size", size), authenticated = true))
                withContext(Dispatchers.IO) { fileClient.upload(signed, size, open) { sent -> updateUpload(id) { it.copy(sent = sent) } } }
                val ref = FileRef(signed.path, name, signed.mimeType.ifBlank { type }, size)
                if (_uploads.value.none { it.id == id }) deleteStoredPaths(listOf(ref.path))
                else updateUpload(id) { it.copy(state = PendingUpload.DONE, sent = size, ref = ref) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateUpload(id) { it.copy(state = PendingUpload.FAILED, error = when (failure) {
                    is java.io.IOException -> "Upload interrupted. Check the connection and try again."
                    else -> failure.message ?: "Upload failed. Try again."
                }) }
            } finally { uploadJobs.remove(id); runCatching { cleanup() } }
        }
    }
    /** Removes one pending upload; an already uploaded (unsent) file is deleted from storage. */
    fun cancelUpload(id: String) {
        val upload = _uploads.value.find { it.id == id } ?: return
        if (_isTyping.value) return
        uploadJobs.remove(id)?.cancel()
        _uploads.update { list -> list.filterNot { it.id == id } }
        if (!upload.shared) upload.ref?.let { deleteStoredPaths(listOf(it.path)) }
    }
    internal fun discardUploads() {
        val current = _uploads.value
        if (current.isEmpty()) return
        current.forEach { uploadJobs.remove(it.id)?.cancel() }
        _uploads.value = emptyList()
        deleteStoredPaths(current.filterNot { it.shared }.mapNotNull { it.ref?.path })
    }
    fun flushPendingFileDeletes() {
        val user = account.state.value.user ?: return
        if (flushingDeletes) return
        val paths = pendingDeletes.forUser(user.id).take(500)
        if (paths.isEmpty()) return
        flushingDeletes = true
        viewModelScope.launch {
            var again = false
            try {
                account.request("/api/files/delete", "POST", org.json.JSONObject().put("paths", org.json.JSONArray(paths)), authenticated = true)
                pendingDeletes.remove(paths); again = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: com.dua.ai.account.AccountException) { if (failure.status == 400) pendingDeletes.remove(paths) }
            catch (failure: Exception) { com.dua.ai.diagnostics.DuaLog.soft(com.dua.ai.diagnostics.SoftErrorArea.FileDelete, failure) }
            finally { flushingDeletes = false }
            if (again) flushPendingFileDeletes()
        }
    }
    fun startRecording() {
        if (_isTyping.value || recorder.recording) return
        if (!api.usesInlineChatFiles(_imagine.value) && !storedFilesAvailable()) { _attachmentError.value = storedFilesHint(); return }
        try { recorder.start(); _recordingSince.value = recorder.startedAt; _attachmentError.value = "" }
        catch (failure: Exception) { _attachmentError.value = failure.message ?: "Recording could not start." }
    }
    fun stopRecording() {
        val file = recorder.stop()
        _recordingSince.value = null
        if (file == null) { _attachmentError.value = "Recording was too short. Hold on a little longer and try again."; return }
        val name = "Voice note " + java.time.LocalTime.now().withNano(0).toString().take(5) + ".m4a"
        if (api.usesInlineChatFiles(_imagine.value)) loadFiles(cleanup = { file.delete() }) {
            file.inputStream().use { PhotoAttachmentReader.prepare(listOf(PhotoPolicy.readSource(name, AudioRecorder.MIME, it, allowAudio = true))) }
        } else startUpload(name, AudioRecorder.MIME, file.length(), open = { file.inputStream() }, cleanup = { file.delete() })
    }
    fun loadStorageUsage() {
        val owner = account.state.value.user?.id
        val generation = ++storageUsageGeneration
        _storageUsage.value = null
        _storageUsageStatus.value = com.dua.ai.files.StorageUsageStatus(ownerId = owner, loading = owner != null)
        if (owner == null) return
        fun current() = generation == storageUsageGeneration && account.state.value.user?.id == owner
        viewModelScope.launch {
            try {
                val json = account.request("/api/files/usage", authenticated = true)
                val usage = com.dua.ai.files.StorageOverview.usage(json)
                if (current()) _storageUsage.value = usage
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (current()) {
                    val message = failure.message ?: "Storage usage could not be loaded. Refresh to retry."
                    _storageUsageStatus.value = com.dua.ai.files.StorageUsageStatus(owner, error = message)
                    if (!_dataBusy.value) _dataStatus.value = message
                }
            } finally {
                if (current()) _storageUsageStatus.value = _storageUsageStatus.value.copy(loading = false)
            }
        }
    }
    /** Deletes every uploaded file for this account. Chats keep the file names but can no longer open or re-read them. */
    fun deleteAllUploadedFiles() {
        if (_dataBusy.value || _isTyping.value) return
        val user = account.state.value.user ?: run { _dataStatus.value = "Sign in to delete uploaded files."; return }
        ++storageUsageGeneration
        _storageUsageStatus.value = com.dua.ai.files.StorageUsageStatus(user.id)
        _dataBusy.value = true; refreshSyncBusy()
        _dataStatus.value = "Deleting uploaded files…"
        viewModelScope.launch {
            try {
                val json = account.request("/api/files/delete-all", "POST", org.json.JSONObject(), authenticated = true)
                discardUploads()
                pendingDeletes.remove(pendingDeletes.forUser(user.id))
                _messages.value = _messages.value.map { if (it.files.isEmpty() && it.media.isEmpty()) it else it.copy(files = emptyList(), media = emptyList()) }
                _savedChats.value = _savedChats.value.map { chat -> if (chat.messages.none { it.files.isNotEmpty() || it.media.isNotEmpty() }) chat else chat.copy(messages = chat.messages.map { it.copy(files = emptyList(), media = emptyList()) }) }
                runCatching { mediaCache.clear() }
                saveHistory()
                _storageUsage.value = StorageUsage(0, 0, _storageUsage.value?.maxFileBytes ?: maxStoredBytes())
                _dataStatus.value = "${json.optInt("deleted")} uploaded and generated files deleted. Chats keep file names only."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _dataStatus.value = failure.message ?: "Uploaded files could not be deleted. Try again." }
            finally { _dataBusy.value = false }
        }
    }
    internal fun loadFiles(cleanup: () -> Unit = {}, loader: () -> List<ChatAttachment>) {
        if (_isTyping.value) { cleanup(); _attachmentError.value = "Reply complete hone ke baad files select karein."; return }
        val version = ++selectionVersion
        _readingFiles.value = true
        _attachmentError.value = ""
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    val result = loader()
                    require(result.size <= 3) { "Maximum 3 files select karein." }
                    require(result.sumOf { java.util.Base64.getDecoder().decode(it.data).size } <= AttachmentReader.MAX_BYTES) {
                        "Attachments total 2 MB se chhote rakhein."
                    }
                    result
                }
                if (version == selectionVersion) _attachments.value = files
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == selectionVersion) _attachmentError.value = error.message ?: "File read nahi hui. Dobara select karein."
            } finally { cleanup(); if (version == selectionVersion) _readingFiles.value = false }
        }
    }
    fun selectAttachments(uris: List<Uri>) {
        if (_isTyping.value) { _attachmentError.value = "Reply complete hone ke baad files select karein."; return }
        preparingSelections++
        _preparingFiles.value = true
        viewModelScope.launch {
            try { prepareAttachments(uris) }
            finally { preparingSelections--; _preparingFiles.value = preparingSelections > 0 }
        }
    }
    internal suspend fun prepareAttachments(uris: List<Uri>) {
        val picked = try { withContext(Dispatchers.IO) { describe(uris) } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _attachmentError.value = "File read nahi hui. Dobara select karein."; return }
        val inline = if (api.usesInlineChatFiles(_imagine.value)) picked.map { it.uri }
        else if (storedFilesAvailable()) uploadPicked(picked) else {
            val blocked = picked.filter { StoredFileTypes.needsStorage(it.name, it.type) || (it.size ?: 0) > AttachmentReader.MAX_BYTES }
            if (blocked.isNotEmpty()) _attachmentError.value = storedFilesHint()
            picked.filterNot { it in blocked }.map { it.uri }
        }
        if (inline.isNotEmpty()) selectInlineAttachments(inline)
    }
    internal fun selectInlineAttachments(uris: List<Uri>) = loadFiles {
        require(uris.size <= 3) { "Maximum 3 files select karein." }
        val resolver = getApplication<Application>().contentResolver
        val sources = uris.map { uri ->
            val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else "Selected file"
            } ?: "Selected file"
            // Text/code files are recognised by MIME type or extension (many apps report octet-stream).
            val type = resolver.getType(uri).orEmpty()
            val input = resolver.openInputStream(uri) ?: error("File read nahi hui.")
            input.use { PhotoPolicy.readSource(name, type, it, allowAudio = api.usesInlineChatFiles(_imagine.value)) }
        }
        PhotoAttachmentReader.prepare(sources)
    }
    fun selectCameraFile(file: File) {
        // Imagine edits need the photo in storage (the server reads sources from the owner's folder).
        if (_imagine.value && storedFilesAvailable() && !_isTyping.value) {
            val name = "Camera photo " + java.time.LocalTime.now().withNano(0).toString().take(5) + ".jpg"
            startUpload(name, "image/jpeg", file.length(), open = { file.inputStream() }, cleanup = { file.delete() })
            return
        }
        selectInlineCamera(file)
    }
    internal fun selectInlineCamera(file: File) = loadFiles(cleanup = { file.delete() }) {
        listOf(CameraCaptureStore(getApplication<Application>().cacheDir).read(file))
    }
}

package com.dua.ai.backup

import android.content.ContentResolver
import android.net.Uri
import com.dua.ai.files.FileRef
import com.dua.ai.history.ChatBackup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class FullBackupSnapshot(val backup: ChatBackup, val memory: List<String>, val ownerId: String?, val cloudFilesEnabled: Boolean,
    val deletedChatIds: List<String> = emptyList())
data class FullBackupPreview(val chats: Int, val projects: Int, val notes: Int, val files: Int, val bytes: Long)
data class FullBackupState(val busy: Boolean = false, val status: String = "", val preview: FullBackupPreview? = null)

/** Host owns the existing cache transaction and operation guard; archive/file handling stays isolated here. */
interface FullBackupPort {
    fun begin(): FullBackupSnapshot
    fun end()
    fun owner(): String?
    fun restoreFilesAvailable(): Boolean
    fun preflight(data: FullBackupData)
    suspend fun importData(backup: ChatBackup, memory: List<String>): Boolean
}

class FullBackupController(private val scope: CoroutineScope, private val resolver: ContentResolver,
    private val root: File, private val transfer: BackupFileTransfer, private val port: FullBackupPort) {
    private val mutable = MutableStateFlow(FullBackupState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var reviewed: PreparedFullBackup? = null
    private var closed = false
    private var workspaceInitialized = false
    private var operationSucceeded = false

    fun save(uri: Uri, password: CharArray) = run(password, discardReview = true) { snapshot, workspace ->
        val encrypted = File(workspace, "backup.duabackup")
        FullBackupBuilder(transfer).write(snapshot, workspace, encrypted, password) { message ->
            mutable.value = mutable.value.copy(status = message)
        }
        withContext(Dispatchers.IO) {
            val active = currentCoroutineContext()
            // All downloads, validation and encryption finish before touching the selected destination.
            resolver.openOutputStream(uri, "wt")?.use { output ->
                encrypted.inputStream().use { BackupCipher.copyBounded(it, output, BackupCipher.MAX_BYTES + 2 * 1024 * 1024, active::ensureActive) }
            } ?: error("The backup destination cannot be written.")
        }
        operationSucceeded = true
        mutable.value = FullBackupState(busy = true, status = "Full backup saved through the file picker. Check Google Drive for its upload status.")
    }

    fun review(uri: Uri, password: CharArray) = run(password, retainReview = true, discardReview = true) { _, workspace ->
        val zip = File(workspace, "payload.zip")
        val prepared = withContext(Dispatchers.IO) {
            val active = currentCoroutineContext()
            resolver.openInputStream(uri)?.use { input -> zip.outputStream().use { BackupCipher.decrypt(input, it, password, active::ensureActive) } }
                ?: error("The selected backup cannot be read.")
            // No ZIP or user data is parsed until the complete stream authenticates.
            FullBackupArchive.readZip(zip, workspace, active::ensureActive).also { zip.delete() }
        }
        checkRestoreOwner(prepared.data)
        port.preflight(prepared.data)
        reviewed = prepared
        mutable.value = FullBackupState(busy = true, status = "Backup verified. Review before restoring.", preview = preview(prepared.data))
    }

    fun confirmRestore() {
        val prepared = reviewed ?: return
        run(charArrayOf(), validatePassword = false) { _, _ ->
            checkRestoreOwner(prepared.data)
            port.preflight(prepared.data)
            val owner = port.owner()
            val current = if (prepared.data.assets.isNotEmpty()) {
                check(port.restoreFilesAvailable()) { "Update Feature setup and deploy the file-restore backend before restoring raw files." }
                transfer.inventory(owner ?: error("Sign in to restore raw files."))
            } else emptyList()
            val existing = current.associateBy { it.path }
            val inline = mutableMapOf<String, String>()
            val verified = mutableSetOf<String>()
            for ((index, asset) in prepared.data.assets.withIndex()) {
                currentCoroutineContext().ensureActive()
                mutable.value = mutable.value.copy(status = "Restoring file ${index + 1} of ${prepared.data.assets.size}…")
                val path = BackupFileTransfer.restorePath(asset, owner ?: error("Sign in to restore raw files."))
                val present = existing[path]
                if (path in verified) { /* Identical in-memory attachment already recovered in this run. */ }
                else if (present != null) {
                    require(present.size == asset.size) { "An existing cloud file differs from the backup. It was kept; nothing was imported." }
                    val check = File(prepared.directory, "check-${asset.id}")
                    try {
                        transfer.download(present, owner!!, check)
                        require(withContext(Dispatchers.IO) { FullBackupArchive.digest(check) } == asset.sha256) { "An existing cloud file differs from the backup. It was kept; nothing was imported." }
                    } finally { check.delete() }
                } else {
                    transfer.restore(asset, prepared.files.getValue(asset.id), owner ?: error("Sign in to restore raw files."))
                }
                verified += path
                if (asset.path == null) inline[asset.id] = path
            }
            checkRestoreOwner(prepared.data)
            val backup = attachInlineFiles(prepared.data, inline)
            // The host releases/reacquires the operation flag synchronously for its existing import transaction.
            check(port.importData(backup, prepared.data.memory)) { "Files were recovered where possible, but the phone data import failed. Existing data was kept. Retry the restore." }
            reviewed = null
            operationSucceeded = true
            mutable.value = FullBackupState(busy = true, status = "Full backup restored as chat/project copies; Personal notes merged. Existing chats and cloud files were kept. Use account sync to save restored data to the server.")
            withContext(NonCancellable + Dispatchers.IO) { prepared.directory.deleteRecursively() }
        }
    }

    fun cancelReview() {
        if (mutable.value.busy) return
        val old = reviewed; reviewed = null
        if (old != null) scope.launch(Dispatchers.IO) { old.directory.deleteRecursively() }
        mutable.value = FullBackupState(status = "Restore cancelled. Your data is unchanged.")
    }

    fun cancel() { job?.cancel() }
    fun notice(message: String) { if (!mutable.value.busy) mutable.value = mutable.value.copy(status = message) }
    fun close() { closed = true; job?.cancel(); if (!mutable.value.busy) { reviewed?.directory?.deleteRecursively(); reviewed = null } }

    private fun run(password: CharArray, validatePassword: Boolean = true, retainReview: Boolean = false, discardReview: Boolean = false,
        action: suspend (FullBackupSnapshot, File) -> Unit) {
        if (closed || mutable.value.busy) { password.fill('\u0000'); return }
        val snapshot = try {
            if (validatePassword) BackupCipher.validatePassword(password)
            port.begin()
        } catch (failure: Exception) {
            password.fill('\u0000'); notice(failure.message ?: "Finish the current action, then retry."); return
        }
        mutable.value = mutable.value.copy(busy = true, status = "Preparing full backup…")
        operationSucceeded = false
        job = scope.launch {
            val workspace = File(root, UUID.randomUUID().toString())
            try {
                if (!workspaceInitialized) {
                    withContext(Dispatchers.IO) { root.listFiles()?.forEach { it.deleteRecursively() } }
                    workspaceInitialized = true
                }
                if (discardReview) {
                    val old = reviewed; reviewed = null
                    mutable.value = mutable.value.copy(preview = null)
                    withContext(Dispatchers.IO) { old?.directory?.deleteRecursively() }
                }
                withContext(Dispatchers.IO) { check(workspace.mkdirs()) { "Backup workspace could not be created." } }
                action(snapshot, workspace)
            } catch (cancelled: CancellationException) {
                if (!operationSucceeded) mutable.value = mutable.value.copy(status = "Backup operation cancelled. Check the destination for an incomplete copy. Any already recovered cloud files were kept.")
                throw cancelled
            } catch (failure: Exception) {
                val detail = failure.message ?: "Backup operation failed."
                mutable.value = mutable.value.copy(status = if (!validatePassword) "$detail Existing phone data was kept. Recovered cloud files were kept; retry to continue." else detail)
            } finally {
                password.fill('\u0000')
                // Keep the entire finalizer uncancellable: returning from an IO dispatcher into
                // a cancelled job must not skip releasing the host's operation guard.
                withContext(NonCancellable) {
                    if (!retainReview || reviewed?.directory != workspace) withContext(Dispatchers.IO) { workspace.deleteRecursively() }
                    if (closed) withContext(Dispatchers.IO) { reviewed?.directory?.deleteRecursively(); reviewed = null }
                    port.end()
                    job = null
                    mutable.value = mutable.value.copy(busy = false)
                }
            }
        }
    }

    private fun checkRestoreOwner(data: FullBackupData) {
        if (data.assets.any { it.path != null }) check(data.ownerId != null && port.owner() == data.ownerId) { "Sign in to the original backup account to restore its cloud files." }
    }

    private fun preview(data: FullBackupData) = FullBackupPreview(data.backup.chats.size,
        data.backup.projects.count { it.id != com.dua.ai.projects.ProjectCodec.PERSONAL && it.details?.deleted != true }, data.memory.size, data.assets.size, data.assets.sumOf { it.size })

    private fun attachInlineFiles(data: FullBackupData, paths: Map<String, String>): ChatBackup = data.backup.copy(chats = data.backup.chats.map { chat ->
        chat.copy(messages = chat.messages.map { message ->
            val added = data.assets.filter { it.path == null && it.chatId == chat.id && it.messageId == message.id }
                .map { asset -> FileRef(paths.getValue(asset.id), asset.name, asset.mimeType, asset.size) }
            message.copy(files = message.files + added)
        })
    })
}

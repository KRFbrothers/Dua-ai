package com.dua.ai.backup

import com.dua.ai.attachments.AttachmentReader
import com.dua.ai.history.ChatHistoryCodec
import kotlinx.coroutines.*
import java.io.File
import java.util.Base64

/** Shared full-data archive writer for manual and scheduled backups. Never changes live phone data. */
class FullBackupBuilder(private val transfer: BackupFileTransfer) {
    suspend fun write(snapshot: FullBackupSnapshot, workspace: File, target: File, password: CharArray, onProgress: (String) -> Unit = {}) {
        val prepared = collect(snapshot, workspace, onProgress)
        val zip = File(workspace, "payload.zip")
        withContext(Dispatchers.IO) {
            val active = currentCoroutineContext()
            FullBackupArchive.writeZip(prepared, zip, active::ensureActive)
            onProgress("Encrypting full backup…")
            zip.inputStream().use { input -> target.outputStream().use { BackupCipher.encrypt(input, it, password, active::ensureActive) } }
        }
    }

    private suspend fun collect(snapshot: FullBackupSnapshot, directory: File, onProgress: (String) -> Unit): PreparedFullBackup {
        val backup = snapshot.backup
        require(backup.chats.none { c -> c.messages.any { m -> m.media.any { it.path == null } } }) { "Some generated media has no stored file. Save it before creating a full backup." }
        val remote = if (snapshot.ownerId != null && snapshot.cloudFilesEnabled) transfer.inventory(snapshot.ownerId) else emptyList()
        val estimate = remote.sumOf { it.size }
        require(estimate <= FullBackupArchive.MAX_ASSET_BYTES) { "Raw files exceed the 1 GB backup limit." }
        require(estimate * 3 + 32 * 1024 * 1024 < directory.usableSpace) { "Free more phone space to prepare this encrypted backup." }
        val assets = mutableListOf<BackupAsset>()
        val files = mutableMapOf<String, File>()
        suspend fun add(path: String?, name: String, mime: String, chat: String? = null, message: String? = null, create: suspend (File) -> Unit) {
            require(assets.size < FullBackupArchive.MAX_FILES) { "There are too many files for one backup." }
            val id = "%05d".format(java.util.Locale.US, assets.size)
            val file = File(directory, "asset-$id")
            create(file)
            val sha = withContext(Dispatchers.IO) { FullBackupArchive.digest(file) }
            assets += BackupAsset(id, path, name.take(200), mime, file.length(), sha, chat, message)
            files[id] = file
        }
        for ((index, file) in remote.withIndex()) {
            onProgress("Backing up file ${index + 1} of ${remote.size}…")
            add(file.path, file.name, file.mimeType) { transfer.download(file, snapshot.ownerId!!, it) }
        }
        for (chat in backup.chats) for (message in chat.messages) for (attachment in message.inlineAttachments) {
            require(attachment.data.length <= (AttachmentReader.MAX_BYTES * 4L / 3 + 8)) { "An inline attachment is too large." }
            val mime = when (attachment.mimeType) {
                "audio/m4a", "audio/x-m4a" -> "audio/mp4"; "audio/mp3" -> "audio/mpeg"; "audio/x-wav", "audio/wave" -> "audio/wav"
                else -> attachment.mimeType
            }
            add(null, attachment.name, mime, chat.id, message.id) { file -> withContext(Dispatchers.IO) {
                val bytes = Base64.getDecoder().decode(attachment.data)
                try { require(bytes.size <= AttachmentReader.MAX_BYTES); file.writeBytes(bytes) } finally { bytes.fill(0) }
            } }
        }
        val exported = ChatHistoryCodec.readBackup(java.io.ByteArrayInputStream(ChatHistoryCodec.exportBackup(backup.chats, projects = backup.projects).toByteArray(Charsets.UTF_8)))
        val data = FullBackupData(exported, snapshot.memory, snapshot.ownerId, assets)
        FullBackupArchive.validate(data)
        return PreparedFullBackup(data, files, directory)
    }

}

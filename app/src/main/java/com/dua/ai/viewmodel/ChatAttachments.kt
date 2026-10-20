package com.dua.ai.viewmodel

import android.app.Application
import android.net.Uri
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.PendingUpload
import com.dua.ai.files.StoredFileTypes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The ChatViewModel state and actions the draft-attachment glue reads and drives. Implemented by ChatViewModel. */
internal interface AttachmentsHost {
    fun <T : Application> getApplication(): T
    val uploadsFeature: ChatUploads
    var selectionVersion: Int
    val _attachments: MutableStateFlow<List<ChatAttachment>>
    val _attachmentError: MutableStateFlow<String>
    val _readingFiles: MutableStateFlow<Boolean>
    val _preparingFiles: MutableStateFlow<Boolean>
    val _uploads: MutableStateFlow<List<PendingUpload>>
    val _isTyping: MutableStateFlow<Boolean>
    val _dataBusy: MutableStateFlow<Boolean>
    fun copyCapped(uri: Uri, limit: Long): File?
}

/** Draft-attachment glue (pick, remove, clear, stored-file split), moved out of ChatViewModel.kt unchanged. */
internal class ChatAttachments(host: AttachmentsHost) : AttachmentsHost by host {

    /** Bytes of a draft image attachment, or null when it is not an image any more. */
    fun attachmentImageBytes(index: Int): ByteArray? = _attachments.value.getOrNull(index)
        ?.takeIf { it.mimeType.startsWith("image/") && it.data.length <= (com.dua.ai.attachments.AttachmentReader.MAX_BYTES + 2) / 3 * 4 }
        ?.let { runCatching { java.util.Base64.getDecoder().decode(it.data) }.getOrNull() }

    /** Reserve shared-file intake synchronously, before the next queued entry can dispatch. */
    fun acceptSharedAttachments(uris: List<Uri>): Boolean {
        if (_isTyping.value || _dataBusy.value || _preparingFiles.value || _readingFiles.value ||
            _attachments.value.isNotEmpty() || _uploads.value.isNotEmpty()) return false
        uploadsFeature.selectAttachments(uris)
        return true
    }

    /** Remove only the selected inline draft file; keep every other attachment intact. */
    fun removeAttachment(index: Int) {
        if (_isTyping.value) return
        _attachments.update { files -> files.filterIndexed { i, _ -> i != index } }
    }

    fun clearAttachments() {
        selectionVersion++
        _attachments.value = emptyList()
        _attachmentError.value = ""
        _readingFiles.value = false
        uploadsFeature.discardUploads()
    }

    /** Uploads picked files that storage accepts; returns the rest for the small inline path. */
    internal suspend fun uploadPicked(files: List<PickedFile>): List<Uri> {
        val resolver = getApplication<Application>().contentResolver
        val inline = mutableListOf<Uri>()
        var errors = 0
        for (file in files) {
            val type = StoredFileTypes.storedType(file.name, file.type)
            if (type == null) { inline += file.uri; continue }
            if (_uploads.value.count { it.state != PendingUpload.FAILED } >= MAX_UPLOADS) { _attachmentError.value = "Up to $MAX_UPLOADS stored files per message."; break }
            var size = file.size
            var temp: File? = null
            if (size == null) {
                // Some providers do not report a size; copy to cache first (capped) so the upload has a length.
                temp = withContext(Dispatchers.IO) { copyCapped(file.uri, uploadsFeature.maxStoredBytes()) }
                size = temp?.length()
            }
            when {
                size == null -> { errors++; _attachmentError.value = "\"${file.name}\" is larger than ${StoredFileTypes.sizeLabel(uploadsFeature.maxStoredBytes())}." }
                size == 0L -> { temp?.delete(); errors++; _attachmentError.value = "\"${file.name}\" is empty." }
                size > uploadsFeature.maxStoredBytes() -> { temp?.delete(); errors++; _attachmentError.value = "\"${file.name}\" is larger than ${StoredFileTypes.sizeLabel(uploadsFeature.maxStoredBytes())}. Choose a smaller file." }
                else -> {
                    val copy = temp
                    uploadsFeature.startUpload(file.name, type, size, open = { copy?.inputStream() ?: resolver.openInputStream(file.uri) ?: throw java.io.IOException("File cannot be read.") },
                        cleanup = { copy?.delete() })
                }
            }
        }
        if (errors == 0 && inline.isEmpty()) _attachmentError.value = ""
        return inline
    }

    private companion object {
        const val MAX_UPLOADS = 10
    }
}

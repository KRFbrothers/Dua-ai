package com.dua.ai.library

import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.attachments.TextFiles
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.history.SavedChat
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.model.ChatMessage
import com.dua.ai.projects.ChatProject
import java.security.MessageDigest
import java.util.Base64

enum class LibraryKind { Files, Images, Code }
data class LibraryItem(val id: String, val name: String, val content: String = "", val kind: LibraryKind,
    val chatId: String? = null, val projectId: String? = null, val file: FileRef? = null,
    val media: GeneratedMedia? = null, val inline: ChatAttachment? = null, val language: String? = null,
    val status: String = "") {
    fun matches(query: String) = query.isBlank() || name.contains(query.trim(), true) || content.contains(query.trim(), true)
}

/** Derived inventory only: never creates demo data, fetches storage, or becomes a second source of truth. */
object LibraryIndex {
    fun build(chats: List<SavedChat>, activeId: String, messages: List<ChatMessage>, projects: List<ChatProject>,
        uploads: List<PendingUpload> = emptyList(), attachments: List<ChatAttachment> = emptyList(), cloudFiles: List<FileRef> = emptyList()): List<LibraryItem> {
        val items = linkedMapOf<String, LibraryItem>()
        fun add(item: LibraryItem) {
            val existing = items[item.id]
            if (existing == null) items[item.id] = item
            else if (existing.file == null && item.file != null) {
                // Keep the generated prompt, but make the real attachment filename searchable too.
                items[item.id] = existing.copy(name = item.name, file = item.file)
            }
        }
        fun file(ref: FileRef, chat: String? = null, project: String? = null, status: String = "") {
            val image = ref.mimeType.startsWith("image/")
            add(LibraryItem("file:${ref.path}", ref.name, ref.transcript.orEmpty(), if (image) LibraryKind.Images else LibraryKind.Files,
                chat, project, ref, if (image) GeneratedMedia(ref.path, ref.mimeType, ref.size) else null, status = status))
        }
        fun inline(ref: ChatAttachment, chat: String?, project: String?, status: String = "") {
            val image = ref.mimeType.startsWith("image/")
            val hash = MessageDigest.getInstance("SHA-256").digest(ref.data.toByteArray()).joinToString("") { "%02x".format(it) }
            val text = if (ref.mimeType == "text/plain") runCatching { TextFiles.decode(ref.name, Base64.getDecoder().decode(ref.data)) }.getOrDefault("") else ""
            add(LibraryItem("inline:$hash", ref.name, text, if (image) LibraryKind.Images else LibraryKind.Files, chat, project,
                media = if (image) GeneratedMedia(null, ref.mimeType, url = "inline:$hash") else null, inline = ref, status = status))
        }
        val current = chats.map { if (it.id == activeId) it.copy(messages = messages) else it }.toMutableList()
        if (current.none { it.id == activeId } && messages.isNotEmpty()) current.add(SavedChat(activeId, "Dua", "", messages, Long.MAX_VALUE))
        current.sortedByDescending { it.updatedAt }.forEach { chat -> chat.messages.forEach { message ->
            message.media.filter { !it.isVideo }.forEachIndexed { n, media ->
                add(LibraryItem("file:${media.key}", "Image ${n + 1}", message.imagine?.prompt ?: message.text, LibraryKind.Images,
                    chat.id, chat.projectId, media = media))
            }
            message.files.forEach { file(it, chat.id, chat.projectId) }
            message.inlineAttachments.forEach { inline(it, chat.id, chat.projectId) }
            if (!message.attachmentText.isNullOrBlank()) add(LibraryItem("text:${chat.id}:${message.id}",
                "Attached text · ${message.attachmentText.lineSequence().first().removePrefix("Attached text file (").removeSuffix("):")}",
                message.attachmentText, LibraryKind.Files, chat.id, chat.projectId))
            if (!message.isUser) {
                codeBlocks(message.text).forEachIndexed { n, (language, text) -> add(LibraryItem("code:${chat.id}:${message.id}:$n", "${language.ifBlank { "Code" }} snippet", text,
                    LibraryKind.Code, chat.id, chat.projectId, language = language)) }
                message.codeRuns.forEachIndexed { n, run -> add(LibraryItem("run:${chat.id}:${message.id}:$n", "${run.language} run", run.code,
                    LibraryKind.Code, chat.id, chat.projectId, language = run.language)) }
            }
        } }
        projects.filter { it.details?.deleted != true }.forEach { p -> p.sources.forEach { file(it, project = p.id) } }
        cloudFiles.forEach { file(it) }
        uploads.forEach { upload -> upload.ref?.let { file(it, activeId, status = "Attached to current draft") } ?: add(
            LibraryItem("upload:${upload.id}", upload.name, upload.error.orEmpty(), LibraryKind.Files, activeId,
                status = if (upload.uploading) "Uploading ${upload.sent} / ${upload.size} bytes" else upload.error.orEmpty())) }
        attachments.forEach { inline(it, activeId, null, "Attached to current draft") }
        return items.values.toList()
    }

    /** Fenced snippets from real assistant messages; closing fence must match the opening character/length. */
    internal fun codeBlocks(text: String): List<Pair<String, String>> {
        return com.dua.ai.model.CodeFences.parse(text)
    }
}

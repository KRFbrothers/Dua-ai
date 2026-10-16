package com.dua.ai.backup.drive

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.backup.FullBackupArchive
import com.dua.ai.backup.FullBackupSnapshot
import com.dua.ai.history.ChatBackup
import com.dua.ai.projects.ProjectImports

/** Read-only recovery snapshot: fresh server data plus preserved offline drafts/context. Never applies to the live cache. */
internal fun mergeDriveSnapshot(phone: FullBackupSnapshot, server: CloudSnapshot): FullBackupSnapshot {
    val adjusted = ProjectImports.prepare(server.projects, phone.backup)
    val merged = CloudSnapshotCodec.mergeDownload(
        CloudSnapshot(adjusted.chats, phone.memory, adjusted.projects, phone.deletedChatIds), server.copy(projects = adjusted.projects)).snapshot
    val chats = merged.chats.map { chat ->
        val local = phone.backup.chats.firstOrNull { it.id == chat.id }
        chat.copy(messages = chat.messages.map { message ->
            val original = local?.messages?.firstOrNull { it.id == message.id }
            // Inline bytes are process-only and absent from cloud JSON; recover them only for the
            // same unedited user turn. Offline conflict copies already retain their original bytes.
            if (original != null && message.isUser && original.isUser && message.text == original.text &&
                message.files == original.files && message.attachmentText == original.attachmentText && message.inlineAttachments.isEmpty())
                message.copy(inlineAttachments = original.inlineAttachments)
            else message
        })
    }
    return phone.copy(backup = ChatBackup(chats, merged.projects), memory = FullBackupArchive.mergeMemory(server.memory, phone.memory),
        deletedChatIds = merged.deletedChatIds)
}

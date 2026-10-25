package com.dua.ai.backup.drive

import android.app.Application
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.backup.FullBackupSnapshot
import com.dua.ai.history.ChatBackup
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.projects.ProjectCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveBackupSnapshotTest {
    private val message = ChatMessage(id = "message", text = "Hello", isUser = true, model = "Dua")
    private val chat = SavedChat("chat", "Dua", "", listOf(message), 123)
    private fun phone(chats: List<SavedChat> = listOf(chat), notes: List<String> = emptyList()) =
        FullBackupSnapshot(ChatBackup(chats, ProjectCodec.defaults), notes, "11111111-2222-4333-8444-555555555555", true)

    @Test fun freshServerChatsAndOfflineDraftConflictsAreBothBackedUpWithoutChangingPhoneData() {
        val local = phone(listOf(chat.copy(draft = "Offline draft")), listOf("Phone memory"))
        val server = CloudSnapshot(listOf(chat, chat.copy(id = "server-only")), listOf("Server memory"))
        val result = mergeDriveSnapshot(local, server)
        assertTrue(result.backup.chats.any { it.id == "server-only" })
        assertTrue(result.backup.chats.any { it.id.startsWith("unsynced-") && it.draft == "Offline draft" })
        assertEquals(listOf("Server memory", "Phone memory"), result.memory)
        assertEquals(1, local.backup.chats.size); assertEquals("Offline draft", local.backup.chats.single().draft)
        assertEquals(listOf("Phone memory"), local.memory)
    }
    @Test fun unchangedUserTurnsKeepInlineBytesThatTheCloudSnapshotCannotStore() {
        val attachment = ChatAttachment("photo.png", "image/png", "AQID")
        val local = phone(listOf(chat.copy(messages = listOf(message.copy(inlineAttachments = listOf(attachment))))))
        val result = mergeDriveSnapshot(local, CloudSnapshot(listOf(chat), emptyList()))
        assertEquals(listOf(attachment), result.backup.chats.single().messages.single().inlineAttachments)
    }
    @Test fun localOrServerDeletionTombstonesBlockStaleCopiesAndNoteOverflowIsNeverSilentlyDropped() {
        val local = phone().copy(deletedChatIds = listOf("already-deleted"))
        val remote = CloudSnapshot(listOf(chat.copy(id = "already-deleted")), emptyList(), deletedChatIds = listOf("chat"))
        assertTrue(mergeDriveSnapshot(local, remote).backup.chats.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            mergeDriveSnapshot(phone(notes = listOf("Phone note")), CloudSnapshot(listOf(chat), (1..10).map { "Server note $it" }))
        }
    }
    @Test fun differingProjectInstructionsAndTasksAreRetainedAsSeparateContexts() {
        val local = phone().let { it.copy(backup = it.backup.copy(projects = ProjectCodec.defaults.map { project -> if (project.id == ProjectCodec.PERSONAL) project.copy(instructions = "Offline instructions") else project })) }
        val result = mergeDriveSnapshot(local, CloudSnapshot(listOf(chat), emptyList()))
        assertTrue(result.backup.projects.any { it.instructions == "Offline instructions" })
        assertTrue(result.backup.projects.any { it.id == ProjectCodec.PERSONAL && it.instructions.isEmpty() })
        assertTrue(result.backup.chats.any { it.projectId != ProjectCodec.PERSONAL })
    }
}

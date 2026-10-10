package com.dua.ai.account

import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.BuildInfo
import com.dua.ai.model.BuildFile
import com.dua.ai.model.ChatSummary
import com.dua.ai.model.CodeRun
import com.dua.ai.files.FileRef
import org.junit.Assert.*
import org.junit.Test

class CloudSnapshotTest {
    private val chat = SavedChat("id", "Dua", "Local draft", listOf(ChatMessage(text = "Hi", isUser = true)), 123)
    @Test fun snapshotsRoundTripDraftsAndMemoriesWithoutAttachmentBytes() {
        val snapshot = CloudSnapshot(listOf(chat), listOf("Prefer Hindi")); val json = CloudSnapshotCodec.encode(snapshot)
        val restored = CloudSnapshotCodec.decode(json)
        assertEquals("Local draft", restored.chats.single().draft); assertEquals("Hi", restored.chats.single().messages.single().text)
        assertEquals(snapshot.memory, restored.memory); assertFalse(json.has("accessToken")); assertFalse(json.getJSONArray("chats").getJSONObject(0).has("attachments"))
    }
    @Test fun mergePreservesLocalConflictsAndDoesNotDuplicateRepeatedDownloads() {
        val remote = chat.copy(draft = "Remote draft")
        val merged = CloudSnapshotCodec.merge(listOf(chat), listOf(remote)) { "copy" }
        assertEquals(1, merged.conflicts); assertEquals("Local draft", merged.chats[0].draft); assertEquals("Remote draft", merged.chats[1].draft)
        assertEquals(0, CloudSnapshotCodec.merge(merged.chats, listOf(remote)).added)
    }
    @Test fun malformedOrOversizedSnapshotsAreRejectedBeforeLocalChanges() {
        for (snapshot in listOf(CloudSnapshot(listOf(chat, chat), emptyList()), CloudSnapshot(listOf(chat.copy(draft = "x".repeat(8001))), emptyList()), CloudSnapshot(listOf(chat), List(11) { "note" }), CloudSnapshot(listOf(chat), listOf("x".repeat(201))))) {
            try { CloudSnapshotCodec.encode(snapshot); fail("Invalid snapshot accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun accountSessionRoundTripAndBadTokensAreRejected() {
        val value = AccountSession(AccountUser("11111111-1111-4111-8111-111111111111", "person@example.com", "Dua"), "fake-access", "fake-refresh", 123)
        assertEquals(value, AccountSessionCodec.decode(AccountSessionCodec.encode(value)))
        try { AccountSessionCodec.decode(AccountSessionCodec.encode(value.copy(accessToken = "bad\nheader"))); fail("Bad token accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun serverSnapshotRestoresExtendedChatStateWithoutFileBytes() {
        val user = ChatMessage(text = "Review this file", isUser = true, attachmentText = "a,b\n1,2",
            files = listOf(FileRef("11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222.csv", "file.csv", "text/csv", 12, "a,b\n1,2")))
        val reply = ChatMessage(text = "Done", isUser = false,
            codeRuns = listOf(CodeRun(code = "print(2 + 2)", output = "4", outcome = "ok")),
            build = BuildInfo("owner/repo", "main", listOf(BuildFile("src/Main.kt", 42))),
            imagine = com.dua.ai.imagine.ImagineInfo("image", "owner/job"))
        val original = SavedChat("extended", "Dua", "draft", listOf(user, reply), 123,
            ChatSummary("Earlier context", user.id), "owner/repo")
        val restored = CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(CloudSnapshot(listOf(original), emptyList()))).chats.single()
        assertEquals(original.summary, restored.summary)
        assertEquals(original.buildRepository, restored.buildRepository)
        assertEquals(user.id, restored.messages[0].id)
        assertEquals(user.attachmentText, restored.messages[0].attachmentText)
        assertEquals(user.files, restored.messages[0].files)
        assertEquals(reply.id, restored.messages[1].id)
        assertEquals(reply.codeRuns, restored.messages[1].codeRuns)
        assertEquals(reply.build, restored.messages[1].build)
        assertEquals(reply.imagine, restored.messages[1].imagine)
    }
}

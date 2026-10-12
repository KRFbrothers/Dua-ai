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
    @Test fun deletionMetadataRoundTripsAndRemovesStalePhoneCopiesWithoutRecoveringTheDeletedChat() {
        val remote = CloudSnapshot(emptyList(), emptyList(), deletedChatIds = listOf(chat.id))
        assertEquals(remote, CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(remote)))
        val merged = CloudSnapshotCodec.mergeDownload(CloudSnapshot(listOf(chat), emptyList()), remote)
        assertTrue(merged.snapshot.chats.isEmpty())
        assertEquals(listOf(chat.id), merged.snapshot.deletedChatIds)
        assertEquals(0, merged.conflicts)
        assertEquals(0, merged.added)
        assertEquals(merged.snapshot, CloudSnapshotCodec.mergeDownload(merged.snapshot, remote).snapshot)
    }
    @Test fun malformedOrOverlappingDeletionMetadataIsRejectedWithoutDroppingLiveChatsSilently() {
        val raw = CloudSnapshotCodec.encode(CloudSnapshot(listOf(chat), emptyList()))
        for (value in listOf(org.json.JSONArray().put(chat.id), org.json.JSONArray().put("gone").put("gone"),
            org.json.JSONArray().put(1), org.json.JSONArray().put(""), "invalid", org.json.JSONObject.NULL)) {
            raw.put("deletedChatIds", value)
            assertTrue(runCatching { CloudSnapshotCodec.decode(raw) }.isFailure)
        }
        assertTrue(runCatching { CloudSnapshotCodec.encode(CloudSnapshot(listOf(chat), emptyList(), deletedChatIds = listOf(chat.id))) }.isFailure)
    }
    @Test fun titleOnlyCloudConflictPreservesBothNamesWithoutDuplicatingMessages() {
        val local = chat.copy(customTitle = "Phone name")
        val remote = chat.copy(customTitle = "Server name")
        val merged = CloudSnapshotCodec.mergeDownload(CloudSnapshot(listOf(local), emptyList()), CloudSnapshot(listOf(remote), emptyList()))
        assertEquals(1, merged.conflicts)
        assertEquals(listOf("Server name", "Phone name"), merged.snapshot.chats.map { it.title })
        assertTrue(merged.snapshot.chats.all { it.messages == chat.messages })
        val repeated = CloudSnapshotCodec.mergeDownload(merged.snapshot, CloudSnapshot(listOf(remote), emptyList()))
        assertEquals(0, repeated.conflicts)
        assertEquals(merged.snapshot, repeated.snapshot)
    }
    @Test fun sharedBackendFixtureSurvivesAndroidDecodeAndReencode() {
        val raw = javaClass.getResourceAsStream("/cloud-snapshot-v1.json")!!.bufferedReader().use { it.readText() }
        val expected = org.json.JSONObject(raw).put("projects", com.dua.ai.projects.ProjectCodec.toJson(com.dua.ai.projects.ProjectCodec.defaults))
        val chats = expected.getJSONArray("chats")
        for (i in 0 until chats.length()) chats.getJSONObject(i).put("projectId", "personal")
        val restored = CloudSnapshotCodec.decode(expected)
        val actual = CloudSnapshotCodec.encode(restored)
        assertTrue("Android and backend must preserve the same snapshot fields", expected.similar(actual))
    }

    private val chat = SavedChat("id", "Dua", "Local draft", listOf(ChatMessage(text = "Hi", isUser = true)), 123)
    @Test fun snapshotsRoundTripDraftsAndMemoriesWithoutAttachmentBytes() {
        val snapshot = CloudSnapshot(listOf(chat), listOf("Prefer Hindi")); val json = CloudSnapshotCodec.encode(snapshot)
        val restored = CloudSnapshotCodec.decode(json)
        assertEquals("Local draft", restored.chats.single().draft); assertEquals("Hi", restored.chats.single().messages.single().text)
        assertEquals(snapshot.memory, restored.memory); assertFalse(json.has("accessToken")); assertFalse(json.getJSONArray("chats").getJSONObject(0).has("attachments"))
    }
    @Test fun mergePreservesLocalConflictsAndDoesNotDuplicateRepeatedDownloads() {
        val remote = chat.copy(draft = "Remote draft")
        val server = CloudSnapshot(listOf(remote), listOf("Server memory"))
        val merged = CloudSnapshotCodec.mergeDownload(CloudSnapshot(listOf(chat), listOf("Phone memory")), server)
        assertEquals(1, merged.conflicts)
        assertEquals(0, merged.added)
        assertEquals(remote, merged.snapshot.chats.first())
        val copy = merged.snapshot.chats.last()
        assertTrue(copy.id.startsWith("unsynced-"))
        assertEquals(chat.copy(id = copy.id), copy)
        assertEquals(server.memory, merged.snapshot.memory)
        val repeated = CloudSnapshotCodec.mergeDownload(merged.snapshot, server)
        assertEquals(0, repeated.added)
        assertEquals(0, repeated.conflicts)
        assertEquals(merged.snapshot, repeated.snapshot)
    }
    @Test fun separateServerIdsWithIdenticalContentRemainSeparateChats() {
        val server = CloudSnapshot(listOf(chat, chat.copy(id = "other")), emptyList())
        val merged = CloudSnapshotCodec.mergeDownload(CloudSnapshot(listOf(chat), emptyList()), server)
        assertEquals(server, merged.snapshot)
        assertEquals(1, merged.added)
        assertEquals(0, merged.conflicts)
    }
    @Test fun downloadKeepsPhoneOnlyChatsAndRestoresAnEmptyServerMemory() {
        val local = CloudSnapshot(listOf(chat), listOf("Phone note"))
        val merged = CloudSnapshotCodec.mergeDownload(local, CloudSnapshot(emptyList(), emptyList()))
        assertEquals(local.chats, merged.snapshot.chats)
        assertTrue(merged.snapshot.memory.isEmpty())
        assertEquals(listOf("Phone note"), local.memory)
    }
    @Test fun conflictingCopyRetainsExtendedMetadataAndDoesNotOverwriteACollidingId() {
        val raw = javaClass.getResourceAsStream("/cloud-snapshot-v1.json")!!.bufferedReader().use { it.readText() }
        val local = CloudSnapshotCodec.decode(org.json.JSONObject(raw))
        val original = local.chats.first()
        val remote = local.copy(chats = listOf(original.copy(draft = "New server draft")))
        val first = CloudSnapshotCodec.mergeDownload(local, remote)
        val copy = first.snapshot.chats.single { it.id.startsWith("unsynced-") }
        assertEquals(original.copy(id = copy.id), copy)
        val collision = chat.copy(id = copy.id, draft = "Unrelated chat")
        val merged = CloudSnapshotCodec.mergeDownload(local.copy(chats = local.chats + collision), remote)
        assertEquals(collision, merged.snapshot.chats.single { it.id == copy.id })
        assertEquals(original.copy(id = copy.id + "-1"), merged.snapshot.chats.single { it.id == copy.id + "-1" })
    }
    @Test fun oversizedUnionAndConflictCopyFailWithoutMutatingInputs() {
        val local = CloudSnapshot((0 until 100).map { chat.copy(id = "chat-$it") }, listOf("Phone note"))
        for (remote in listOf(
            CloudSnapshot(listOf(chat.copy(id = "new")), listOf("Server note")),
            CloudSnapshot(listOf(local.chats.first().copy(draft = "Conflicting draft")), listOf("Server note")))) {
            try { CloudSnapshotCodec.mergeDownload(local, remote); fail("Oversized union accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(100, local.chats.size)
            assertEquals("Local draft", local.chats.first().draft)
            assertEquals(listOf("Phone note"), local.memory)
        }
    }
    @Test fun individuallyValidSnapshotsCannotExceedCombinedByteLimit() {
        val messages = (0 until 3).map { ChatMessage(id = "message-$it", text = "x".repeat(800000), isUser = true) }
        val local = CloudSnapshot(listOf(chat.copy(messages = messages)), emptyList())
        val remote = CloudSnapshot(listOf(chat.copy(id = "other", messages = messages)), emptyList())
        CloudSnapshotCodec.encode(local)
        CloudSnapshotCodec.encode(remote)
        try { CloudSnapshotCodec.mergeDownload(local, remote); fail("Oversized bytes accepted") }
        catch (_: IllegalArgumentException) { }
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

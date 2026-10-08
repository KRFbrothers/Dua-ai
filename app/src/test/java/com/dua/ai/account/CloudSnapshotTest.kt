package com.dua.ai.account

import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
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
}

package com.dua.ai.history

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.model.ChatMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatPinPersistenceTest {
    private val chat = SavedChat("chat", "Dua", "Draft", listOf(ChatMessage(id = "message", text = "Question", isUser = true, model = "Dua")), 10, customTitle = "Notes", pinned = true)
    @Test fun pinSurvivesCacheCloudBackupAndImportedCopies() {
        assertEquals(chat, ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), chat.id)).second.single())
        val snapshot = CloudSnapshot(listOf(chat), listOf("Memory"))
        assertEquals(snapshot, CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(snapshot)))
        val restored = ChatHistoryCodec.parseImport(ChatHistoryCodec.exportBackup(listOf(chat))).single()
        assertEquals(chat, restored)
        val copied = ChatHistoryCodec.importCopies(emptyList(), listOf(restored)).single()
        assertTrue(copied.pinned); assertNotEquals(chat.id, copied.id)
        assertEquals(chat.title, copied.title); assertEquals(chat.messages.single().text, copied.messages.single().text)
        val unpinned = chat.copy(pinned = false)
        assertFalse(CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(snapshot.copy(chats = listOf(unpinned)))).chats.single().pinned)
    }
    @Test fun legacyDataDefaultsToUnpinnedAndMalformedFlagsFailBeforeImport() {
        val legacy = JSONObject(ChatHistoryCodec.export(listOf(chat), exportedAt = 0))
        val record = legacy.getJSONArray("chats").getJSONObject(0)
        record.remove("pinned")
        assertFalse(ChatHistoryCodec.parseImport(legacy.toString()).single().pinned)
        val cache = JSONObject(ChatHistoryCodec.encode(listOf(chat), chat.id))
        cache.getJSONArray("chats").getJSONObject(0).remove("pinned")
        assertFalse(ChatHistoryCodec.decode(cache.toString()).second.single().pinned)
        for (bad in listOf("true", 1, JSONObject.NULL, JSONObject())) {
            record.put("pinned", bad)
            assertThrows(IllegalArgumentException::class.java) { ChatHistoryCodec.parseImport(legacy.toString()) }
            cache.getJSONArray("chats").getJSONObject(0).put("pinned", bad)
            assertTrue(runCatching { ChatHistoryCodec.decode(cache.toString()) }.isFailure)
        }
    }
    @Test fun reviewedDownloadUsesServerPinWithoutCreatingAContentConflict() {
        val remote = CloudSnapshot(listOf(chat.copy(pinned = false)), emptyList())
        val merged = CloudSnapshotCodec.mergeDownload(CloudSnapshot(listOf(chat), emptyList()), remote)
        assertEquals(0, merged.conflicts); assertEquals(remote, merged.snapshot)
    }
}

package com.dua.ai.setup

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatPinSyncTest {
    private val chat = SavedChat("chat", "Dua", "Draft", listOf(ChatMessage(id = "u", text = "Question", isUser = true)), 10)
    private fun snapshot(pinned: Boolean) = CloudSnapshot(listOf(chat.copy(pinned = pinned)), emptyList())
    @Test fun localPinAndUnpinPropagateWithoutCreatingConversationCopies() {
        for (base in listOf(false, true)) {
            val original = snapshot(base)
            val local = snapshot(!base)
            val merged = AutomationSyncPolicy.merge(local, original, AutomationSyncPolicy.baseline(original)).snapshot
            assertEquals(local, merged); assertEquals(1, merged.chats.size)
            assertEquals(chat.updatedAt, merged.chats.single().updatedAt)
            assertEquals(chat.messages, merged.chats.single().messages)
            assertEquals(local, AutomationSyncPolicy.merge(local, merged, AutomationSyncPolicy.baseline(merged)).snapshot)
        }
    }
    @Test fun remotePinWinsUnchangedCacheWhileFirstReadProtectsNewLocalEdits() {
        assertEquals(snapshot(true), AutomationSyncPolicy.merge(snapshot(false), snapshot(true), AutomationSyncPolicy.baseline(snapshot(false))).snapshot)
        assertEquals(snapshot(false), AutomationSyncPolicy.merge(snapshot(true), snapshot(false), AutomationSyncPolicy.baseline(snapshot(true))).snapshot)
        assertEquals(snapshot(false), AutomationSyncPolicy.merge(snapshot(true), snapshot(false), null).snapshot)
        assertEquals(snapshot(true), AutomationSyncPolicy.merge(snapshot(true), snapshot(false), null, AutomationSyncPolicy.baseline(snapshot(false))).snapshot)
    }
    @Test fun pinMergesIntoLatestServerContentWhileRealOfflineEditsStillHaveRecoveryCopies() {
        val baseline = AutomationSyncPolicy.baseline(snapshot(false))
        val remote = snapshot(false).copy(chats = listOf(chat.copy(draft = "Server draft")))
        val merged = AutomationSyncPolicy.merge(snapshot(true), remote, baseline).snapshot
        assertEquals(listOf(chat.copy(draft = "Server draft", pinned = true)), merged.chats)
        val edited = snapshot(true).copy(chats = listOf(chat.copy(draft = "Offline draft", pinned = true)))
        val conflict = AutomationSyncPolicy.merge(edited, remote, baseline).snapshot
        assertEquals(2, conflict.chats.size)
        assertEquals("Server draft", conflict.chats.first().draft)
        assertTrue(conflict.chats.any { it.id.startsWith("unsynced-") && it.draft == "Offline draft" })
    }
    @Test fun oldContentSignaturesStayValidAndPinMetadataParticipatesInEquality() {
        val json = JSONObject(ChatHistoryCodec.export(listOf(chat), exportedAt = 0, includeServerFields = true))
        val item = json.getJSONArray("chats").getJSONObject(0)
        item.remove("id"); item.remove("pinned"); item.remove("projectId")
        item.getJSONArray("messages").getJSONObject(0).remove("id")
        assertEquals(AutomationSyncPolicy.digest(json.toString()), AutomationSyncPolicy.signature(chat))
        assertEquals(AutomationSyncPolicy.signature(chat), AutomationSyncPolicy.signature(chat.copy(pinned = true)))
        assertFalse(AutomationSyncPolicy.same(snapshot(false), snapshot(true)))
    }
    @Test fun baselinePinsRoundTripAndRejectUnknownDuplicateAndMalformedIds() {
        val owner = "11111111-1111-4111-8111-111111111111"
        val record = AutomationSyncRecord(true, owner, 3, AutomationSyncPolicy.baseline(snapshot(true)))
        val raw = AutomationSyncRecordCodec.encode(record, "https://dua.test")
        assertEquals(record, AutomationSyncRecordCodec.decode(raw, "https://dua.test"))
        val old = JSONObject(raw).apply { getJSONObject("baseline").remove("pinnedChatIds") }
        assertEquals(emptySet<String>(), AutomationSyncRecordCodec.decode(old.toString(), "https://dua.test").baseline!!.pinnedChatIds)
        for (pins in listOf("[\"unknown\"]", "[\"chat\",\"chat\"]", "[1]", "null")) {
            val broken = JSONObject(raw).apply { getJSONObject("baseline").put("pinnedChatIds", org.json.JSONTokener(pins).nextValue()) }
            assertTrue(runCatching { AutomationSyncRecordCodec.decode(broken.toString(), "https://dua.test") }.isFailure)
        }
    }
}

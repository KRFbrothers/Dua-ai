package com.dua.ai.account

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.ReplyEvent
import com.dua.ai.network.StreamAccumulator
import com.dua.ai.setup.AutomationSyncPolicy
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplyPersistenceTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val user = ChatMessage(id = "new-user", text = "Five tips", isUser = true, model = "Dua")
    private val reply = ChatMessage(id = "new-reply", text = "A complete reply.", isUser = false, model = "Dua")
    private fun metadata() = JSONObject().put("id", "chat-12345").put("model", "Dua").put("projectId", "personal")
        .put("draft", "").put("updatedAt", 1000).put("pinned", false).put("title", "Server title")
    private fun receipt(answer: ChatMessage = reply): ReplySaved {
        val chat = JSONObject(ChatHistoryCodec.export(listOf(SavedChat("chat-12345", "Dua", "", listOf(user, answer), 1000)), exportedAt = 0))
            .getJSONArray("chats").getJSONObject(0)
        val message = chat.getJSONArray("messages").getJSONObject(1)
        return ReplySaved(owner, "chat-12345", reply.id, user.id, 4, metadata(), ReplyPersistenceCodec.digest(JSONArray().put(message)),
            answer.thinkingSeconds, JSONObject().put("title", JSONObject.NULL).put("pinned", false).put("buildRepository", JSONObject.NULL))
    }
    @Test fun sharedBackendDigestMatchesAcrossUnicodeKeyOrderAndNumericRepresentations() {
        val raw = javaClass.getResourceAsStream("/reply-digest-v1.json")!!.bufferedReader().use { it.readText() }
        val fixture = JSONObject(raw)
        assertEquals(fixture.getString("digest"), ReplyPersistenceCodec.digest(fixture.getJSONArray("messages")))
        val left = JSONArray().put(JSONObject().put("x", 2).put("y", "✨"))
        val right = JSONArray().put(JSONObject().put("y", "✨").put("x", 2.0))
        assertEquals(ReplyPersistenceCodec.digest(left), ReplyPersistenceCodec.digest(right))
        assertNotEquals(ReplyPersistenceCodec.digest(left), ReplyPersistenceCodec.digest(JSONArray().put(JSONObject().put("x", 3).put("y", "✨"))))
    }
    @Test fun requestBindsOwnerStableIdsAndRetryPrefixWithoutDuplicatingConversationContent() {
        val base = listOf(user, reply)
        val created = ReplyPersistenceCodec.create("chat-12345", owner, base, user, reply.id, listOf(user), "", replacing = true)
        assertEquals(owner, created.json.getString("ownerId")); assertEquals(user.id, created.json.getJSONObject("user").getString("id"))
        assertEquals(user.id, created.json.getJSONArray("turnIds").getString(0)); assertTrue(created.json.getBoolean("replaceReply"))
        assertEquals(ReplyPersistenceCodec.digest(JSONArray()), created.json.getString("prefixHash"))
        assertFalse(created.json.toString().contains(reply.text))
    }
    @Test fun confirmedReceiptPreservesExactContentAndServerMetadataWithoutManufacturingSyncConflicts() {
        val confirmed = ReplyPersistenceCodec.confirmedChat(receipt(), listOf(user, reply))
        assertEquals(1000, confirmed.updatedAt); assertEquals("Server title", confirmed.customTitle)
        assertEquals(listOf(user.id, reply.id), confirmed.messages.map { it.id })
        val server = CloudSnapshot(listOf(confirmed), emptyList())
        assertTrue(AutomationSyncPolicy.same(server, CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(server))))
        assertEquals(0, CloudSnapshotCodec.mergeDownload(server, server).conflicts)
    }
    @Test fun mismatchedReceiptContentOrIdentityCannotReplaceCachedHistory() {
        assertTrue(runCatching { ReplyPersistenceCodec.confirmedChat(receipt(), listOf(user, reply.copy(text = "Different answer"))) }.isFailure)
        assertTrue(runCatching { ReplyPersistenceCodec.confirmedChat(receipt(), listOf(user.copy(id = "wrong-user"), reply)) }.isFailure)
        assertTrue(runCatching { ReplyPersistenceCodec.confirmedChat(receipt(), listOf(user, reply.copy(state = "stopped"))) }.isFailure)
        val bad = receipt().copy(metadata = JSONObject(metadata().toString()).put("id", "wrong-chat"))
        assertTrue(runCatching { ReplyPersistenceCodec.confirmedChat(bad, listOf(user, reply)) }.isFailure)
    }
    @Test fun acknowledgementThinkingTimeOverridesLocalTimingAndThinkingIsPreciselyBounded() {
        val answer = reply.copy(thinking = "x".repeat(ChatMessage.MAX_THINKING_CHARS), thinkingSeconds = 7)
        val stream = StreamAccumulator(0) { 100000 }
        stream.apply(ReplyEvent.Thinking("x".repeat(ChatMessage.MAX_THINKING_CHARS + 1000)))
        stream.apply(ReplyEvent.Text(reply.text)); stream.apply(ReplyEvent.Saved(receipt(answer))); stream.apply(ReplyEvent.Done(false))
        assertEquals(ChatMessage.MAX_THINKING_CHARS, stream.snapshot().thinking!!.length)
        assertEquals(7, stream.snapshot().thinkingSeconds)
    }
    @Test fun persistenceCapabilityRequiresExplicitTrueAndDefaultsOffForOlderBackends() {
        assertFalse(IntegrationCapabilities.configuration(JSONObject(), IntegrationState(serverRepliesEnabled = true)).serverRepliesEnabled)
        assertFalse(IntegrationCapabilities.configuration(JSONObject("{\"history\":{\"persistReplies\":\"true\"}}"), IntegrationState()).serverRepliesEnabled)
        assertTrue(IntegrationCapabilities.configuration(JSONObject("{\"history\":{\"persistReplies\":true}}"), IntegrationState()).serverRepliesEnabled)
    }
    @Test fun receiptAdoptsConcurrentServerMetadataButKeepsUnsentLocalEditsForReview() {
        val saved = receipt()
        val server = ReplyPersistenceCodec.confirmedChat(saved, listOf(user, reply)).copy(pinned = true, draft = "Server draft", buildRepository = "owner/server")
        val unchanged = server.copy(customTitle = null, pinned = false, draft = "", buildRepository = null)
        assertEquals(server, ReplyPersistenceCodec.cachedMetadata(server, unchanged, "", null, saved))
        val edited = unchanged.copy(customTitle = "Phone title", pinned = true, buildRepository = "owner/local")
        val cached = ReplyPersistenceCodec.cachedMetadata(server.copy(pinned = false), edited, "Unsent phone draft", "owner/local", saved)
        assertEquals("Phone title", cached.customTitle); assertTrue(cached.pinned); assertEquals("Unsent phone draft", cached.draft)
        assertEquals("owner/local", cached.buildRepository); assertEquals(server.messages, cached.messages); assertEquals(server.updatedAt, cached.updatedAt)
    }
    @Test fun malformedSaveReceiptCannotClaimAValidOwnerOrRevision() {
        val valid = JSONObject().put("ownerId", owner).put("chatId", "chat-12345").put("replyId", reply.id).put("userId", user.id)
            .put("revision", 1).put("chat", metadata()).put("replyHash", "a".repeat(64)).put("original", JSONObject())
        for ((key, value) in listOf("ownerId" to "-".repeat(36), "revision" to 0, "revision" to 1.5, "replyHash" to "invalid", "thinkingSeconds" to 86401)) {
            val bad = JSONObject(valid.toString()).put(key, value)
            assertTrue(runCatching { ReplyPersistenceCodec.receipt(bad) }.isFailure)
        }
    }
}

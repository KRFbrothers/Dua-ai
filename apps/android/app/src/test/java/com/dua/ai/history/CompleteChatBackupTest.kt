package com.dua.ai.history

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CompleteChatBackupTest {
    private fun fixture(): SavedChat = CloudSnapshotCodec.decode(JSONObject(javaClass
        .getResourceAsStream("/cloud-snapshot-v1.json")!!.bufferedReader().use { it.readText() })).chats.single()
    private fun root() = JSONObject(ChatHistoryCodec.exportBackup(listOf(fixture()), 456))
    private fun rejected(root: JSONObject) {
        try { ChatHistoryCodec.parseImport(root.toString()); fail("Lossy or invalid metadata accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun fullBackupRestoresAllServerMetadataWithoutMemoryOrFileBytes() {
        val chat = fixture()
        val raw = ChatHistoryCodec.exportBackup(listOf(chat), 456)
        val restored = ChatHistoryCodec.readBackup(ByteArrayInputStream(raw.toByteArray())).chats.single()
        assertEquals(chat, restored)
        val json = JSONObject(raw)
        assertEquals("dua-chat-export", json.getString("format"))
        assertEquals(1, json.getInt("version"))
        assertFalse(json.has("memory"))
        assertFalse(raw.contains("accessToken")); assertFalse(raw.contains("refreshToken"))
        assertFalse(raw.contains("\"attachments\""))
        assertFalse(json.getJSONArray("chats").getJSONObject(0).getJSONArray("messages")
            .getJSONObject(1).getJSONArray("media").getJSONObject(0).has("url"))
        assertTrue(restored.messages[1].proposal!!.complete)
        assertEquals(chat.messages[2].proposal!!.pullRequest, restored.messages[2].proposal!!.pullRequest)
    }

    @Test fun copiedImportsKeepMetadataAndRemapTheSummaryToTheirOwnMessageIds() {
        val chat = fixture()
        val imported = ChatHistoryCodec.parseImport(ChatHistoryCodec.exportBackup(listOf(chat)))
        val first = ChatHistoryCodec.importCopies(listOf(chat), imported)
        val second = ChatHistoryCodec.importCopies(first, imported)
        assertEquals(chat, second.first())
        assertEquals(3, second.map { it.id }.distinct().size)
        assertEquals(9, second.flatMap { it.messages }.map { it.id }.distinct().size)
        for (copy in second.drop(1)) {
            assertNotEquals(chat.id, copy.id)
            assertEquals(copy.messages.first().id, copy.summary!!.throughId)
            assertEquals(chat.summary!!.text, copy.summary!!.text)
            assertEquals(chat.buildRepository, copy.buildRepository)
            copy.messages.zip(chat.messages).forEach { (actual, original) -> assertEquals(original.copy(id = actual.id), actual) }
            val cached = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(copy), copy.id)).second.single()
            assertEquals(copy, cached)
        }
        CloudSnapshotCodec.encode(CloudSnapshot(second, emptyList()))
    }

    @Test fun oldTextOnlyExportsAndExplicitRedactedExportsRemainReadable() {
        val raw = ChatHistoryCodec.export(listOf(fixture()), includeServerFields = false)
        val restored = ChatHistoryCodec.readBackup(ByteArrayInputStream(raw.toByteArray())).chats.single()
        assertNull(restored.summary); assertNull(restored.buildRepository)
        assertTrue(restored.messages.first().files.isEmpty())
        assertTrue(restored.messages[1].codeRuns.isEmpty())
        assertEquals(fixture().messages.map { it.text }, restored.messages.map { it.text })
    }

    @Test fun duplicateIdsAndDanglingSummariesAreRejectedBeforeImportCopies() {
        val duplicate = root(); val messages = duplicate.getJSONArray("chats").getJSONObject(0).getJSONArray("messages")
        messages.getJSONObject(1).put("id", messages.getJSONObject(0).getString("id")); rejected(duplicate)
        val summary = root(); summary.getJSONArray("chats").getJSONObject(0).getJSONObject("summary").put("throughId", "missing"); rejected(summary)
        for (value in listOf<Any>(42, "", JSONObject.NULL)) {
            val bad = root(); bad.getJSONArray("chats").getJSONObject(0).getJSONArray("messages").getJSONObject(0).put("id", value); rejected(bad)
        }
    }

    @Test fun unsafeOrTruncatedExtendedMetadataIsRejectedRatherThanSilentlyDropped() {
        val mutations = listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("messages").getJSONObject(0).put("attachmentText", "x".repeat(32_001)) },
            { it.getJSONArray("messages").getJSONObject(0).getJSONArray("files").getJSONObject(0).put("path", "../private.pdf") },
            { it.getJSONArray("messages").getJSONObject(0).getJSONArray("files").getJSONObject(0).put("size", "12") },
            { it.getJSONArray("messages").getJSONObject(1).put("codeRuns", JSONArray().apply { repeat(11) { put(JSONObject().put("code", "print(1)")) } }) },
            { it.getJSONArray("messages").getJSONObject(1).getJSONArray("codeRuns").getJSONObject(0).put("code", "x".repeat(20_001)) },
            { it.getJSONArray("messages").getJSONObject(1).getJSONObject("build").getJSONArray("files").getJSONObject(0).put("path", "../secret") },
            { it.getJSONArray("messages").getJSONObject(1).getJSONArray("media").getJSONObject(0).put("path", "../image.png") },
            { it.getJSONArray("messages").getJSONObject(1).getJSONObject("proposal").getJSONArray("files").getJSONObject(0).put("path", ".github/workflows/run.yml") },
            { it.getJSONArray("messages").getJSONObject(2).getJSONObject("proposal").getJSONObject("pullRequest").put("url", "https://evil.example/pull/123") },
            { it.put("buildRepository", "bad repo") },
            { it.put("summary", "not an object") },
            { it.getJSONArray("messages").getJSONObject(0).put("codeRuns", JSONArray()) }
        )
        for (mutate in mutations) { val bad = root(); mutate(bad.getJSONArray("chats").getJSONObject(0)); rejected(bad) }
    }

    @Test fun combinedCountAndByteLimitsRejectImportsWithoutMutatingExistingChats() {
        val chat = fixture()
        val existing = (0 until 100).map { chat.copy(id = "chat-$it", messages = emptyList(), summary = null) }
        try { ChatHistoryCodec.importCopies(existing, listOf(chat)); fail("Oversized combined chat count accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(100, existing.size)
        val big = chat.copy(messages = List(3) { chat.messages.first().copy(id = "big-$it", text = "x".repeat(800_000)) }, summary = null)
        ChatHistoryCodec.exportBackup(listOf(big))
        try { ChatHistoryCodec.importCopies(listOf(big), listOf(big)); fail("Oversized combined bytes accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(3, big.messages.size)
    }

    @Test fun backupPreparationRejectsFilesThatTheImporterCannotRead() {
        val chat = fixture()
        for (chats in listOf(List(101) { chat.copy(id = "chat-$it") }, listOf(chat.copy(draft = "x".repeat(8001))))) {
            try { ChatHistoryCodec.exportBackup(chats); fail("Unimportable backup prepared") }
            catch (_: IllegalArgumentException) { }
        }
    }
}

package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ChatImportTest {
    private val existing = SavedChat("existing", "Gemini", "अधूरा 😀", listOf(ChatMessage(text = "नमस्ते", isUser = true)), 123)
    private fun export() = ChatHistoryCodec.export(listOf(existing), 456)
    private fun reject(raw: String) {
        try { ChatHistoryCodec.parseImport(raw); fail("Invalid export accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun importRestoresWebFormatRolesDraftsUnicodeAndStoppedState() {
        val raw = """{"format":"dua-chat-export","version":1,"exportedAt":456,"chats":[{"id":"web","model":"Dua","draft":"Draft 😀","updatedAt":123,"messages":[{"role":"user","text":"नमस्ते","state":"complete"},{"role":"model","text":"Response stopped","state":"stopped"}]}]}"""
        val parsed = ChatHistoryCodec.readImport(ByteArrayInputStream(("\uFEFF" + raw).toByteArray()))
        assertEquals("Draft 😀", parsed[0].draft); assertEquals("नमस्ते", parsed[0].messages[0].text)
        assertTrue(parsed[0].messages[0].isUser); assertFalse(parsed[0].messages[1].isUser)
        assertEquals("stopped", parsed[0].messages[1].state)
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(parsed, "web")).second
        assertEquals("stopped", restored[0].messages[1].state)
        assertEquals("stopped", JSONObject(ChatHistoryCodec.export(restored)).getJSONArray("chats").getJSONObject(0).getJSONArray("messages").getJSONObject(1).getString("state"))
    }

    @Test fun nativeExportRoundTripsAndImportAddsCopiesWithoutReplacingExisting() {
        val parsed = ChatHistoryCodec.parseImport(export())
        assertEquals(existing.draft, parsed[0].draft); assertEquals(existing.messages[0].text, parsed[0].messages[0].text)
        val first = ChatHistoryCodec.importCopies(listOf(existing), parsed)
        val second = ChatHistoryCodec.importCopies(first, parsed)
        assertEquals(3, second.size); assertEquals(3, second.map { it.id }.distinct().size)
        assertEquals(existing, second.first()); assertNotEquals(existing.messages[0].id, first[1].messages[0].id)
        assertEquals(first[1].draft, second[2].draft)
    }

    @Test fun invalidSchemaVersionsTypesRolesLimitsAndDuplicateIdsAreRejected() {
        listOf("broken", "null", export() + " trailing", "[".repeat(65) + "0" + "]".repeat(65)).forEach(::reject)
        for ((key, value) in listOf("format" to "other", "version" to 2, "version" to "1", "exportedAt" to -1, "exportedAt" to 1.5)) {
            reject(JSONObject(export()).put(key, value).toString())
        }
        for ((key, value) in listOf("id" to "", "model" to "ChatGPT", "draft" to 4, "draft" to "x".repeat(8001), "updatedAt" to -1)) {
            val root = JSONObject(export()); root.getJSONArray("chats").getJSONObject(0).put(key, value); reject(root.toString())
        }
        for ((key, value) in listOf("role" to "system", "state" to "pending", "text" to JSONObject.NULL, "text" to "x".repeat(1048577))) {
            val root = JSONObject(export()); root.getJSONArray("chats").getJSONObject(0).getJSONArray("messages").getJSONObject(0).put(key, value); reject(root.toString())
        }
        val duplicate = JSONObject(export()); duplicate.getJSONArray("chats").put(duplicate.getJSONArray("chats").getJSONObject(0)); reject(duplicate.toString())
        val many = JSONObject(export()); val chat = many.getJSONArray("chats").getJSONObject(0)
        many.put("chats", JSONArray().apply { repeat(101) { put(JSONObject(chat.toString()).put("id", "id$it")) } }); reject(many.toString())
        val messages = JSONObject(export()); val item = messages.getJSONArray("chats").getJSONObject(0)
        val message = item.getJSONArray("messages").getJSONObject(0)
        item.put("messages", JSONArray().apply { repeat(501) { put(message) } }); reject(messages.toString())
    }

    @Test fun inputIsBoundedAndInvalidUtf8RejectedBeforeParsing() {
        for (bytes in listOf(byteArrayOf(0xff.toByte()), ByteArray(ChatHistoryCodec.MAX_IMPORT_BYTES + 1) { 32 })) {
            try { ChatHistoryCodec.readImport(ByteArrayInputStream(bytes)); fail("Invalid input accepted") }
            catch (_: IllegalArgumentException) { }
            catch (_: IllegalStateException) { }
        }
        assertTrue(ChatHistoryCodec.parseImport(ChatHistoryCodec.export(emptyList(), 0)).isEmpty())
    }
}

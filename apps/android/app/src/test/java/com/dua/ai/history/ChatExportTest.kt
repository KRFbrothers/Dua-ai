package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatExportTest {
    @Test fun exportUsesCommonFormatAndPreservesDraftsModelsHindiAndEmoji() {
        val record = SavedChat("id", "Gemini", "अधूरा 😀", listOf(ChatMessage(text = "नमस्ते", isUser = true), ChatMessage(text = "Hello", isUser = false)), 123)
        val export = JSONObject(ChatHistoryCodec.export(listOf(record), 456))
        assertEquals("dua-chat-export", export.getString("format")); assertEquals(1, export.getInt("version")); assertEquals(456, export.getLong("exportedAt"))
        val chat = export.getJSONArray("chats").getJSONObject(0)
        assertEquals("Gemini", chat.getString("model")); assertEquals("अधूरा 😀", chat.getString("draft"))
        val messages = chat.getJSONArray("messages")
        assertEquals("user", messages.getJSONObject(0).getString("role")); assertEquals("नमस्ते", messages.getJSONObject(0).getString("text"))
        assertEquals("model", messages.getJSONObject(1).getString("role")); assertEquals("complete", messages.getJSONObject(1).getString("state"))
        assertFalse(chat.has("attachments"))
    }
    @Test fun emptyExportAndClearedHistoryRemainValidAndReloadWithoutChats() {
        assertEquals(0, JSONObject(ChatHistoryCodec.export(emptyList())).getJSONArray("chats").length())
        val cleared = ChatHistoryCodec.decode(ChatHistoryCodec.encode(emptyList(), "new"))
        assertEquals("new", cleared.first); assertTrue(cleared.second.isEmpty())
    }
}

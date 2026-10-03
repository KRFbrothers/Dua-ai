package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ChatHistoryCodecTest {
    private val first = SavedChat("one", "Gemini", "अधूरा", listOf(
        ChatMessage(id = "message", text = "नमस्ते", isUser = true, model = "Gemini")
    ), 10)
    @Test fun multipleChatsAndActiveSelectionRoundTrip() {
        val records = listOf(first, SavedChat("two", "Dua", "Second draft", emptyList(), 20))
        val (active, restored) = ChatHistoryCodec.decode(ChatHistoryCodec.encode(records, "two"))
        assertEquals("two", active)
        assertEquals(records, restored)
        assertEquals("नमस्ते", restored[0].title)
        assertEquals("Draft: अधूरा", restored[0].preview)
    }
    @Test fun newEmptyChatDoesNotDestroyOlderRecords() {
        val (active, restored) = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(first), "new"))
        assertEquals("new", active)
        assertEquals(listOf(first), restored)
    }
    @Test fun deletedChatStaysDeletedAfterReload() {
        val records = listOf(first, first.copy(id = "two"))
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(records.filterNot { it.id == "one" }, "two")).second
        assertEquals(listOf("two"), restored.map { it.id })
    }
    @Test fun corruptDataIsRejected() {
        assertTrue(runCatching { ChatHistoryCodec.decode("broken") }.isFailure)
        assertTrue(runCatching { ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(first.copy(model = "unsupported")), "one")) }.isFailure)
    }
}

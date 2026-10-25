package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentChatsTest {
    private fun sent(id: String, at: Long, text: String = "hi") = SavedChat(id, "Dua", "", listOf(ChatMessage(text = text, isUser = true)), at)

    @Test fun hidesChatsWithoutSentUserMessage() {
        val chats = listOf(sent("a", 1), SavedChat("empty", "Dua", "", emptyList(), 2), SavedChat("draft", "Dua", "typing", emptyList(), 3),
            SavedChat("assistantOnly", "Dua", "", listOf(ChatMessage(text = "hello", isUser = false)), 4))
        assertEquals(listOf("a"), HistoryGrouping.recent(chats).map { it.id })
    }

    @Test fun keepsRenamedChatEvenWithoutMessages() {
        val renamed = SavedChat("r", "Dua", "", emptyList(), 1, customTitle = "Ideas")
        assertEquals(listOf("r"), HistoryGrouping.recent(listOf(renamed)).map { it.id })
    }

    @Test fun dedupesSameIdKeepingNewestAndKeepsDistinctChats() {
        val result = HistoryGrouping.recent(listOf(sent("x", 1), sent("x", 9), sent("y", 5)))
        assertEquals(listOf("x" to 9L, "y" to 5L), result.map { it.id to it.updatedAt })
    }

    @Test fun doesNotMutateInput() {
        val input = listOf(SavedChat("d", "Dua", "", emptyList(), 1), sent("a", 2))
        HistoryGrouping.recent(input)
        assertEquals(2, input.size)
    }
}

package com.dua.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEditsTest {
    private val u1 = ChatMessage(text = "First question", isUser = true)
    private val a1 = ChatMessage(text = "First answer", isUser = false)
    private val u2 = ChatMessage(text = "Second question", isUser = true)
    private val a2 = ChatMessage(text = "Second answer", isUser = false)
    private val chat = listOf(u1, a1, u2, a2)

    @Test fun editingKeepsOnlyEarlierTurns() {
        assertEquals(listOf(u1, a1), MessageEdits.keptBefore(chat, u2.id))
        assertEquals(emptyList<ChatMessage>(), MessageEdits.keptBefore(chat, u1.id))
        assertEquals(listOf(u2.id, a2.id), MessageEdits.removedIds(chat, u2.id))
    }

    @Test fun onlyPlainTextUserMessagesAreEditable() {
        assertTrue(MessageEdits.editable(u1))
        assertFalse(MessageEdits.editable(a1))
        assertFalse(MessageEdits.editable(u1.copy(text = "See this\nFiles: a.pdf")))
        assertFalse(MessageEdits.editable(u1.copy(attachmentText = "file text")))
        assertFalse(MessageEdits.editable(u1.copy(state = "streaming")))
        assertNull(MessageEdits.keptBefore(chat, a1.id))
        assertNull(MessageEdits.keptBefore(chat, "missing"))
    }
}

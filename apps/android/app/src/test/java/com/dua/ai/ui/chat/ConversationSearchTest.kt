package com.dua.ai.ui.chat

import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRun
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ConversationSearchTest {
    @Test fun findsOlderTurnsInOrderWithOneResultPerMessage() {
        val messages = listOf(ChatMessage("first", "Invoice INVOICE invoice", true),
            ChatMessage("middle", "Unrelated", false), ChatMessage("last", "Your invoice", false))
        val matches = ConversationSearch.search(messages, " invoice ")
        assertEquals(listOf("first", "last"), matches.map { it.messageId })
        assertEquals(listOf(0, 2), matches.map { it.messageIndex })
        assertEquals("Invoice", matches.first().snippet.let { it.text.substring(it.matchStart, it.matchEnd) })
    }
    @Test fun searchesLiteralMarkdownCodeAndSavedOutput() {
        val messages = listOf(ChatMessage("markdown", "```kotlin\n.*[value]\n```", false),
            ChatMessage("code", "Done", false, codeRuns = listOf(CodeRun(code = "print('.*[value]')"))),
            ChatMessage("output", "Done", false, codeRuns = listOf(CodeRun(code = "print(1)", output = ".*[value]"))))
        val found = ConversationSearch.search(messages, ".*[value]")
        assertEquals(3, found.size)
        assertTrue(found[1].snippet.text.startsWith("Code: "))
        assertTrue(found[2].snippet.text.startsWith("Output: "))
    }
    @Test fun excludesThinkingHiddenAttachmentContextAndUserCodeRuns() {
        val message = ChatMessage("hidden", "Nothing here", true, thinking = "secret", attachmentText = "secret",
            codeRuns = listOf(CodeRun(code = "secret")))
        assertTrue(ConversationSearch.search(listOf(message), "secret").isEmpty())
        assertTrue(ConversationSearch.search(listOf(message), "   ").isEmpty())
    }
    @Test fun handlesLongTextEmojiAndMultilineSnippetsWithoutBreakingSurrogatePairs() {
        val message = ChatMessage("long", "🙂".repeat(100) + "unique\nphrase" + "🙂".repeat(100), false)
        val found = ConversationSearch.search(listOf(message), "unique\nphrase").single().snippet
        assertEquals("unique phrase", found.text.substring(found.matchStart, found.matchEnd))
        assertTrue(found.text.length < 200)
        assertFalse(found.text.contains('\uFFFD'))
        found.text.forEachIndexed { i, c ->
            if (c.isHighSurrogate()) assertTrue(found.text.getOrNull(i + 1)?.isLowSurrogate() == true)
            if (c.isLowSurrogate()) assertTrue(found.text.getOrNull(i - 1)?.isHighSurrogate() == true)
        }
    }
    @Test fun queryIsBoundedAndCancellationStopsTheScan() {
        val text = "a".repeat(200)
        assertEquals(1, ConversationSearch.search(listOf(ChatMessage(text = text, isUser = true)), text + "b").size)
        var checks = 0
        assertThrows(CancellationException::class.java) {
            ConversationSearch.search(List(100) { ChatMessage(text = "text", isUser = true) }, "absent") {
                if (++checks == 3) throw CancellationException()
            }
        }
        assertEquals(3, checks)
    }
}

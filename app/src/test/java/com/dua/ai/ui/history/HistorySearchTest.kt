package com.dua.ai.ui.history

import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ChatSummary
import com.dua.ai.model.CodeRun
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class HistorySearchTest {
    private fun chat(messages: List<ChatMessage>, draft: String = "") = SavedChat("one", "Dua", draft, messages, 1)
    private fun message(text: String, user: Boolean = false) = ChatMessage(text = text, isUser = user)

    @Test fun findsOlderUserAndAssistantTurnsBeyondTitleAndLastReply() {
        val value = chat(listOf(message("x".repeat(90) + " invoice-2026", true), message("A Kotlin coroutine bug"), message("Thanks", true), message("Done")))
        assertFalse(value.title.contains("invoice")); assertFalse(value.preview.contains("coroutine"))
        assertTrue(HistorySearch.search(listOf(value), "invoice-2026").single().snippet!!.startsWith("You: "))
        val match = HistorySearch.search(listOf(value), "  COROUTINE ").single()
        assertEquals("coroutine", match.snippet!!.substring(match.matchStart, match.matchEnd))
        assertTrue(match.snippet.startsWith("Dua: "))
        assertEquals(listOf(value), HistoryGrouping.filter(listOf(value), "coroutine"))
    }

    @Test fun draftAndNewestMatchingReplyTakePriorityWithOneResultPerChat() {
        val value = chat(listOf(message("Budget old", true), message("Budget current")), "Budget draft")
        assertEquals("Draft: Budget draft", HistorySearch.search(listOf(value), "budget").single().snippet)
        assertEquals("Dua: Budget current", HistorySearch.search(listOf(value.copy(draft = "")), "budget").single().snippet)
        assertEquals(HistoryMatch(value), HistorySearch.search(listOf(value), " \t ").single())
        assertEquals("Title: Draft", HistorySearch.search(listOf(chat(emptyList())), "draft").single().snippet)
    }

    @Test fun codeFencesAndCodeExecutionOutputAreSearchedLiterallyWithoutRegexOrCrossFieldMatches() {
        val value = chat(listOf(message("```kotlin\nval token = \"[a+b]*\"\n```", true),
            message("Result").copy(codeRuns = listOf(CodeRun("python", "print(total_revenue)", "Revenue: 4500")))))
        assertEquals(1, HistorySearch.search(listOf(value), "[a+b]*").size)
        assertTrue(HistorySearch.search(listOf(value), "total_revenue").single().snippet!!.startsWith("Code: "))
        assertTrue(HistorySearch.search(listOf(value), "4500").single().snippet!!.startsWith("Output: "))
        assertTrue(HistorySearch.search(listOf(chat(listOf(message("alpha", true), message("beta")))), "alpha beta").isEmpty())
    }

    @Test fun snippetsStayBoundedAndPreserveEmojiAndHighlightedMultilineWhitespace() {
        val text = "🙂".repeat(60) + "Needle\n\tvalue" + "🙂".repeat(90)
        val match = HistorySearch.search(listOf(chat(listOf(message(text)))), "needle\n\tvalue").single()
        assertEquals("Needle value", match.snippet!!.substring(match.matchStart, match.matchEnd))
        assertTrue(match.snippet.length < 160)
        assertTrue(match.snippet.startsWith("Dua: …")); assertTrue(match.snippet.endsWith("…"))
        var index = 0
        while (index < match.snippet.length) {
            val char = match.snippet[index]
            if (char.isHighSurrogate()) { assertTrue(match.snippet[index + 1].isLowSurrogate()); index += 2 }
            else { assertFalse(char.isLowSurrogate()); index++ }
        }
    }

    @Test fun hiddenAttachmentReasoningSummaryAndProjectMetadataDoNotBecomeSearchResults() {
        val value = chat(listOf(message("Visible reply").copy(thinking = "secret-reasoning", attachmentText = "secret-file")), "Visible draft")
            .copy(summary = ChatSummary("secret-summary", "message-id"), buildRepository = "private/secret-repo", projectId = "secret-project")
        for (query in listOf("secret-reasoning", "secret-file", "secret-summary", "secret-repo", "secret-project")) {
            assertTrue(HistorySearch.search(listOf(value), query).isEmpty())
        }
    }

    @Test fun workerCancellationStopsScanningInsteadOfReturningStaleResults() {
        var checks = 0
        val values = listOf(chat(List(50) { message("Unmatched") }))
        try {
            HistorySearch.search(values, "invoice") { if (++checks == 3) throw CancellationException("New query") }
            fail("Should stop")
        } catch (_: CancellationException) { assertEquals(3, checks) }
    }
}

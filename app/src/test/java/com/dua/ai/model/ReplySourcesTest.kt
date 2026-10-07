package com.dua.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplySourcesTest {
    @Test fun splitsTheBackendSourcesFooterIntoChips() {
        val text = "Answer with **markdown**.\n\nSources:\n[1] Example page\nhttps://www.example.org/a\n\n[2] Docs\nhttps://docs.example.com/b?x=1"
        val parts = ReplySources.split(text)
        assertEquals("Answer with **markdown**.", parts.body)
        assertEquals(listOf(ReplySource(1, "Example page", "https://www.example.org/a"), ReplySource(2, "Docs", "https://docs.example.com/b?x=1")), parts.sources)
        assertEquals("example.org", parts.sources[0].domain)
    }

    @Test fun leavesTextUnchangedWithoutAFooter() {
        val parts = ReplySources.split("Just an answer. Sources: none")
        assertEquals("Just an answer. Sources: none", parts.body)
        assertTrue(parts.sources.isEmpty())
    }

    @Test fun keepsTheFooterWhenAnyEntryIsMalformedOrUnsafe() {
        listOf(
            "A\n\nSources:\n[1] Insecure\nhttp://example.org",
            "A\n\nSources:\n[1] Credentials\nhttps://user@example.org",
            "A\n\nSources:\nnot a source line",
            "A\n\nSources:\n[1] Ok\nhttps://example.org\n\n[2] Missing url",
        ).forEach { text ->
            val parts = ReplySources.split(text)
            assertEquals(text, parts.body)
            assertTrue(parts.sources.isEmpty())
        }
    }

    @Test fun usesTheDomainWhenATitleIsBlank() {
        val parts = ReplySources.split("A\n\nSources:\n[1] \nhttps://news.example.net/x")
        assertEquals("news.example.net", parts.sources.single().title)
    }
}

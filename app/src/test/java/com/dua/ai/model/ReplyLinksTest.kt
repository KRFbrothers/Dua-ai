package com.dua.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyLinksTest {
    private fun assertLinks(text: String, expected: List<String>): List<ReplyLink> {
        val links = ReplyLinks.find(text)
        assertEquals(expected, links.map { it.url })
        links.forEach { link ->
            assertEquals(link.url, text.substring(link.start, link.end))
            assertTrue(link.start >= 0 && link.end <= text.length)
        }
        return links
    }

    @Test fun proseTrimsSentencePunctuationAndPreservesBalancedParentheses() {
        val first = "https://example.org/report"
        val second = "https://example.org/wiki/Topic_(detail)"
        val text = "Read $first. Also ($second)."
        val links = assertLinks(text, listOf(first, second))
        assertEquals(ReplyLink(first, text.indexOf(first), text.indexOf(first) + first.length), links[0])
        assertEquals(ReplyLink(second, text.indexOf(second), text.indexOf(second) + second.length), links[1])
    }

    @Test fun canonicalFooterUrlLinesKeepTheirExactUrlPunctuation() {
        val first = "https://example.org/path_(v2)?item=done!"
        val second = "https://example.org/report."
        val text = "Answer.\n\nSources:\n[1] First source\n$first\n[2] Second source\n  $second  \n"
        val links = assertLinks(text, listOf(first, second))
        assertEquals(text.indexOf(first), links[0].start)
        assertEquals(text.indexOf(first) + first.length, links[0].end)
        assertEquals(text.indexOf(second), links[1].start)
        assertEquals(text.indexOf(second) + second.length, links[1].end)
    }

    @Test fun markdownAndQuotedProseHaveExactUrlRanges() {
        val first = "https://example.org/a?q=1#part"
        val second = "https://example.org/b"
        val third = "https://example.org/c"
        val text = "See [reference]($first), `$second`, and \"$third\"."
        assertLinks(text, listOf(first, second, third))
    }

    @Test fun repeatedLinksAndUnicodeTextPreserveEachOriginalRange() {
        val url = "https://example.org/read"
        val text = "📖 पढ़ें $url, फिर $url."
        val links = assertLinks(text, listOf(url, url))
        assertEquals(ReplyLink(url, text.indexOf(url), text.indexOf(url) + url.length), links[0])
        assertEquals(ReplyLink(url, text.lastIndexOf(url), text.lastIndexOf(url) + url.length), links[1])
    }

    @Test fun dangerousSchemesMalformedUrlsAndCredentialsAreRejected() {
        val unsafe = listOf(
            "http://example.org",
            "ftp://example.org",
            "javascript:alert(1)",
            "javascript:alert('https://evil.com')",
            "data:text/plain,https://evil.com",
            "https:///missing-host",
            "https://user:password@example.org",
            "https://%75ser@example.org",
            "https://user:https://evil.com@example.org",
            "https://user:'https://evil.com'@example.org",
            "http://user:https://evil.com@example.org",
            "https://example.org\\@evil.com",
            "prefixhttps://example.org"
        )
        unsafe.forEach { text ->
            assertEquals("Unsafe link extracted from $text", emptyList<ReplyLink>(), ReplyLinks.find(text))
        }
        val safe = "https://example.org/news"
        assertLinks(unsafe.joinToString("\n") + "\n$safe", listOf(safe))
    }

    @Test fun overlongUrlsAreRejectedWithoutExtractingAValidPrefix() {
        val prefix = "https://example.org/"
        val maximum = prefix + "x".repeat(2000 - prefix.length)
        val tooLong = maximum + "x"
        val text = "$maximum\n$tooLong"
        val links = assertLinks(text, listOf(maximum))
        assertEquals(ReplyLink(maximum, 0, 2000), links.single())
    }
}

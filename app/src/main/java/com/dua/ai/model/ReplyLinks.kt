package com.dua.ai.model

import com.dua.ai.setup.IntegrationCapabilities

data class ReplyLink(val url: String, val start: Int, val end: Int)

/** Finds safe HTTPS links with end-exclusive ranges in the original reply text. */
object ReplyLinks {
    // Consume the whole token before validating it. A rejected credential URL must
    // not expose a nested, apparently safe HTTPS address as a separate link.
    private val candidates = Regex("https://\\S+")

    fun find(text: String): List<ReplyLink> = candidates.findAll(text).mapNotNull { match ->
        val start = match.range.first
        if (!startsLink(text, start)) return@mapNotNull null
        val raw = match.value
        val url = if (occupiesLine(text, start, start + raw.length)) raw else trimProsePunctuation(raw)
        IntegrationCapabilities.safeUrl(url)?.let { ReplyLink(it, start, start + it.length) }
    }.toList()

    private fun startsLink(text: String, start: Int): Boolean {
        if (start > 0) {
            val previous = text[start - 1]
            if (previous.isLetterOrDigit() || previous in "._~:/@%+-") return false
        }
        var tokenStart = start
        while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) tokenStart--
        // Also reject addresses embedded inside another scheme, such as
        // javascript:alert('https://example.org') or a malformed authority.
        return (tokenStart until start).none { text[it] == ':' || text[it] == '@' }
    }

    private fun occupiesLine(text: String, start: Int, end: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val lineEnd = text.indexOf('\n', end).takeIf { it >= 0 } ?: text.length
        return text.substring(lineStart, start).isBlank() && text.substring(end, lineEnd).isBlank()
    }

    private fun trimProsePunctuation(raw: String): String {
        var end = raw.length
        var parentheses = raw.count { it == ')' } - raw.count { it == '(' }
        var brackets = raw.count { it == ']' } - raw.count { it == '[' }
        var braces = raw.count { it == '}' } - raw.count { it == '{' }
        while (end > 0) {
            when (raw[end - 1]) {
                '.', ',', ';', ':', '!', '?', '"', '\'', '`', '>', '*', '\u2019', '\u201d' -> end--
                ')' -> if (parentheses > 0) { parentheses--; end-- } else break
                ']' -> if (brackets > 0) { brackets--; end-- } else break
                '}' -> if (braces > 0) { braces--; end-- } else break
                else -> break
            }
        }
        return raw.substring(0, end)
    }
}

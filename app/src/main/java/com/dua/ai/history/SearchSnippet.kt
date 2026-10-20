package com.dua.ai.history

data class SearchSnippet(val text: String, val matchStart: Int, val matchEnd: Int)

/** Bounded literal-match context shared by history and in-conversation search. */
internal fun searchSnippet(text: String, needle: String, label: String): SearchSnippet? {
    if (needle.isEmpty()) return null
    val index = text.indexOf(needle, ignoreCase = true)
    if (index < 0) return null
    var start = (index - 32).coerceAtLeast(0)
    var end = (index + needle.length + 80).coerceAtMost(text.length)
    if (start > 0 && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
    if (end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end++
    val prefix = label + (if (start > 0) "…" else "") + text.substring(start, index).replace(searchWhitespace, " ")
    val found = text.substring(index, index + needle.length).replace(searchWhitespace, " ")
    val suffix = text.substring(index + needle.length, end).replace(searchWhitespace, " ") + if (end < text.length) "…" else ""
    return SearchSnippet(prefix + found + suffix, prefix.length, prefix.length + found.length)
}

private val searchWhitespace = Regex("\\s+")

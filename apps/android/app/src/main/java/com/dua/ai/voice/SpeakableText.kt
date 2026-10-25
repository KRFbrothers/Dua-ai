package com.dua.ai.voice

/**
 * Turns a markdown reply into text worth speaking: code blocks, tables, links, citation markers and the
 * "Sources" footer are not read aloud, and markdown symbols are dropped.
 */
object SpeakableText {
    private val FENCE = Regex("```[\\s\\S]*?(```|$)")
    private val SOURCES = Regex("\\n+(?:\\*\\*)?Sources:?(?:\\*\\*)?\\s*\\n[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]+)]\\((?:https?://|mailto:)[^)]*\\)")
    private val URL = Regex("https?://\\S+")
    private val CITATION = Regex("\\[(\\d{1,3})(?:\\s*,\\s*\\d{1,3})*]")
    private val TABLE_RULE = Regex("(?m)^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")
    private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
    private val BULLET = Regex("(?m)^\\s*(?:[-*+]|\\d{1,3}[.)])\\s+")
    private val QUOTE = Regex("(?m)^\\s*>\\s?")
    private val EMPHASIS = Regex("(\\*\\*|__|~~|\\*|_)(?=\\S)([^\\n]*?\\S)\\1")
    private val INLINE_CODE = Regex("`([^`\\n]{1,80})`")
    private val LONG_CODE = Regex("`[^`\\n]{81,}`")

    fun clean(markdown: String): String {
        var text = markdown.replace("\r\n", "\n")
        text = SOURCES.replace(text, "")
        text = FENCE.replace(text) { "\n(code omitted)\n" }
        text = IMAGE.replace(text, "")
        text = LINK.replace(text) { it.groupValues[1] }
        text = URL.replace(text, "")
        text = CITATION.replace(text, "")
        text = TABLE_RULE.replace(text, "")
        text = text.lines().joinToString("\n") { line -> if (line.trim().startsWith("|")) line.trim().trim('|').split('|').joinToString(", ") { it.trim() } else line }
        text = HEADING.replace(text, "")
        text = BULLET.replace(text, "")
        text = QUOTE.replace(text, "")
        text = LONG_CODE.replace(text, "(code)")
        text = INLINE_CODE.replace(text) { it.groupValues[1] }
        repeat(2) { text = EMPHASIS.replace(text) { it.groupValues[2] } }
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            .replace(Regex("[ \\t]{2,}"), " ").replace(Regex("\\s+([,.!?;:])"), "$1").trim()
    }
}

/**
 * Splits text for AI voice requests: a short first chunk so audio starts quickly, then larger chunks, always at
 * sentence or word boundaries and never above the provider's per-request limit.
 */
object TtsChunker {
    const val MAX_TOTAL = 12_000
    private val SENTENCE_END = setOf('.', '!', '?', '\n', '।', '۔', '؟', '…')

    fun split(text: String, maxCharacters: Int, firstChunk: Int = 220, laterChunk: Int = 600): List<String> {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Choose a reply with text." }
        require(clean.length <= MAX_TOTAL) { "AI voice reads replies up to ${MAX_TOTAL / 1000}k characters. Use device voice for this reply." }
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            val limit = minOf(if (chunks.isEmpty()) firstChunk else laterChunk, maxCharacters).coerceAtLeast(40)
            var end = minOf(start + limit, clean.length)
            if (end < clean.length) {
                val floor = start + limit / 3
                val sentence = (end - 1 downTo floor).firstOrNull { clean[it] in SENTENCE_END }
                val word = (end - 1 downTo floor).firstOrNull { clean[it].isWhitespace() }
                val cut = sentence ?: word
                if (cut != null) end = cut + 1
                // Never split a surrogate pair.
                if (end < clean.length && Character.isHighSurrogate(clean[end - 1])) end--
            }
            clean.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
            start = end
        }
        return chunks
    }
}

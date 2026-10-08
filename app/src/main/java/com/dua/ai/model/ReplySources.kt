package com.dua.ai.model

import com.dua.ai.setup.IntegrationCapabilities
import java.net.URI

data class ReplySource(val number: Int, val title: String, val url: String) {
    /** Host without a leading "www.", for compact chips. */
    val domain: String get() = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault(url)
}

data class ReplyParts(val body: String, val sources: List<ReplySource>)

/**
 * Splits the plain-text "Sources:" footer that the backend appends to web replies
 * (`web/src/core/chat-web.mjs` withWebSources) from the answer, so the UI can show
 * sources as chips. The footer is only removed when every entry parses and every
 * URL passes [IntegrationCapabilities.safeUrl]; otherwise the text is left untouched.
 */
object ReplySources {
    private const val MARKER = "\n\nSources:\n"
    private val heading = Regex("""^\[(\d{1,3})](?: (.*))?$""")

    fun split(text: String): ReplyParts {
        val unchanged = ReplyParts(text, emptyList())
        val at = text.lastIndexOf(MARKER)
        if (at < 0) return unchanged
        val footer = text.substring(at + MARKER.length).trim()
        if (footer.isEmpty()) return unchanged
        val sources = footer.split(Regex("""\n[ \t]*\n""")).map { entry ->
            val lines = entry.trim().lines()
            if (lines.size != 2) return unchanged
            val match = heading.matchEntire(lines[0].trim()) ?: return unchanged
            val url = IntegrationCapabilities.safeUrl(lines[1].trim()) ?: return unchanged
            val source = ReplySource(match.groupValues[1].toInt(), match.groupValues[2].trim(), url)
            if (source.title.isBlank()) source.copy(title = source.domain) else source
        }
        return ReplyParts(text.substring(0, at).trimEnd(), sources)
    }
}

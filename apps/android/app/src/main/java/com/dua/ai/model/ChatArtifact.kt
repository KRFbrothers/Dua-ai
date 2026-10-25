package com.dua.ai.model

data class ChatArtifact(val id: String, val language: String, val code: String)

/** Derived only from real assistant content. No sample documents, inferred outputs or code execution. */
object MessageArtifacts {
    const val MAX_ARTIFACTS = 8
    private const val MAX_MESSAGE_CHARS = 500_000
    private val formats = setOf("html", "htm", "svg", "markdown", "md", "mermaid", "mmd")
    private val uiLanguages = setOf("jsx", "tsx", "vue", "svelte", "css", "xml", "kotlin", "kt", "kts", "swift", "swiftui", "javascript", "js", "typescript", "ts")
    private val markup = Regex("^<(?:!doctype\\s+html|html\\b|head\\b|body\\b|div\\b|section\\b|main\\b|table\\b|svg\\b)", RegexOption.IGNORE_CASE)
    private val composeUi = Regex("@Composable|\\b(?:Column|Row|Box|Text|Button|Surface|Scaffold|setContent)\\s*\\(")
    private val tableRule = Regex(":?-{3,}:?")

    fun find(text: String): List<ChatArtifact> {
        if (text.length > MAX_MESSAGE_CHARS) return emptyList()
        val artifacts = CodeFences.parse(text, includeUnclosed = false, preserveTrailingNewlines = true).mapIndexedNotNull { index, (info, code) ->
            if (code.isBlank()) return@mapIndexedNotNull null
            val language = info.takeWhile { !it.isWhitespace() }.lowercase()
            val inferred = if (language.isBlank()) markupLanguage(code) else null
            when {
                language in formats -> ChatArtifact("fence-$index", language, code)
                inferred != null -> ChatArtifact("fence-$index", inferred, code)
                language in uiLanguages && isMultilineUi(language, code) -> ChatArtifact("fence-$index", language, code)
                else -> null
            }
        }.take(MAX_ARTIFACTS)
        if (artifacts.isNotEmpty()) return artifacts
        val plain = text.trim()
        // Never interpret an unfinished fence, a code-only answer or ordinary prose as a document.
        if (plain.contains("```") || plain.contains("~~~")) return emptyList()
        val markup = markupLanguage(plain)
        if (markup != null && completeMarkup(plain, markup)) return listOf(ChatArtifact("document", markup, plain))
        val lines = plain.lines()
        if (lines.zipWithNext().any { (header, rule) ->
                header.contains('|') && rule.trim().trim('|').split('|').let { cells ->
                    cells.size == header.trim().trim('|').split('|').size && cells.all { tableRule.matches(it.trim()) }
                }
            }) return listOf(ChatArtifact("table", "markdown", plain))
        return emptyList()
    }

    private fun markupLanguage(code: String): String? = when {
        code.trimStart().startsWith("<svg", ignoreCase = true) -> "svg"
        markup.containsMatchIn(code.trimStart()) -> "html"
        else -> null
    }

    private fun completeMarkup(code: String, language: String): Boolean {
        if (language == "svg") return code.endsWith("</svg>", true) || code.endsWith("/>")
        val root = Regex("<([A-Za-z][\\w:-]*)(?:\\s|>)").find(code)?.groupValues?.get(1) ?: return false
        return code.endsWith("</$root>", true)
    }

    private fun isMultilineUi(language: String, code: String): Boolean {
        if (code.lineSequence().count { it.isNotBlank() } < 2) return false
        return when (language) {
            "kotlin", "kt", "kts" -> composeUi.containsMatchIn(code)
            "swift", "swiftui" -> code.contains("SwiftUI") || code.contains(Regex("\\b(?:VStack|HStack|View)\\b"))
            "js", "javascript", "ts", "typescript" -> code.contains(Regex("<[A-Za-z][\\w:-]*(?:\\s|>|/)"))
            else -> true
        }
    }
}

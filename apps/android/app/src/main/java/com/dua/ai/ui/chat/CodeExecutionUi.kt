package com.dua.ai.ui.chat

import androidx.compose.runtime.staticCompositionLocalOf
import com.dua.ai.model.CodeSnippet

/** Reuses the chat's provider-hosted code pipeline, never evaluates code on the phone. */
internal data class CodeExecutionUi(
    val unavailableReason: String? = "Code execution is unavailable in this view.",
    val run: (String) -> Boolean = { false },
)
internal val LocalCodeExecutionUi = staticCompositionLocalOf { CodeExecutionUi() }

internal fun codeRunUnavailableReason(language: String?, code: String, execution: CodeExecutionUi): String? = when {
    language?.trim()?.lowercase() !in setOf("python", "py") -> "Run supports Python only. HTML, SVG, Markdown and Mermaid have local previews."
    code.isBlank() -> "There is no code to run."
    CodeSnippet.prompt(code).length > 8000 -> "This snippet is too large for a chat run. Copy or share it instead."
    else -> execution.unavailableReason
}

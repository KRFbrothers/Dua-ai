/*
 * The Summarize / Rewrite flow (check, download, run, show result or error) follows the genai-summarization and
 * genai-writing-assistance samples in android/ai-samples (https://github.com/android/ai-samples), Copyright 2025
 * The Android Open Source Project, licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md.
 * Changes: runs on Dua's existing ML Kit GenAI Prompt (Gemini Nano) client instead of adding the separate
 * Summarization/Rewriting libraries, Dua's own prompts and output clean-up, and a visible server fallback.
 */
package com.dua.ai.ondevice

/** Rewrite styles, like the ML Kit Rewriting sample's options. [instruction] is what the model is asked to do. */
enum class RewriteStyle(val label: String, val instruction: String) {
    Rephrase("Rephrase", "Rephrase it with different words. Keep the same meaning and length."),
    Shorter("Shorter", "Make it shorter and simpler. Keep the key points."),
    Elaborate("More detail", "Expand it with a little more helpful detail."),
    Friendly("Friendly", "Make it warm and friendly."),
    Professional("Professional", "Make it clear, polite and professional."),
    Grammar("Fix grammar", "Fix spelling, grammar and punctuation only. Do not change the meaning or style."),
}

/** What the user asked for: a short summary, or a rewrite in one [RewriteStyle]. */
sealed interface WritingTask {
    data object Summarize : WritingTask
    data class Rewrite(val style: RewriteStyle) : WritingTask
}

object WritingPrompts {
    const val INPUT_MAX = 4000
    const val OUTPUT_MAX = 3000

    fun onDevice(task: WritingTask, text: String): String {
        val input = text.trim().take(INPUT_MAX)
        return when (task) {
            WritingTask.Summarize -> "Summarize the text below in at most 3 short bullet points, each starting with \"- \". " +
                "Use the same language as the text. Reply with the bullet points only.\n\nText:\n$input\n\nSummary:"
            is WritingTask.Rewrite -> "Rewrite the text below. ${task.style.instruction} Use the same language as the text. " +
                "Reply with the rewritten text only, no explanation.\n\nText:\n$input\n\nRewritten:"
        }
    }

    /**
     * The message put in the composer when the phone can't do it, so the normal Dua chat (server) can. Nothing is sent
     * until the user taps Send.
     */
    fun server(task: WritingTask, text: String): String {
        val input = text.trim().take(INPUT_MAX)
        return when (task) {
            WritingTask.Summarize -> "Summarize this in 3 short bullet points:\n\n$input"
            is WritingTask.Rewrite -> "Rewrite this. ${task.style.instruction} Reply with the rewritten text only.\n\n$input"
        }
    }

    /** Multi-line output kept, labels and wrapping quotes removed, length capped; null when empty or an echo. */
    fun clean(raw: String?, input: String): String? {
        var text = raw?.replace("\r", "")?.trim() ?: return null
        listOf("summary:", "rewritten:", "rewritten text:", "here is the rewritten text:", "here is the summary:").forEach { label ->
            if (text.startsWith(label, ignoreCase = true)) text = text.substring(label.length).trim()
        }
        if (text.length >= 2 && text.first() in "\"“" && text.last() in "\"”") text = text.substring(1, text.length - 1).trim()
        text = text.filter { it == '\n' || it == '\t' || !it.isISOControl() }.replace(Regex("\n{3,}"), "\n\n")
        if (text.length > OUTPUT_MAX) text = text.take(OUTPUT_MAX).substringBeforeLast(' ').trimEnd() + "…"
        return text.takeIf { it.isNotBlank() && !it.equals(input.trim(), ignoreCase = true) }
    }
}

/** One open Summarize / Rewrite sheet. [task] is null while the user is still choosing a rewrite style. */
data class WritingSession(
    val text: String,
    val fromDraft: Boolean,
    val task: WritingTask?,
    val phase: Phase = Phase.Choose,
    val result: String = "",
) {
    enum class Phase { Choose, Working, Result, Unavailable, Failed }
}

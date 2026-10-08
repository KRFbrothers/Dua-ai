package com.dua.ai.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val model: String = "Dua 2.0 Flash",
    /** "complete", "stopped" (kept partial or stopped reply) or, in memory only, "streaming". */
    val state: String = "complete",
    /** Reasoning summary shown in the collapsible Thinking block (Reasoning mode). Saved with the chat. */
    val thinking: String? = null,
    /** Seconds from send until the answer started (or finished) when thinking was shown. Saved. */
    val thinkingSeconds: Int? = null,
    /** User turns: UTF-8 text of attached text/code files, size-capped, kept on this device for follow-ups. */
    val attachmentText: String? = null,
    /** In memory only: when the streaming request started (for the live "Thinking… Ns" timer). */
    val startedAt: Long = 0L,
    /** In memory only: non-fatal note from the server, e.g. provider has no visible thinking or output was cut off. */
    val notice: String? = null,
    /** User turns: files stored in the private Supabase bucket (references only). Kept on this device. */
    val files: List<com.dua.ai.files.FileRef> = emptyList(),
    /** Model turns: Imagine results (references into the private bucket, never bytes). Saved, exported and synced. */
    val media: List<com.dua.ai.imagine.GeneratedMedia> = emptyList(),
    /** Model turns: the Imagine request and its progress (video jobs resume from here). Kept on this device. */
    val imagine: com.dua.ai.imagine.ImagineInfo? = null,
    /** Model turns: code the provider ran for this reply and its output (Run code). Kept in local history only. */
    val codeRuns: List<CodeRun> = emptyList(),
    /** Model turns: Build mode repository and the files the server read (Phase 5B). Kept in local history only. */
    val build: BuildInfo? = null,
) {
    /** An Imagine reply (generated images or video, finished or not). */
    val isImagine: Boolean get() = !isUser && (imagine != null || media.isNotEmpty())
    val streaming: Boolean get() = state == STREAMING
    companion object {
        const val STREAMING = "streaming"
        const val MAX_THINKING_CHARS = 20000
        const val MAX_ATTACHMENT_TEXT_CHARS = 32000
    }
}

data class PromptSuggestion(
    val title: String,
    val prompt: String,
    val category: String
)


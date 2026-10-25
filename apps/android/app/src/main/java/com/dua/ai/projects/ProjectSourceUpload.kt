package com.dua.ai.projects

/** In-flight upload state; no signed URLs, tokens or file bytes are persisted. */
data class ProjectSourceUpload(val id: String, val projectId: String, val name: String, val sent: Long = 0, val total: Long = 0,
    val error: String? = null, val complete: Boolean = false) {
    val progress: Float get() = if (total <= 0) 0f else (sent.toFloat() / total).coerceIn(0f, 1f)
}

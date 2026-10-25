package com.dua.ai.history

/** Opening an unchanged chat must not become a competing cloud edit. */
object ChatSavePolicy {
    fun preserveUnchangedTime(previous: SavedChat?, next: SavedChat): SavedChat {
        if (previous == null || previous.id != next.id) return next
        val unchanged = previous.model == next.model && previous.draft == next.draft && previous.customTitle == next.customTitle && previous.pinned == next.pinned &&
            previous.messages.map { Triple(it.isUser, it.text, it.state) } == next.messages.map { Triple(it.isUser, it.text, it.state) }
        return if (unchanged) next.copy(updatedAt = previous.updatedAt) else next
    }
}

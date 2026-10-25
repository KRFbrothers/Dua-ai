package com.dua.ai.settings

/** Edits only Personal notes; never replaces unrelated settings from an old UI snapshot. */
object PersonalMemory {
    fun save(current: List<String>, index: Int?, original: String, text: String): List<String> {
        val note = text.trim()
        require(note.isNotBlank() && note.length <= 200) { "Memory notes must contain 1–200 characters." }
        if (index == null) {
            require(current.size < 10) { "Memory is full. Delete a note before adding another." }
            return current + note
        }
        require(current.getOrNull(index) == original) { "This note changed. Cancel and reopen it before saving." }
        return current.mapIndexed { i, value -> if (i == index) note else value }
    }

    fun delete(current: List<String>, index: Int, original: String): List<String> {
        require(current.getOrNull(index) == original) { "This note changed. Cancel and review it before deleting." }
        return current.filterIndexed { i, _ -> i != index }
    }
}

package com.dua.ai.setup

import java.util.Locale

/** Local suggestions only. Callers must show each candidate and confirm before saving it. */
object AutomationMemoryCandidates {
    private val explicit = Regex("^(?:please\\s+)?remember(?:\\s+that|\\s+this)?\\s*[:,-]?\\s+(.+)$|^(?:yaad\\s+rakho|yaad\\s+rakhna|याद\\s+रखो|याद\\s+रखना)\\s*[:,-]?\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val credentials = Regex("\\b(password|passcode|api[ _-]?key|access[ _-]?token|refresh[ _-]?token|secret|otp|pin\\s*(?:code|number))\\b", RegexOption.IGNORE_CASE)
    private fun normalized(text: String) = text.trim().replace(Regex("\\s+"), " ")

    fun propose(text: String, existing: List<String> = emptyList()): List<String> {
        if (text.length > 8000) return emptyList()
        val used = existing.map { normalized(it).lowercase(Locale.ROOT) }.toMutableSet()
        return text.split(Regex("\\n|(?<=[.!?])\\s+")).mapNotNull { line ->
            val match = explicit.matchEntire(line.trim()) ?: return@mapNotNull null
            val note = normalized(match.groupValues[1].ifBlank { match.groupValues[2] })
            note.takeIf { it.isNotEmpty() && it.length <= 200 && !credentials.containsMatchIn(it) && used.add(it.lowercase(Locale.ROOT)) }
        }.take(4)
    }

    fun confirm(candidate: String, existing: List<String>): List<String> {
        val note = normalized(candidate)
        require(note.isNotEmpty() && note.length <= 200) { "Memory notes must contain 1–200 characters." }
        if (existing.any { normalized(it).equals(note, ignoreCase = true) }) return existing
        require(existing.size < 10) { "Memory is full. Remove a note before confirming another." }
        return existing + note
    }
}

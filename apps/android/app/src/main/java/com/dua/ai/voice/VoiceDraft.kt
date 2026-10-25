package com.dua.ai.voice

object VoiceDraft {
    fun merge(existing: String, transcript: String): String {
        val words = transcript.trim()
        require(words.isNotEmpty()) { "No speech detected. Tap mic to retry." }
        val merged = if (existing.isBlank()) words else "$existing\n$words"
        require(merged.length <= 8000) { "Message limit is 8000 characters. Shorten your draft or recording." }
        return merged
    }
}

package com.dua.ai.voice

import java.security.MessageDigest

/**
 * In-memory LRU cache of generated reply audio, keyed by provider, voice, language, speed and chunk text
 * (hashed, so reply text is not kept as a key). Replaying a reply or a voice preview costs nothing.
 * Nothing is written to disk; [clear] runs when the account changes or chats are cleared.
 * Callers always get a copy, so playback can wipe its bytes without touching the cache.
 */
class AudioChunkCache(private val maxBytes: Int = 12 * 1024 * 1024) {
    private val entries = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var size = 0

    @Synchronized fun get(key: String): ByteArray? = entries[hash(key)]?.copyOf()

    @Synchronized fun put(key: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > maxBytes / 2) return
        val id = hash(key)
        entries.remove(id)?.let { size -= it.size; it.fill(0) }
        entries[id] = bytes.copyOf()
        size += bytes.size
        val iterator = entries.entries.iterator()
        while (size > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            size -= eldest.value.size
            eldest.value.fill(0)
            iterator.remove()
        }
    }

    @Synchronized fun clear() { entries.values.forEach { it.fill(0) }; entries.clear(); size = 0 }
    @Synchronized fun bytes(): Int = size
    @Synchronized fun count(): Int = entries.size

    private fun hash(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
}

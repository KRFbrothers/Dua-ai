package com.dua.ai.files

import org.json.JSONArray

/**
 * Discarded unsent upload paths waiting for cleanup while offline or signed out.
 * The backend retains paths still referenced by the authoritative server snapshot,
 * including paths queued by older app versions when a cached chat was deleted.
 */
class PendingFileDeletes(private val read: () -> String?, private val write: (String) -> Unit) {
    @Synchronized fun all(): List<String> = runCatching {
        val array = JSONArray(read() ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf { path -> StoredFileTypes.PATH.matches(path) } }
    }.getOrDefault(emptyList())

    @Synchronized fun add(paths: Collection<String>) {
        if (paths.isEmpty()) return
        save((all() + paths.filter { StoredFileTypes.PATH.matches(it) }).distinct().takeLast(MAX))
    }

    @Synchronized fun remove(paths: Collection<String>) { if (paths.isNotEmpty()) save(all() - paths.toSet()) }

    /** Paths that belong to [userId] (the only ones that account can delete). */
    fun forUser(userId: String): List<String> = all().filter { it.startsWith(userId.lowercase() + "/") }

    private fun save(paths: List<String>) = write(JSONArray(paths).toString())

    companion object { const val MAX = 5000 }
}

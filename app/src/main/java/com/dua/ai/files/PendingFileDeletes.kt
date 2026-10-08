package com.dua.ai.files

import org.json.JSONArray

/**
 * Storage paths waiting to be deleted (chat deleted while offline or signed out). Persisted so a
 * deleted chat's files are removed on the next successful attempt.
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

package com.dua.ai.files

import com.dua.ai.imagine.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

data class CacheUsage(val bytes: Long, val mediaBytes: Long)
data class StorageUsageStatus(val ownerId: String? = null, val loading: Boolean = false, val error: String = "")

object StorageOverview {
    /** Measures only this app's cache, without reading file contents, following links or deleting data. */
    suspend fun cacheUsage(cacheDir: File): CacheUsage = withContext(Dispatchers.IO) {
        if (!cacheDir.exists()) return@withContext CacheUsage(0, 0)
        require(cacheDir.isDirectory && !Files.isSymbolicLink(cacheDir.toPath())) { "App cache could not be measured." }
        val mediaDir = File(cacheDir, MediaCache.DIR).toPath()
        var bytes = 0L
        var mediaBytes = 0L
        for (file in cacheDir.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.onFail { _, error -> throw error }) {
            currentCoroutineContext().ensureActive()
            if (Files.isSymbolicLink(file.toPath()) || !file.isFile) continue
            val size = file.length().coerceAtLeast(0)
            bytes = Math.addExact(bytes, size)
            if (file.toPath().startsWith(mediaDir)) mediaBytes = Math.addExact(mediaBytes, size)
        }
        CacheUsage(bytes, mediaBytes)
    }

    /** Reject malformed responses instead of presenting a fabricated zero-byte account. */
    fun usage(json: JSONObject): StorageUsage {
        fun count(name: String): Long {
            val raw = json.opt(name)
            require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() >= 0 &&
                raw.toDouble() <= 9007199254740991.0 && raw.toDouble() == raw.toLong().toDouble()) { "Storage usage response is invalid. Refresh to retry." }
            return raw.toLong()
        }
        val files = count("files")
        val bytes = count("bytes")
        val maxBytes = count("maxBytes")
        require(files <= Int.MAX_VALUE && maxBytes > 0) { "Storage usage response is invalid. Refresh to retry." }
        return StorageUsage(files.toInt(), bytes, maxBytes)
    }
}

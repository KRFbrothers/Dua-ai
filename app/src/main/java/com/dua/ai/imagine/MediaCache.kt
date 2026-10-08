package com.dua.ai.imagine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Loads generated media for display, saving and sharing. Implemented by [MediaCache]; screenshot tests and
 * previews provide fakes so rendering stays deterministic.
 */
interface MediaLoader {
    /** A decoded image already in memory (no I/O), or null. */
    fun cached(media: GeneratedMedia, maxPx: Int): Bitmap? = null
    /** Decodes an image (downloading it into the cache first if needed), downsampled to about [maxPx]. */
    suspend fun image(media: GeneratedMedia, maxPx: Int): Bitmap? = null
    /** A local copy of the file (downloaded on demand), for saving, sharing and video playback. */
    suspend fun file(media: GeneratedMedia): File? = null
    /** A link the video player can stream right away (local file URI or signed URL). */
    suspend fun playable(media: GeneratedMedia): String? = null
    /** A poster frame for a video, or null. */
    suspend fun poster(media: GeneratedMedia, maxPx: Int): Bitmap? = null
    fun cachedPoster(media: GeneratedMedia, maxPx: Int): Bitmap? = null
}

/**
 * Cache under cacheDir/generated-media: one file per stored path, fetched with a short-lived signed link from
 * [signedUrl] (the server checks the owner). Android may clear the cache; files are fetched again when needed.
 */
class MediaCache(cacheDir: File, private val signedUrl: suspend (String) -> String, private val client: OkHttpClient = downloadClient) : MediaLoader {
    val dir = File(cacheDir, DIR)
    private val memory = object : LruCache<String, Bitmap>(memoryBytes()) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
    private val locks = mutableMapOf<String, Mutex>()
    private val links = mutableMapOf<String, Pair<String, Long>>()

    /** Remembers a fresh signed link (for example from the generation response) so the first download skips a round trip. */
    @Synchronized fun remember(path: String, url: String) { links[path] = url to System.currentTimeMillis() + LINK_MS }
    @Synchronized private fun link(path: String): String? = links[path]?.takeIf { it.second > System.currentTimeMillis() }?.first
    @Synchronized private fun lock(key: String) = locks.getOrPut(key) { Mutex() }

    fun local(media: GeneratedMedia): File? = media.path?.let { File(dir, it.substringAfter('/')) }

    override fun cached(media: GeneratedMedia, maxPx: Int): Bitmap? = memory.get("${media.key}@$maxPx")
    override fun cachedPoster(media: GeneratedMedia, maxPx: Int): Bitmap? = memory.get("poster:${media.key}@$maxPx")

    override suspend fun image(media: GeneratedMedia, maxPx: Int): Bitmap? {
        cached(media, maxPx)?.let { return it }
        val file = file(media) ?: return null
        return withContext(Dispatchers.IO) { decode(file, maxPx) }?.also { memory.put("${media.key}@$maxPx", it) }
    }

    override suspend fun file(media: GeneratedMedia): File? {
        val path = media.path
        if (path == null) {
            // A video too large to store: only the provider's temporary link exists.
            val url = media.url ?: return null
            val target = File(dir, "link-" + Integer.toHexString(url.hashCode()) + ".mp4")
            return lock(target.name).withLock { if (target.length() > 0) target else download(url, target) }
        }
        val target = local(media)!!
        return lock(path).withLock {
            if (target.length() > 0) return@withLock target
            val first = link(path) ?: signedUrl(path)
            try { download(first, target) }
            catch (failure: IOException) { if (first == link(path)) download(signedUrl(path), target) else throw failure }
        }
    }

    override suspend fun playable(media: GeneratedMedia): String? {
        local(media)?.takeIf { it.length() > 0 }?.let { return android.net.Uri.fromFile(it).toString() }
        return media.path?.let { link(it) ?: signedUrl(it).also { url -> remember(it, url) } } ?: media.url
    }

    override suspend fun poster(media: GeneratedMedia, maxPx: Int): Bitmap? {
        cachedPoster(media, maxPx)?.let { return it }
        val file = file(media) ?: return null
        return withContext(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(0)?.let { scale(it, maxPx) }
            } catch (_: Exception) { null } finally { runCatching { retriever.release() } }
        }?.also { memory.put("poster:${media.key}@$maxPx", it) }
    }

    /** Removes cached copies of deleted files. */
    fun forget(paths: Collection<String>) {
        paths.forEach { path -> File(dir, path.substringAfter('/')).delete(); synchronized(this) { links.remove(path) } }
        memory.evictAll()
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() }; memory.evictAll() }

    private suspend fun download(url: String, target: File): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val temp = File(dir, target.name + ".part")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("Empty download.")
            var total = 0L
            body.byteStream().use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; if (total > MAX_BYTES) throw IOException("File is too large.")
                    output.write(buffer, 0, count)
                }
            } }
            if (total == 0L) throw IOException("Empty download.")
        }
        if (!temp.renameTo(target)) { temp.delete(); throw IOException("Could not save the download.") }
        target
    }

    companion object {
        const val DIR = "generated-media"
        const val MAX_BYTES = 100L * 1024 * 1024
        private const val LINK_MS = 50L * 60 * 1000
        private fun memoryBytes() = (Runtime.getRuntime().maxMemory() / 16).coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024).toInt()
        val downloadClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

        /** Decodes with power-of-two sampling so the longest side lands near [maxPx]. */
        fun decode(file: File, maxPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        private fun scale(bitmap: Bitmap, maxPx: Int): Bitmap {
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest <= maxPx) return bitmap
            val ratio = maxPx.toFloat() / longest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        }
    }
}

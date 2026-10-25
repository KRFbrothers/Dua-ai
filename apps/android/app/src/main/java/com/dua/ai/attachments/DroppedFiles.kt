/*
 * Portions adapted from Androidify's DropBehaviourFactory.kt
 * (https://github.com/android/androidify), Copyright 2025 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md.
 * Changes: any file type and up to three items, a byte cap, original display names, and
 * Dua's private cache folder instead of a decoded JPEG copy.
 */
package com.dua.ai.attachments

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** One item from a drag-and-drop clip: a file reference or plain text. */
data class DropItem(val uri: android.net.Uri? = null, val text: String? = null)

/** What a drop turns into: files for the attachment pipeline, text for the draft, and a short notice. */
data class DropResult(val files: List<File> = emptyList(), val text: String = "", val notice: String = "")

/**
 * Copies dropped files into private cache. Read access to dropped content lasts only until the drop
 * finishes, while uploads and inline reads happen later, so every file is copied first (capped).
 * Copies live in their own folder per drop (keeping the original name) and are removed after a day.
 */
class DroppedFileStore(cacheDir: File, private val now: () -> Long = System::currentTimeMillis) {
    val directory = File(cacheDir, DIRECTORY)

    /**
     * Receives a drop. [nameOf], [typeOf] and [open] read the source while drop access is held;
     * text items are joined for the draft. Never throws for a single bad item.
     */
    fun receive(
        items: List<DropItem>,
        maxBytes: Long,
        nameOf: (android.net.Uri) -> String?,
        typeOf: (android.net.Uri) -> String?,
        open: (android.net.Uri) -> InputStream?,
    ): DropResult {
        sweep()
        val uris = items.mapNotNull { it.uri }
        val text = items.filter { it.uri == null }.mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString("\n").take(MAX_TEXT_CHARS)
        val files = mutableListOf<File>()
        var tooLarge = 0; var unreadable = 0
        uris.take(MAX_ITEMS).forEach { uri ->
            try {
                val name = safeName(runCatching { nameOf(uri) }.getOrNull(), runCatching { typeOf(uri) }.getOrNull())
                files += copy(name, maxBytes) { open(uri) }
            } catch (_: TooLargeException) { tooLarge++ }
            catch (_: IOException) { unreadable++ }
            catch (_: SecurityException) { unreadable++ }
            catch (_: IllegalArgumentException) { unreadable++ }
        }
        val notice = listOfNotNull(
            "Only the first $MAX_ITEMS dropped files were added.".takeIf { uris.size > MAX_ITEMS },
            "$tooLarge dropped file(s) were too large (max ${maxBytes / (1024 * 1024)} MB).".takeIf { tooLarge > 0 },
            "$unreadable dropped file(s) could not be read. Try Files instead.".takeIf { unreadable > 0 },
            "Nothing to attach here. Drop photos or files.".takeIf { files.isEmpty() && text.isEmpty() && tooLarge + unreadable == 0 },
        ).joinToString(" ")
        return DropResult(files, text, notice)
    }

    /** Copies one stream to `dropped-files/<id>/<name>`, failing (and cleaning up) above [maxBytes]. */
    fun copy(name: String, maxBytes: Long, open: () -> InputStream?): File {
        require(File(name).name == name && name.isNotBlank()) { "Invalid file name." }
        val folder = File(directory, UUID.randomUUID().toString())
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Drop storage unavailable.")
        val target = File(folder, name)
        try {
            val input = open() ?: throw IOException("Dropped file unavailable.")
            input.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw TooLargeException()
                        out.write(buffer, 0, read)
                    }
                }
            }
            return target
        } catch (failure: Exception) {
            folder.deleteRecursively()
            throw failure
        }
    }

    /** Removes copies older than a day (abandoned drops, or files already sent). */
    fun sweep() {
        val cutoff = now() - MAX_AGE_MS
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    class TooLargeException : IOException("Dropped file is too large.")

    companion object {
        const val DIRECTORY = "dropped-files"
        const val MAX_ITEMS = 3
        const val MAX_TEXT_CHARS = 20_000
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private val EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif",
            "image/heic" to "heic", "application/pdf" to "pdf", "text/plain" to "txt", "text/csv" to "csv", "audio/mpeg" to "mp3",
            "audio/mp4" to "m4a", "video/mp4" to "mp4", "application/json" to "json")

        /** A plain file name: no folders or control characters, at most 120 characters, with an extension when the type is known. */
        fun safeName(raw: String?, type: String?): String {
            val cleaned = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
                .filter { it >= ' ' && it !in "<>:\"|?*" }.trim().trimStart('.')
            var name = cleaned.ifBlank { "Dropped file" }
            if (!name.contains('.')) EXTENSIONS[type?.lowercase()?.substringBefore(';')?.trim()]?.let { name += ".$it" }
            if (name.length > 120) {
                val ext = name.substringAfterLast('.', "").take(10)
                name = name.take(120 - ext.length - 1).trimEnd() + if (ext.isNotEmpty()) ".$ext" else ""
            }
            return name
        }
    }
}

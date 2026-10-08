package com.dua.ai.files

import org.json.JSONArray
import org.json.JSONObject

/**
 * A file uploaded to the private `dua-files` Supabase bucket. Messages keep these references (never
 * bytes); the server re-attaches the file by [path] on every follow-up. [transcript] is filled in for
 * audio after the first reply so it is never transcribed twice.
 */
data class FileRef(val path: String, val name: String, val mimeType: String, val size: Long, val transcript: String? = null) {
    val kind: String get() = StoredFileTypes.kind(mimeType)
}

/** Mirrors web/src/core/file-types.mjs (the server re-validates everything). */
object StoredFileTypes {
    private val byMime = mapOf(
        "image/jpeg" to "image", "image/png" to "image", "image/webp" to "image",
        "application/pdf" to "pdf",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "sheet", "application/vnd.ms-excel" to "sheet",
        "application/vnd.oasis.opendocument.spreadsheet" to "sheet", "text/csv" to "sheet",
        "text/plain" to "text", "text/markdown" to "text", "application/json" to "text",
        "audio/mp4" to "audio", "audio/m4a" to "audio", "audio/x-m4a" to "audio", "audio/aac" to "audio", "audio/mpeg" to "audio", "audio/mp3" to "audio",
        "audio/wav" to "audio", "audio/x-wav" to "audio", "audio/wave" to "audio", "audio/ogg" to "audio", "audio/opus" to "audio", "audio/webm" to "audio", "audio/flac" to "audio",
    )
    private val byExtension = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp", "pdf" to "application/pdf",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xls" to "application/vnd.ms-excel",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet", "csv" to "text/csv", "txt" to "text/plain", "md" to "text/markdown", "json" to "application/json",
        "m4a" to "audio/mp4", "aac" to "audio/aac", "mp3" to "audio/mpeg", "wav" to "audio/wav", "ogg" to "audio/ogg", "oga" to "audio/ogg",
        "opus" to "audio/opus", "webm" to "audio/webm", "flac" to "audio/flac",
    )
    /** Picker MIME filters for the Audio tile. */
    val AUDIO_PICKER = arrayOf("audio/*")
    /** Extra picker types (spreadsheets, audio) offered by Files when stored files are available. */
    val PICKER_EXTRA = arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
        "application/vnd.oasis.opendocument.spreadsheet", "text/csv", "audio/*")
    val PATH = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{2,5}")

    fun kind(mimeType: String): String = byMime[mimeType] ?: "file"

    /** Stored MIME type for a picked file, or null when it must use the small inline path (or is unsupported). */
    fun storedType(name: String, reported: String): String? {
        val type = reported.substringBefore(';').trim().lowercase()
        if (type in byMime) return type
        val ext = name.substringAfterLast('.', "").lowercase()
        val fromExt = byExtension[ext] ?: return null
        return if (type.isEmpty() || type == "application/octet-stream" || type.startsWith("application/") || type.startsWith("audio/")) fromExt else null
    }

    /** Types that cannot be sent without stored files (no inline equivalent). */
    fun needsStorage(name: String, reported: String): Boolean = storedType(name, reported)?.let { kind(it) == "audio" || (kind(it) == "sheet" && it != "text/csv") } == true

    fun label(mimeType: String): String = when (kind(mimeType)) {
        "image" -> mimeType.substringAfter('/').uppercase()
        "pdf" -> "PDF"; "sheet" -> "Spreadsheet"; "audio" -> "Audio"; "text" -> "Text"; else -> "File"
    }

    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0).replace(".0 ", " ")
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0).replace(".0 ", " ")
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

object FileRefCodec {
    const val MAX_TRANSCRIPT_CHARS = 100_000

    fun toJson(ref: FileRef, includeTranscript: Boolean = true): JSONObject = JSONObject().put("path", ref.path).put("name", ref.name.take(200))
        .put("mimeType", ref.mimeType).put("size", ref.size)
        .apply { if (includeTranscript && !ref.transcript.isNullOrEmpty()) put("transcript", ref.transcript.take(MAX_TRANSCRIPT_CHARS)) }

    fun array(refs: List<FileRef>, includeTranscript: Boolean = true) = JSONArray().apply { refs.forEach { put(toJson(it, includeTranscript)) } }

    /** Lenient decode for local history: invalid entries are dropped instead of failing the chat. */
    fun fromArray(array: JSONArray?): List<FileRef> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), 20)).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
            val path = item.optString("path"); val name = item.optString("name"); val mime = item.optString("mimeType")
            if (!StoredFileTypes.PATH.matches(path) || name.isBlank() || StoredFileTypes.kind(mime) == "file") null
            else FileRef(path, name.take(200), mime, item.optLong("size").coerceAtLeast(0),
                item.optString("transcript").takeIf { item.has("transcript") && it.isNotEmpty() }?.take(MAX_TRANSCRIPT_CHARS))
        } }
    }
}

/** A file being uploaded (or uploaded and waiting to be sent) from the composer. */
data class PendingUpload(val id: String, val name: String, val mimeType: String, val size: Long, val sent: Long = 0,
    val state: String = UPLOADING, val ref: FileRef? = null, val error: String? = null,
    /** Already stored for an earlier message (Imagine "Edit"): removing it from the composer must not delete the file. */
    val shared: Boolean = false) {
    val progress: Float get() = if (size <= 0) 0f else (sent.toFloat() / size).coerceIn(0f, 1f)
    val uploading: Boolean get() = state == UPLOADING
    companion object { const val UPLOADING = "uploading"; const val DONE = "done"; const val FAILED = "failed" }
}

/** Storage used by this account in the private bucket (GET /api/files/usage). */
data class StorageUsage(val files: Int, val bytes: Long, val maxFileBytes: Long)

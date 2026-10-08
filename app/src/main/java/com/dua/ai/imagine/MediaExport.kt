package com.dua.ai.imagine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Saving to the gallery (MediaStore) and sharing through the app's FileProvider. */
object MediaExport {
    fun displayName(media: GeneratedMedia, index: Int = 0): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        return "Dua-$stamp" + (if (index > 0) "-$index" else "") + "." + extension(media.mimeType)
    }
    fun extension(mimeType: String) = when (mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; ImagineCodec.VIDEO -> "mp4"; else -> "jpg" }

    /** Android 10+ needs no permission; Android 8–9 needs WRITE_EXTERNAL_STORAGE (requested by the caller). */
    fun needsPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Copies [file] into Pictures/Dua or Movies/Dua. Returns the new item's URI. */
    fun saveToGallery(context: Context, file: File, media: GeneratedMedia, index: Int = 0): android.net.Uri {
        val resolver = context.contentResolver
        val video = media.isVideo
        val name = displayName(media, index)
        val collection = if (video) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Dua")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val folder = File(Environment.getExternalStoragePublicDirectory(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "Dua").apply { mkdirs() }
                @Suppress("DEPRECATION") put(MediaStore.MediaColumns.DATA, File(folder, name).absolutePath)
            }
        }
        val uri = resolver.insert(collection, values) ?: error("The gallery is unavailable.")
        try {
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("The gallery is unavailable.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (failure: Exception) { runCatching { resolver.delete(uri, null, null) }; throw failure }
        return uri
    }

    /** A share sheet for cached files (served by the FileProvider from cacheDir/generated-media). */
    fun shareIntent(context: Context, files: List<Pair<File, GeneratedMedia>>): Intent {
        val uris = files.map { (file, _) -> FileProvider.getUriForFile(context, "${context.packageName}.camera", file) }
        val type = if (files.all { !it.second.isVideo }) "image/*" else if (files.all { it.second.isVideo }) "video/mp4" else "*/*"
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        intent.type = type
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        return Intent.createChooser(intent, null)
    }
}

package com.dua.ai.viewmodel

import android.app.Application
import android.net.Uri
import java.io.File
import java.util.UUID

// Attachment file helpers moved out of ChatViewModel.kt unchanged (refactor only).

internal data class PickedFile(val uri: Uri, val name: String, val type: String, val size: Long?)

internal fun ChatViewModel.describe(uris: List<Uri>): List<PickedFile> {
    val resolver = getApplication<Application>().contentResolver
    return uris.map { uri ->
        var name = "Selected file"; var size: Long? = null
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) { name = it.getString(0) ?: name; if (!it.isNull(1)) size = it.getLong(1) }
        }
        PickedFile(uri, name.take(200), resolver.getType(uri).orEmpty(), size?.takeIf { it >= 0 })
    }
}

internal fun ChatViewModel.copyCapped(uri: Uri, limit: Long): File? {
    val dir = File(getApplication<Application>().cacheDir, "upload-staging").apply { mkdirs() }
    val target = File(dir, UUID.randomUUID().toString())
    val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: return null
    var total = 0L
    input.use { source -> target.outputStream().use { out ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = source.read(buffer); if (count < 0) break; total += count; if (total > limit) { target.delete(); return null }; out.write(buffer, 0, count) }
    } }
    return target
}

package com.dua.ai.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dua.ai.attachments.AttachmentReader
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.MediaExport
import com.dua.ai.imagine.MediaLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

/** Stored media uses the existing owner-authorized cache; transient inline images stay in app-private cache. */
class LibraryMediaLoader(private val delegate: MediaLoader, private val cacheDir: File, items: List<LibraryItem>) : MediaLoader by delegate {
    private val inline = items.filter { it.inline != null && it.media != null }.associateBy { it.media!!.key }
    override suspend fun file(media: GeneratedMedia): File? {
        val item = inline[media.key] ?: return delegate.file(media)
        return withContext(Dispatchers.IO) {
            val bytes = Base64.getDecoder().decode(item.inline!!.data)
            require(bytes.size <= AttachmentReader.MAX_BYTES) { "Image is too large to preview." }
            val dir = File(cacheDir, "generated-media").apply { mkdirs() }
            val name = item.id.removePrefix("inline:")
            require(name.matches(Regex("[a-f0-9]{64}")))
            File(dir, "inline-$name.${MediaExport.extension(media.mimeType)}").also { if (!it.exists()) it.writeBytes(bytes) }
        }
    }
    override suspend fun image(media: GeneratedMedia, maxPx: Int): Bitmap? {
        if (media.key !in inline) return delegate.image(media, maxPx)
        val file = file(media) ?: return null
        return withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx.coerceAtLeast(1) * 2) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}

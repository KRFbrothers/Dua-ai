package com.dua.ai.ondevice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Turns a photo into a transparent PNG in the app's private cache (cacheDir/generated-media/cutouts). */
object Cutouts {
    /** Long side limit: enough for stickers and edits, small enough for older phones' memory. */
    const val MAX_SIDE = 2048

    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        require(width > 0 && height > 0) { "Not an image." }
        var sample = 1
        while (maxOf(width, height) / sample > maxSide) sample *= 2
        return sample
    }

    fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw BackgroundRemovalException("This file is not a supported image.")
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight); inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw BackgroundRemovalException("This file is not a supported image.")
    }

    fun folder(context: Context) = File(context.cacheDir, "generated-media/cutouts").apply { mkdirs() }

    fun writePng(bitmap: Bitmap, folder: File): File {
        // Keep only the newest few cut-outs; they are copied to the gallery right away.
        folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        val file = File(folder, "cutout-${System.currentTimeMillis()}.png")
        try {
            file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) error("PNG") }
        } catch (failure: Exception) { file.delete(); throw BackgroundRemovalException("Could not save the image without background.") }
        return file
    }

    /** Decode, cut out on the phone, write a PNG. Nothing leaves the phone. */
    suspend fun create(context: Context, remover: BackgroundRemover, bytes: ByteArray): File {
        val source = withContext(Dispatchers.Default) { decode(bytes) }
        val cutout = remover.remove(source)
        return withContext(Dispatchers.IO) { writePng(cutout, folder(context)) }
    }

    private const val KEEP = 5
}

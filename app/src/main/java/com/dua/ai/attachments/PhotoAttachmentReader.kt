package com.dua.ai.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64

/** Only oversized images are flattened/re-encoded; originals on the device remain unchanged. */
object PhotoAttachmentReader {
    fun read(name: String, mimeType: String, input: InputStream): ChatAttachment =
        prepare(listOf(PhotoPolicy.readSource(name, mimeType, input))).single()

    fun prepare(sources: List<AttachmentSource>): List<ChatAttachment> {
        val budgets = PhotoPolicy.budgets(sources)
        return sources.mapIndexed { index, source ->
            val prepared = if (source.bytes.size > budgets[index]) resize(source, budgets[index]) else source
            ChatAttachment(prepared.name, prepared.mimeType, Base64.getEncoder().encodeToString(prepared.bytes))
        }
    }

    private fun resize(source: AttachmentSource, budget: Int): AttachmentSource {
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(source.bytes, 0, source.bytes.size, bounds)
            PhotoPolicy.targetSize(bounds.outWidth, bounds.outHeight)
            require(bounds.outMimeType == source.mimeType) { "Photo could not be decoded. Choose another image." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            decoded = BitmapFactory.decodeByteArray(source.bytes, 0, source.bytes.size, options)
                ?: error("Photo could not be decoded. Choose another image.")
            val orientation = ByteArrayInputStream(source.bytes).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
                }
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (oriented !== decoded) { decoded.recycle(); decoded = null }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for (edge in listOf(2048, 1536, 1024, 768, 512, 256)) {
                val (width, height) = PhotoPolicy.targetSize(oriented.width, oriented.height, edge)
                val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(flattened)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(oriented, null, android.graphics.Rect(0, 0, width, height), paint)
                    for (quality in listOf(85, 70, 55)) {
                        val output = ByteArrayOutputStream()
                        require(flattened.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "Photo could not be resized." }
                        if (output.size() <= budget) return AttachmentSource(PhotoPolicy.resizedName(source.name), "image/jpeg", output.toByteArray())
                    }
                } finally { flattened.recycle() }
            }
            error("Photo could not fit the 2 MB upload limit. Use fewer files or a smaller photo.")
        } catch (_: OutOfMemoryError) { throw IllegalArgumentException("Photo is too large for this device. Choose a smaller image.") }
        finally { if (oriented !== decoded) oriented?.recycle(); decoded?.recycle() }
    }
}

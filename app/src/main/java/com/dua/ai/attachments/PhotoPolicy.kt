package com.dua.ai.attachments

import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Bounds shared with the web photo preparation policy. Documents are never recompressed. */
data class AttachmentSource(val name: String, val mimeType: String, val bytes: ByteArray)

object PhotoPolicy {
    const val MAX_SOURCE_BYTES = 10 * 1024 * 1024
    fun targetSize(width: Int, height: Int, edge: Int = 2048): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxOf(width, height) <= 16000 && width.toLong() * height <= 40000000) {
            "Photo dimensions are too large or invalid. Choose a smaller image."
        }
        val scale = minOf(1.0, edge.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
    fun budgets(files: List<AttachmentSource>): List<Int> {
        require(files.size <= 3) { "Maximum 3 files select karein." }
        if (files.sumOf { it.bytes.size.toLong() } <= AttachmentReader.MAX_BYTES) return files.map { it.bytes.size }
        val images = files.count { it.mimeType.startsWith("image/") }
        val documents = files.filterNot { it.mimeType.startsWith("image/") }.sumOf { it.bytes.size.toLong() }
        require(documents <= AttachmentReader.MAX_BYTES && (images == 0 || documents < AttachmentReader.MAX_BYTES)) {
            "Attachments total 2 MB se chhote rakhein. Use fewer files."
        }
        val budget = if (images > 0) ((AttachmentReader.MAX_BYTES - documents) / images).toInt() else 0
        return files.map { if (it.mimeType.startsWith("image/")) budget else it.bytes.size }
    }
    fun readSource(name: String, mimeType: String, input: InputStream): AttachmentSource {
        require(mimeType in AttachmentReader.types) { "JPEG, PNG, WebP, PDF ya TXT file select karein." }
        val image = mimeType.startsWith("image/")
        val limit = if (image) MAX_SOURCE_BYTES else if (mimeType == "text/plain") 65536 else AttachmentReader.MAX_BYTES
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) {
                if (image) "Photos must be 10 MiB or smaller before resizing."
                else if (mimeType == "text/plain") "TXT file 64 KB se chhoti rakhein."
                else "Attachments total 2 MB se chhote rakhein."
            }
            output.write(buffer, 0, count)
        }
        require(output.size() > 0) { "Empty file select nahi kar sakte." }
        return AttachmentSource(name.take(200), mimeType, output.toByteArray())
    }
    fun resizedName(name: String): String = name.substringBeforeLast('.', name).take(188) + "-resized.jpg"
}

package com.dua.ai.attachments

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

data class ChatAttachment(val name: String, val mimeType: String, val data: String)

object AttachmentReader {
    const val MAX_BYTES = 2 * 1024 * 1024
    val types = arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain")
    fun read(name: String, mimeType: String, input: InputStream): ChatAttachment {
        require(mimeType in types) { "JPEG, PNG, WebP, PDF ya TXT file select karein." }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= MAX_BYTES) { "Attachments total 2 MB se chhote rakhein." }
            out.write(buffer, 0, count)
        }
        require(out.size() > 0) { "Empty file select nahi kar sakte." }
        require(mimeType != "text/plain" || out.size() <= 65536) { "TXT file 64 KB se chhoti rakhein." }
        return ChatAttachment(name.take(200), mimeType, Base64.getEncoder().encodeToString(out.toByteArray()))
    }
}

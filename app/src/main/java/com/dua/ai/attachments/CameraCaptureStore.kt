package com.dua.ai.attachments

import java.io.File

/** Temporary full-photo output shared only with the external camera application. */
class CameraCaptureStore(cacheDir: File) {
    private val directory = File(cacheDir, "camera-captures")

    fun create(): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw java.io.IOException("Camera storage unavailable.")
        // Recover files abandoned by process death without removing an active capture.
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        return File.createTempFile("dua-photo-", ".jpg", directory)
    }

    fun find(name: String): File {
        require(name.startsWith("dua-photo-") && name.endsWith(".jpg") && File(name).name == name) {
            "Invalid camera file."
        }
        val file = File(directory, name)
        require(file.canonicalFile.parentFile == directory.canonicalFile) { "Invalid camera file." }
        return file
    }

    fun read(file: File): ChatAttachment {
        require(find(file.name).canonicalFile == file.canonicalFile) { "Invalid camera file." }
        return try {
            file.inputStream().use { AttachmentReader.read("Camera.jpg", "image/jpeg", it) }
        } finally { file.delete() }
    }

    fun discard(name: String) { find(name).delete() }
}

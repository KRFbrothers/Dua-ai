package com.dua.ai.attachments

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64

data class ChatAttachment(val name: String, val mimeType: String, val data: String)

/** UTF-8 text and source-code files sent inline as text/plain (same list as web/src/core/attachments.mjs). */
object TextFiles {
    const val MAX_BYTES = 256 * 1024
    val EXTENSIONS = setOf("txt", "text", "csv", "tsv", "md", "markdown", "json", "jsonl", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "env", "log",
        "kt", "kts", "java", "js", "mjs", "cjs", "jsx", "ts", "tsx", "py", "go", "rs", "c", "h", "cc", "cpp", "hpp", "cs", "swift", "rb", "php", "dart", "scala", "sql",
        "html", "htm", "css", "scss", "sh", "bash", "zsh", "ps1", "bat", "gradle", "properties", "gitignore", "dockerfile", "tex", "r", "lua", "pl", "vue", "svelte")
    private val APPLICATION_TEXT = setOf("application/json", "application/xml", "application/yaml", "application/x-yaml", "application/javascript",
        "application/x-javascript", "application/x-sh", "application/sql", "application/toml", "application/x-toml", "application/x-python-code")
    /** Picker filter. Many file apps label code files application/octet-stream, so it is included and checked by extension. */
    val PICKER_TYPES = arrayOf("text/*") + APPLICATION_TEXT.toTypedArray() + "application/octet-stream"
    const val UNSUPPORTED = "Choose JPEG, PNG, WebP, PDF, or a UTF-8 text/code file (TXT, CSV, MD, JSON, XML, YAML, LOG, source code)."

    fun extension(name: String): String = name.substringAfterLast('/').let { file ->
        if (file.equals("Dockerfile", ignoreCase = true)) "dockerfile" else file.substringAfterLast('.', "").lowercase()
    }

    fun isText(name: String, mimeType: String): Boolean {
        val type = mimeType.substringBefore(';').trim().lowercase()
        if (type.startsWith("text/") || type in APPLICATION_TEXT || type.endsWith("+json") || type.endsWith("+xml")) return true
        return extension(name) in EXTENSIONS && (type.isEmpty() || type == "application/octet-stream" || type.startsWith("application/"))
    }

    /** Strict UTF-8 decode; rejects binary content with a clear message. */
    fun decode(name: String, bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Text file \"${name.take(60)}\" is larger than 256 KB. Shorten it or split it." }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
            catch (_: CharacterCodingException) { throw IllegalArgumentException("\"${name.take(60)}\" is not UTF-8 text. Save it as UTF-8 and retry.") }
        require('\u0000' !in text) { "\"${name.take(60)}\" looks like a binary file, not text." }
        return text.removePrefix("\uFEFF")
    }

    /** Text of attached text files, as the server labels it, capped for local storage and follow-ups. */
    fun contextText(files: List<ChatAttachment>, limit: Int): String? {
        val joined = files.filter { it.mimeType == "text/plain" }.mapNotNull { file ->
            runCatching { "Attached text file (${file.name}):\n" + decode(file.name, Base64.getDecoder().decode(file.data)) }.getOrNull()
        }.joinToString("\n\n")
        if (joined.isEmpty()) return null
        return if (joined.length <= limit) joined else joined.take(limit - 40).trimEnd() + "\n[…file text truncated on this device]"
    }
}

object AttachmentReader {
    const val MAX_BYTES = 2 * 1024 * 1024
    val MEDIA_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf")
    /** Document picker MIME filter. */
    val types = MEDIA_TYPES + TextFiles.PICKER_TYPES

    /** MIME type sent to the server (text/code files become text/plain), or null when unsupported. */
    fun normalizedType(name: String, mimeType: String): String? = when {
        mimeType in MEDIA_TYPES -> mimeType
        TextFiles.isText(name, mimeType) -> "text/plain"
        else -> null
    }

    fun read(name: String, mimeType: String, input: InputStream): ChatAttachment {
        val type = normalizedType(name, mimeType) ?: throw IllegalArgumentException(TextFiles.UNSUPPORTED)
        val limit = if (type == "text/plain") TextFiles.MAX_BYTES else MAX_BYTES
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= limit) { if (type == "text/plain") "Text file \"${name.take(60)}\" is larger than 256 KB. Shorten it or split it." else "Attachments total 2 MB se chhote rakhein." }
            out.write(buffer, 0, count)
        }
        require(out.size() > 0) { "Empty file select nahi kar sakte." }
        if (type == "text/plain") TextFiles.decode(name, out.toByteArray())
        return ChatAttachment(name.take(200), type, Base64.getEncoder().encodeToString(out.toByteArray()))
    }
}

/** Grok's API takes JPEG/PNG only, so WebP photos are converted on the device for providers without WebP. */
object AttachmentConversion {
    fun needsJpeg(acceptedTypes: List<String>, file: ChatAttachment): Boolean =
        file.mimeType == "image/webp" && "image/webp" !in acceptedTypes && "image/jpeg" in acceptedTypes
}

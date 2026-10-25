package com.dua.ai.attachments

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class AttachmentReaderTest {
    @Test fun utf8FileBytesArePreserved() {
        val bytes = "नमस्ते".toByteArray(Charsets.UTF_8)
        val file = AttachmentReader.read("notes.txt", "text/plain", ByteArrayInputStream(bytes))
        assertArrayEquals(bytes, Base64.getDecoder().decode(file.data))
        assertEquals("text/plain", file.mimeType)
    }
    @Test fun oversizedFileReadIsBounded() {
        assertTrue(runCatching { AttachmentReader.read("x.pdf", "application/pdf", ByteArrayInputStream(ByteArray(AttachmentReader.MAX_BYTES + 1))) }.isFailure)
    }
    @Test fun unsupportedEmptyAndOversizedTextAreRejected() {
        assertTrue(runCatching { AttachmentReader.read("x.exe", "application/octet-stream", ByteArrayInputStream(byteArrayOf(1))) }.isFailure)
        assertTrue(runCatching { AttachmentReader.read("empty.txt", "text/plain", ByteArrayInputStream(byteArrayOf())) }.isFailure)
        assertTrue(runCatching { AttachmentReader.read("big.txt", "text/plain", ByteArrayInputStream(ByteArray(TextFiles.MAX_BYTES + 1) { 97 })) }.isFailure)
    }
    @Test fun codeAndDataFilesAreReadAsUtf8Text() {
        for ((name, mime) in listOf("data.csv" to "text/csv", "README.md" to "application/octet-stream", "Main.kt" to "", "config.json" to "application/json",
            "notes.yaml" to "application/x-yaml", "app.log" to "text/x-log", "script.py" to "text/x-python")) {
            val file = AttachmentReader.read(name, mime, ByteArrayInputStream("a,b\n1,2".toByteArray()))
            assertEquals(name, "text/plain", file.mimeType)
        }
        val big = AttachmentReader.read("big.csv", "text/csv", ByteArrayInputStream(ByteArray(200 * 1024) { 97 }))
        assertEquals("text/plain", big.mimeType)
    }
    @Test fun binaryOrInvalidTextIsRejected() {
        assertTrue(runCatching { AttachmentReader.read("x.bin", "application/octet-stream", ByteArrayInputStream(byteArrayOf(1, 2))) }.isFailure)
        assertTrue(runCatching { AttachmentReader.read("bad.csv", "text/csv", ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }.isFailure)
        assertTrue(runCatching { AttachmentReader.read("nul.txt", "text/plain", ByteArrayInputStream(byteArrayOf(97, 0, 98))) }.isFailure)
        assertFalse(TextFiles.isText("photo.png", "image/png"))
        assertTrue(TextFiles.isText("Notes.MD", ""))
    }
    @Test fun textContextIsCappedAndNamesEachFile() {
        val files = listOf(ChatAttachment("a.csv", "text/plain", Base64.getEncoder().encodeToString("x,y".toByteArray())),
            ChatAttachment("p.png", "image/png", Base64.getEncoder().encodeToString(byteArrayOf(1))),
            ChatAttachment("b.md", "text/plain", Base64.getEncoder().encodeToString("# Title".repeat(100).toByteArray())))
        val text = TextFiles.contextText(files, 200)!!
        assertTrue(text.contains("a.csv")); assertTrue(text.contains("x,y")); assertFalse(text.contains("p.png"))
        assertTrue(text.length <= 200)
        assertNull(TextFiles.contextText(files.subList(1, 2), 200))
    }
    @Test fun onlyWebpIsConvertedForJpegPngProviders() {
        val webp = ChatAttachment("a.webp", "image/webp", "AA==")
        val png = ChatAttachment("a.png", "image/png", "AA==")
        assertTrue(AttachmentConversion.needsJpeg(listOf("text/plain", "image/jpeg", "image/png"), webp))
        assertFalse(AttachmentConversion.needsJpeg(listOf("text/plain", "image/jpeg", "image/png"), png))
        assertFalse(AttachmentConversion.needsJpeg(listOf("image/jpeg", "image/png", "image/webp"), webp))
    }
}

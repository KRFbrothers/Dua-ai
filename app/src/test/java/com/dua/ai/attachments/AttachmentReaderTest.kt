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
        assertTrue(runCatching { AttachmentReader.read("big.txt", "text/plain", ByteArrayInputStream(ByteArray(65537))) }.isFailure)
    }
}

package com.dua.ai.attachments

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class PhotoPolicyTest {
    @Test fun targetPreservesAspectRatioAndNeverUpscales() {
        assertEquals(2048 to 1365, PhotoPolicy.targetSize(6000, 4000))
        assertEquals(100 to 200, PhotoPolicy.targetSize(100, 200))
        for ((width, height) in listOf(0 to 100, 16001 to 1, 10000 to 5000)) {
            assertThrows(IllegalArgumentException::class.java) { PhotoPolicy.targetSize(width, height) }
        }
    }
    @Test fun documentsKeepTheirBytesAndImagesShareTheRemainingBudget() {
        val records = listOf(AttachmentSource("x.pdf", "application/pdf", ByteArray(100)), AttachmentSource("a.png", "image/png", ByteArray(1500000)), AttachmentSource("b.jpg", "image/jpeg", ByteArray(1500000)))
        assertEquals(listOf(100, 1048526, 1048526), PhotoPolicy.budgets(records))
        assertEquals(listOf(1900000, 100), PhotoPolicy.budgets(listOf(AttachmentSource("a.png", "image/png", ByteArray(1900000)), AttachmentSource("b.jpg", "image/jpeg", ByteArray(100)))))
        assertThrows(IllegalArgumentException::class.java) { PhotoPolicy.budgets(listOf(AttachmentSource("x.pdf", "application/pdf", ByteArray(AttachmentReader.MAX_BYTES)), records[1])) }
        assertThrows(IllegalArgumentException::class.java) { PhotoPolicy.budgets(List(4) { records[1] }) }
    }
    @Test fun sourceReaderAcceptsLargerPhotosButBoundsDocumentsTextAndEmptyFiles() {
        val bytes = ByteArray(AttachmentReader.MAX_BYTES + 1) { 7 }
        val photo = PhotoPolicy.readSource("photo.png", "image/png", ByteArrayInputStream(bytes))
        assertArrayEquals(bytes, photo.bytes)
        for ((mime, size) in listOf("image/png" to (PhotoPolicy.MAX_SOURCE_BYTES + 1), "application/pdf" to (AttachmentReader.MAX_BYTES + 1), "text/plain" to (TextFiles.MAX_BYTES + 1), "image/jpeg" to 0)) {
            assertThrows(IllegalArgumentException::class.java) { PhotoPolicy.readSource("file", mime, ByteArrayInputStream(ByteArray(size))) }
        }
        assertThrows(IllegalArgumentException::class.java) { PhotoPolicy.readSource("x.gif", "image/gif", ByteArrayInputStream(byteArrayOf(1))) }
    }
    @Test fun fittingSelectionsPassThroughPreparationWithoutReencoding() {
        val image = AttachmentSource("keep.png", "image/png", ByteArray(1900000) { 7 })
        val text = AttachmentSource("notes.txt", "text/plain", "नमस्ते".toByteArray())
        val prepared = PhotoAttachmentReader.prepare(listOf(image, text))
        assertEquals("keep.png", prepared[0].name); assertEquals("image/png", prepared[0].mimeType)
        assertArrayEquals(image.bytes, java.util.Base64.getDecoder().decode(prepared[0].data))
        assertArrayEquals(text.bytes, java.util.Base64.getDecoder().decode(prepared[1].data))
    }
    @Test fun convertedNamesPreserveUnicodeAndReserveSpaceForTheJpegSuffix() {
        assertEquals("नमस्ते-resized.jpg", PhotoPolicy.resizedName("नमस्ते.png"))
        assertEquals("Camera-resized.jpg", PhotoPolicy.resizedName("Camera.jpg"))
        assertEquals(200, PhotoPolicy.resizedName("x".repeat(300) + ".webp").length)
    }
}

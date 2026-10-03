package com.dua.ai.attachments

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import java.io.File

class CameraCaptureStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun fullPhotoBytesAreRetainedAndTemporaryOutputIsDeleted() {
        val store = CameraCaptureStore(temporary.root)
        val file = store.create()
        val original = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + ByteArray(500_000) { (it % 256).toByte() }
        file.writeBytes(original)
        assertEquals(file, store.find(file.name)) // Recovery after activity recreation uses the saved filename.
        val result = store.read(file)
        assertArrayEquals(original, Base64.getDecoder().decode(result.data))
        assertEquals("image/jpeg", result.mimeType)
        assertFalse(file.exists())
    }

    @Test fun OverSourceLimitAndEmptyCapturesAreRejectedAndCleaned() {
        val store = CameraCaptureStore(temporary.root)
        for (size in listOf(0, PhotoPolicy.MAX_SOURCE_BYTES + 1)) {
            val file = store.create()
            file.writeBytes(ByteArray(size))
            assertThrows(IllegalArgumentException::class.java) { store.read(file) }
            assertFalse(file.exists())
        }
    }

    @Test fun cancelledCaptureAndAbandonedFilesAreCleanedWithoutDeletingFreshCapture() {
        val store = CameraCaptureStore(temporary.root)
        val cancelled = store.create()
        store.discard(cancelled.name)
        assertFalse(cancelled.exists())
        val abandoned = store.create()
        abandoned.setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        val fresh = store.create()
        assertFalse(abandoned.exists())
        assertTrue(fresh.exists())
    }

    @Test fun preparationProcessorReceivesFullCaptureAndCleanupRunsAfterSuccessOrFailure() {
        val store = CameraCaptureStore(temporary.root)
        val capture = store.create().apply { writeBytes(ByteArray(AttachmentReader.MAX_BYTES + 1) { 1 }) }
        val prepared = store.read(capture) { name, mime, input ->
            val source = PhotoPolicy.readSource(name, mime, input)
            assertEquals(AttachmentReader.MAX_BYTES + 1, source.bytes.size)
            ChatAttachment("Camera-resized.jpg", "image/jpeg", "prepared")
        }
        assertEquals("Camera-resized.jpg", prepared.name); assertFalse(capture.exists())
        val failed = store.create().apply { writeBytes(byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { store.read(failed) { _, _, _ -> throw IllegalArgumentException("Cannot resize") } }
        assertFalse(failed.exists())
    }

    @Test fun namesCannotEscapeCameraDirectory() {
        val store = CameraCaptureStore(temporary.root)
        for (name in listOf("../dua-photo-test.jpg", "/tmp/dua-photo-test.jpg", "unrelated.jpg")) {
            assertThrows(IllegalArgumentException::class.java) { store.find(name) }
        }
        val unrelated = File(temporary.root, "dua-photo-outside.jpg").apply { writeText("retain") }
        assertThrows(IllegalArgumentException::class.java) { store.read(unrelated) }
        assertTrue(unrelated.exists())
    }
}

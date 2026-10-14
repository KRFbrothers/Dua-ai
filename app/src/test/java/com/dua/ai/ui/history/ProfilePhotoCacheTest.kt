package com.dua.ai.ui.history

import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilePhotoCacheTest {
    @Test fun acceptsExactlyTwoMillionBytesWithoutTruncation() {
        val bytes = ByteArray(2_000_000) { (it % 251).toByte() }
        assertArrayEquals(bytes, ProfilePhotoCache.readBoundedPhoto(ByteArrayInputStream(bytes)))
    }

    @Test fun abortsAnUnboundedResponseWithoutReadingItToTheEnd() {
        var consumed = 0
        val input = object : InputStream() {
            override fun read(): Int = error("Use bounded chunk reads")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                assertTrue("Each read is at most 8 KB", length <= 8 * 1024)
                consumed += length
                check(consumed <= 2_000_000 + 8 * 1024) { "Kept reading after the limit" }
                buffer.fill(1, offset, offset + length)
                return length // No Content-Length or end-of-stream; the loader must stop itself.
            }
        }
        assertNull(ProfilePhotoCache.readBoundedPhoto(input))
        assertTrue(consumed > 2_000_000)
        assertTrue(consumed <= 2_000_000 + 8 * 1024)
    }

    @Test fun shortStreamReadsPreserveAllBytes() {
        val bytes = ByteArray(10_003) { (it % 251).toByte() }
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(17, length))
        }
        assertArrayEquals(bytes, ProfilePhotoCache.readBoundedPhoto(input))
    }

    @Test fun rejectsEmptyAndInvalidImageBounds() {
        assertNull(ProfilePhotoCache.decodeThumbnail(byteArrayOf()))
        assertNull(ProfilePhotoCache.decodeThumbnail("not an image".toByteArray()))
    }

    @Test fun compressedLargePortraitAndLandscapeImagesDecodeAsThumbnails() {
        for ((width, height) in listOf(2048 to 1024, 1024 to 2048)) {
            val bytes = png(width, height)
            assertTrue("Encoded bytes alone do not cap decoded allocation", bytes.size < 2_000_000)
            val image = requireNotNull(ProfilePhotoCache.decodeThumbnail(bytes))
            assertEquals(width / 16, image.width)
            assertEquals(height / 16, image.height)
            assertTrue(image.width <= 128 && image.height <= 128)
        }
    }

    @Test fun smallImagesKeepTheirOriginalSize() {
        val image = requireNotNull(ProfilePhotoCache.decodeThumbnail(png(40, 30)))
        assertEquals(40, image.width)
        assertEquals(30, image.height)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}

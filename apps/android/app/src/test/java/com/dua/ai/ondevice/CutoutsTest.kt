package com.dua.ai.ondevice

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CutoutsTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun jpeg(width: Int = 40, height: Int = 20): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test fun bigPhotosAreScaledDownBeforeSegmentation() {
        assertEquals(1, Cutouts.sampleSize(2048, 1000))
        assertEquals(2, Cutouts.sampleSize(4000, 3000))
        assertEquals(4, Cutouts.sampleSize(1000, 8192))
    }

    @Test fun createsATransparentPngInThePrivateCache() = runBlocking {
        val remover = FakeBackgroundRemover()
        val file = Cutouts.create(app, remover, jpeg())
        assertEquals(1, remover.calls)
        assertTrue(file.path.startsWith(app.cacheDir.path))
        assertTrue(file.path.contains("generated-media/cutouts"))
        assertTrue(file.name.endsWith(".png"))
        val header = file.readBytes().take(4)
        assertEquals(listOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), header)
        val decoded = BitmapFactory.decodeFile(file.path)
        assertEquals(40, decoded.width)
    }

    @Test fun notAnImageOrFailedCutoutGivesAFriendlyError() {
        val notImage = runCatching { runBlocking { Cutouts.create(app, FakeBackgroundRemover(), "hello".toByteArray()) } }.exceptionOrNull()
        assertTrue(notImage is BackgroundRemovalException)
        val failed = runCatching { runBlocking { Cutouts.create(app, FakeBackgroundRemover(failure = "No clear subject was found in this image."), jpeg()) } }.exceptionOrNull()
        assertEquals("No clear subject was found in this image.", failed?.message)
    }

    @Test fun keepsOnlyTheNewestFewCutouts() {
        val folder = Cutouts.folder(app)
        folder.listFiles()?.forEach { it.delete() }
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        repeat(5) { index -> java.io.File(folder, "old-$index.png").apply { writeText("x"); setLastModified(1000L + index) } }
        Cutouts.writePng(bitmap, folder)
        assertEquals(6, folder.listFiles()!!.size)
        Cutouts.writePng(bitmap, folder)
        assertTrue(folder.listFiles()!!.size <= 6)
        assertFalse(java.io.File(folder, "old-0.png").exists())
    }
}

package com.dua.ai.attachments

import android.net.Uri
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DroppedFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun uri(n: Int) = Uri.parse("content://com.example.files/item/$n")
    private val bytes = mapOf(1 to ByteArray(10) { 1 }, 2 to ByteArray(2_000) { 2 }, 3 to ByteArray(5) { 3 }, 4 to ByteArray(5) { 4 })

    private fun receive(store: DroppedFileStore, items: List<DropItem>, max: Long = 1_000, names: Map<Int, String?> = emptyMap()) =
        store.receive(items, max,
            nameOf = { names[it.lastPathSegment!!.toInt()] ?: "file-${it.lastPathSegment}.bin" },
            typeOf = { "application/octet-stream" },
            open = { u -> bytes[u.lastPathSegment!!.toInt()]?.let(::ByteArrayInputStream) })

    @Test fun copiesFilesWithTheirNamesIntoPrivateCache() {
        val store = DroppedFileStore(temporary.root)
        val result = receive(store, listOf(DropItem(uri(1)), DropItem(uri(3))), names = mapOf(1 to "Holiday photo.jpg"))
        assertEquals(listOf("Holiday photo.jpg", "file-3.bin"), result.files.map { it.name })
        assertArrayEquals(bytes[1], result.files[0].readBytes())
        result.files.forEach { assertEquals(store.directory.canonicalFile, it.canonicalFile.parentFile!!.parentFile) }
        assertEquals("", result.notice)
    }

    @Test fun tooLargeAndUnreadableFilesAreSkippedAndExplained() {
        val store = DroppedFileStore(temporary.root)
        val result = receive(store, listOf(DropItem(uri(2)), DropItem(uri(9)), DropItem(uri(1))))
        assertEquals(listOf("file-1.bin"), result.files.map { it.name })
        assertTrue(result.notice, result.notice.contains("too large"))
        assertTrue(result.notice, result.notice.contains("could not be read"))
        // The failed copies leave nothing behind.
        assertEquals(1, store.directory.listFiles()!!.size)
    }

    @Test fun onlyThreeFilesAreTakenAndTextGoesToTheDraft() {
        val store = DroppedFileStore(temporary.root)
        val result = receive(store, listOf(DropItem(uri(1)), DropItem(text = "  hello  "), DropItem(uri(3)), DropItem(uri(4)), DropItem(uri(1)), DropItem(text = "world")))
        assertEquals(3, result.files.size)
        assertEquals("hello\nworld", result.text)
        assertTrue(result.notice.contains("first 3"))
    }

    @Test fun anEmptyDropSaysWhatToDrop() {
        assertTrue(receive(DroppedFileStore(temporary.root), listOf(DropItem(text = "   "))).notice.contains("Nothing to attach"))
    }

    @Test fun oldCopiesAreRemoved() {
        var now = 10L * 24 * 60 * 60 * 1000
        val store = DroppedFileStore(temporary.root) { now }
        val old = store.copy("old.txt", 100) { ByteArrayInputStream(byteArrayOf(1)) }
        old.parentFile!!.setLastModified(now - 25L * 60 * 60 * 1000)
        val fresh = store.copy("new.txt", 100) { ByteArrayInputStream(byteArrayOf(1)) }
        fresh.parentFile!!.setLastModified(now)
        store.sweep()
        assertFalse(old.exists()); assertTrue(fresh.exists())
    }

    @Test fun copyRejectsPathNamesAndMissingStreams() {
        val store = DroppedFileStore(temporary.root)
        assertThrows(IllegalArgumentException::class.java) { store.copy("../escape.txt", 10) { ByteArrayInputStream(byteArrayOf()) } }
        assertThrows(IOException::class.java) { store.copy("missing.txt", 10) { null } }
        assertFalse(File(temporary.root, "escape.txt").exists())
    }

    @Test fun safeNameStripsFoldersAndAddsAKnownExtension() {
        assertEquals("passwd", DroppedFileStore.safeName("../../etc/passwd", null))
        assertEquals("Dropped file.jpg", DroppedFileStore.safeName("..", "image/jpeg"))
        assertEquals("report.pdf", DroppedFileStore.safeName("C:\\Users\\me\\report.pdf", "application/pdf"))
        assertEquals("notes.txt", DroppedFileStore.safeName("notes", "text/plain; charset=utf-8"))
        assertEquals("ab.png", DroppedFileStore.safeName("a\u0000b", "image/png"))
        val long = DroppedFileStore.safeName("x".repeat(300) + ".jpeg", null)
        assertTrue(long.length <= 120); assertTrue(long.endsWith(".jpeg"))
    }
}

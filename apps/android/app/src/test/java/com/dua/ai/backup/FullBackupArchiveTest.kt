package com.dua.ai.backup

import android.app.Application
import com.dua.ai.files.FileRef
import com.dua.ai.history.ChatBackup
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.projects.ProjectCodec
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FullBackupArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = "11111111-2222-4333-8444-555555555555"
    private val path = "$owner/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf"
    private fun fixture(): PreparedFullBackup {
        val dir = temp.newFolder()
        val file = File(dir, "raw").apply { writeBytes(byteArrayOf(0, 1, 2, 3, 127, -1)) }
        val chat = SavedChat("chat", "Dua", "Keep draft", listOf(ChatMessage(id = "message", text = "Read my file", isUser = true, model = "Dua",
            files = listOf(FileRef(path, "Original.pdf", "application/pdf", file.length())))), 10)
        val data = FullBackupData(ChatBackup(listOf(chat), ProjectCodec.defaults), listOf("Prefer Hinglish"), owner,
            listOf(BackupAsset("00000", path, "Original.pdf", "application/pdf", file.length(), FullBackupArchive.digest(file))))
        return PreparedFullBackup(data, mapOf("00000" to file), dir)
    }

    @Test fun fullArchiveKeepsNotesDraftsMetadataAndExactRawBytes() {
        val prepared = fixture(); val zip = temp.newFile()
        FullBackupArchive.writeZip(prepared, zip)
        val read = FullBackupArchive.readZip(zip, temp.newFolder())
        assertEquals(prepared.data, read.data)
        assertArrayEquals(prepared.files.getValue("00000").readBytes(), read.files.getValue("00000").readBytes())
        assertEquals(listOf("Prefer Hinglish"), read.data.memory)
        assertEquals("Keep draft", read.data.backup.chats.single().draft)
    }

    @Test fun missingRawReferencesAndCrossAccountPathsAreRejected() {
        val data = fixture().data
        assertThrows(IllegalArgumentException::class.java) { FullBackupArchive.validate(data.copy(assets = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { FullBackupArchive.validate(data.copy(ownerId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")) }
        assertThrows(IllegalArgumentException::class.java) { FullBackupArchive.validate(data.copy(assets = data.assets + data.assets.single())) }
    }

    @Test fun changedRawBytesCannotBePublishedWithAnOldChecksum() {
        val prepared = fixture()
        prepared.files.getValue("00000").appendBytes(byteArrayOf(4))
        assertThrows(IllegalArgumentException::class.java) { FullBackupArchive.writeZip(prepared, temp.newFile()) }
    }

    @Test fun unexpectedZipEntriesAreRejectedWithoutExtractingPaths() {
        val zip = temp.newFile()
        ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry("../outside")); out.write(1); out.closeEntry() }
        val target = temp.newFolder()
        assertThrows(IllegalArgumentException::class.java) { FullBackupArchive.readZip(zip, target) }
        assertFalse(File(target.parentFile, "outside").exists())
    }

    @Test fun rawChecksumAndUnexpectedTrailingEntriesAreVerifiedBeforeRestore() {
        val original = temp.newFile()
        FullBackupArchive.writeZip(fixture(), original)
        fun rewrite(tamper: Boolean): File = temp.newFile().also { target ->
            ZipOutputStream(target.outputStream()).use { out ->
                ZipInputStream(original.inputStream()).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        val bytes = input.readBytes()
                        if (tamper && entry.name.startsWith("files/")) bytes[0] = (bytes[0].toInt() xor 1).toByte()
                        out.putNextEntry(ZipEntry(entry.name)); out.write(bytes); out.closeEntry()
                    }
                }
                if (!tamper) { out.putNextEntry(ZipEntry("extra")); out.write(1); out.closeEntry() }
            }
        }
        for (tamper in listOf(true, false)) assertThrows(IllegalArgumentException::class.java) {
            FullBackupArchive.readZip(rewrite(tamper), temp.newFolder())
        }
    }

    @Test fun manifestRejectsDeepNestingAndTrailingJsonBeforeParsingChatData() {
        for (raw in listOf("{\"extra\":" + "[".repeat(80) + "0" + "]".repeat(80) + "}", "{}{}")) {
            val zip = temp.newFile()
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json")); out.write(raw.toByteArray()); out.closeEntry()
            }
            assertThrows(IllegalArgumentException::class.java) { FullBackupArchive.readZip(zip, temp.newFolder()) }
        }
    }

    @Test fun memoryMergeKeepsCurrentNotesAndRejectsOverflowBeforeRestoringFiles() {
        assertEquals(listOf("Existing", "Imported"), FullBackupArchive.mergeMemory(listOf("Existing"), listOf("Existing", "Imported")))
        assertThrows(IllegalArgumentException::class.java) { FullBackupArchive.mergeMemory((1..10).map { "Note $it" }, listOf("Extra")) }
    }

    @Test fun inlineRecoveryPathsStayStableAcrossRetriesAndAreOwnerScoped() {
        val asset = fixture().data.assets.single().copy(path = null, chatId = "chat", messageId = "message")
        val first = BackupFileTransfer.restorePath(asset, owner)
        assertEquals(first, BackupFileTransfer.restorePath(asset.copy(id = "00017"), owner))
        assertTrue(first.startsWith("$owner/")); assertTrue(first.endsWith(".pdf"))
        assertNotEquals(first, BackupFileTransfer.restorePath(asset.copy(sha256 = "0".repeat(64)), owner))
    }
}

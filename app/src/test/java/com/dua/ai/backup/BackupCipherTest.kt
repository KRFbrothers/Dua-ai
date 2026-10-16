package com.dua.ai.backup

import android.app.Application
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupCipherTest {
    private val password get() = "correct horse battery staple".toCharArray()
    private fun encrypt(raw: ByteArray): ByteArray = ByteArrayOutputStream().also { BackupCipher.encrypt(ByteArrayInputStream(raw), it, password) }.toByteArray()
    private fun decrypt(raw: ByteArray, key: CharArray = password): ByteArray = ByteArrayOutputStream().also { BackupCipher.decrypt(ByteArrayInputStream(raw), it, key) }.toByteArray()

    @Test fun multiSegmentRawBytesRoundTripAndEachBackupHasFreshEncryption() {
        val raw = ByteArray(3 * 1024 * 1024 + 7) { ((it * 31) % 251).toByte() }
        val first = encrypt(raw)
        assertArrayEquals(raw, decrypt(first))
        val second = encrypt(raw)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(raw, decrypt(second))
    }

    @Test fun wrongPasswordModifiedHeaderChangedDataTruncationAndTrailingBytesAreRejected() {
        val raw = encrypt(ByteArray(2 * 1024 * 1024 + 5) { 42 })
        assertThrows(IllegalArgumentException::class.java) { decrypt(raw, "another long password".toCharArray()) }
        val mutations = listOf(raw.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() },
            raw.copyOf().also { it[it.size / 2] = 99 }, raw.copyOf(raw.size - 1), raw + byteArrayOf(1, 2, 3))
        mutations.forEach { changed -> assertThrows(IllegalArgumentException::class.java) { decrypt(changed) } }
    }

    @Test fun plainMetadataIsNotVisibleAndPasswordsMustMeetTheChosenMinimum() {
        val text = "Personal note: prefer Hinglish. Private photo bytes."
        val encrypted = encrypt(text.toByteArray())
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains(text))
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.validatePassword("short".toCharArray()) }
        BackupCipher.validatePassword("a".repeat(12).toCharArray())
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.validatePassword("a".repeat(257).toCharArray()) }
    }
}

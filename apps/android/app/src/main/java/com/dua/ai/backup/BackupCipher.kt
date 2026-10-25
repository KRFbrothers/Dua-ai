package com.dua.ai.backup

import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Tink Streaming AEAD with a password-wrapped keyset; no passwords or unwrapped keys on disk. */
object BackupCipher {
    private val magic = "DUABKP01".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    const val MAX_BYTES = 1024L * 1024 * 1024
    init { StreamingAeadConfig.register() }

    fun validatePassword(password: CharArray) {
        require(password.size in 12..256) { "Use a backup password with 12–256 characters." }
    }

    fun encrypt(input: InputStream, output: OutputStream, password: CharArray, checkActive: () -> Unit = {}) {
        validatePassword(password)
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val context = magic + salt + nonce
        val handle = KeysetHandle.generateNew(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB)
        val keyBytes = ByteArrayOutputStream().also { CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(it)) }.toByteArray()
        val wrapped = try { wrappingCipher(Cipher.ENCRYPT_MODE, password, salt, nonce).apply { updateAAD(context) }.doFinal(keyBytes) }
            finally { keyBytes.fill(0) }
        val header = context + java.nio.ByteBuffer.allocate(4).putInt(wrapped.size).array() + wrapped
        output.write(header)
        handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java).newEncryptingStream(NonClosingOutput(output), header).use { encrypted ->
            copyBounded(input, encrypted, MAX_BYTES, checkActive)
        }
    }

    /** Consume the complete authenticated stream into a temporary file before opening the ZIP. */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray, checkActive: () -> Unit = {}) {
        validatePassword(password)
        val stream = DataInputStream(input)
        val context = ByteArray(magic.size + 16 + 12)
        stream.readFully(context)
        require(context.copyOfRange(0, magic.size).contentEquals(magic)) { "Choose a password-protected Dua AI backup (.duabackup)." }
        val size = stream.readInt()
        require(size in 32..16_384) { "Backup key header is invalid." }
        val wrapped = ByteArray(size).also(stream::readFully)
        val header = context + java.nio.ByteBuffer.allocate(4).putInt(size).array() + wrapped
        try {
            val raw = wrappingCipher(Cipher.DECRYPT_MODE, password, context.copyOfRange(8, 24), context.copyOfRange(24, 36))
                .apply { updateAAD(context) }.doFinal(wrapped)
            val handle = try { CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(raw)) } finally { raw.fill(0) }
            handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java).newDecryptingStream(NonClosingInput(stream), header).use { decrypted ->
                copyBounded(decrypted, output, MAX_BYTES, checkActive)
            }
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { throw IllegalArgumentException("The password is incorrect or the backup is incomplete or changed. Nothing was restored.") }
    }

    private fun wrappingCipher(mode: Int, password: CharArray, salt: ByteArray, nonce: ByteArray): Cipher {
        val specification = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded }
            finally { specification.clearPassword() }
        return try { Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)) } }
            finally { key.fill(0) }
    }

    internal fun copyBounded(input: InputStream, output: OutputStream, limit: Long, checkActive: () -> Unit = {}): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        try {
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= limit) { "Backup exceeds its size limit." }
                output.write(buffer, 0, count)
            }
            return total
        } finally { buffer.fill(0) }
    }

    private class NonClosingOutput(output: OutputStream) : FilterOutputStream(output) { override fun close() = flush() }
    private class NonClosingInput(input: InputStream) : FilterInputStream(input) { override fun close() {} }
}

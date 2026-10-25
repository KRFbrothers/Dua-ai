package com.dua.ai.backup.drive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dua.ai.BuildConfig
import com.dua.ai.backup.BackupCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DriveBackupRecord(val owner: String = "", val email: String = "", val connected: Boolean = false,
    val enabled: Boolean = false, val generation: String = "", val phase: String = "off", val message: String = "Automatic backup is off.",
    val lastSuccess: Long = 0, val lastFileId: String = "", val progress: Int? = null, val lastSize: Long? = null)

/** Only the backup password is sealed here. Google Play services owns Google tokens; we never persist them. */
interface BackupPasswordProtector {
    fun seal(password: CharArray, associatedData: String): String
    fun open(value: String, associatedData: String): CharArray
}

class KeystoreBackupPasswordProtector : BackupPasswordProtector {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun seal(password: CharArray, associatedData: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(associatedData.toByteArray()) }
        val buffer = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
        val bytes = ByteArray(buffer.remaining()).also(buffer::get)
        return try { JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP)).toString() }
        finally { bytes.fill(0); if (buffer.hasArray()) buffer.array().fill(0) }
    }
    override fun open(value: String, associatedData: String): CharArray {
        val json = JSONObject(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(json.getString("iv"), Base64.NO_WRAP)))
            updateAAD(associatedData.toByteArray())
        }
        val bytes = cipher.doFinal(Base64.decode(json.getString("data"), Base64.NO_WRAP))
        return try {
            val chars = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes))
            CharArray(chars.remaining()).also { chars.get(it); if (chars.hasArray()) chars.array().fill('\u0000') }
        } finally { bytes.fill(0) }
    }
    companion object { private const val ALIAS = "dua-drive-backup-password-v1" }
}

class DriveBackupStore(context: Context, private val protector: BackupPasswordProtector = KeystoreBackupPasswordProtector()) {
    private val storage = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun read(): DriveBackupRecord = try {
        val raw = storage.getString("record", null)
        if (raw == null) DriveBackupRecord() else JSONObject(raw).let { json ->
            require(json.getString("backend") == BuildConfig.DUA_API_BASE_URL)
            DriveBackupRecord(json.getString("owner"), json.getString("email"), json.getBoolean("connected"), json.getBoolean("enabled"),
                json.getString("generation"), json.getString("phase"), json.getString("message"), json.getLong("lastSuccess"), json.getString("lastFileId"),
                json.optInt("progress", -1).takeIf { it in 0..100 }, json.optLong("lastSize", -1).takeIf { it > 0 }).also { validate(it) }
        }
    } catch (_: Exception) { DriveBackupRecord(phase = "setup", message = "Backup settings could not be read. Connect Drive and choose the password again.") }

    @Synchronized fun connect(owner: String, email: String): DriveBackupRecord {
        val old = mutable.value
        if (old.owner == owner && old.email.equals(email, true) && old.connected) {
            return update(old.copy(phase = if (old.enabled) "queued" else "ready", message = "Drive permission verified. Private app backup folder is available."))
        }
        val next = DriveBackupRecord(owner, email, connected = true, generation = UUID.randomUUID().toString(), phase = "ready",
            message = "Drive connected. Enable daily backup to store the backup password on this phone.")
        return update(next, removePassword = true)
    }

    @Synchronized fun enable(owner: String, password: CharArray): DriveBackupRecord {
        BackupCipher.validatePassword(password)
        val old = mutable.value
        check(old.connected && old.owner == owner) { "Connect the signed-in account's Drive first." }
        val next = old.copy(enabled = true, generation = UUID.randomUUID().toString(), phase = "queued", progress = null,
            message = "Waiting for Wi-Fi or another unmetered network. Android chooses the daily run time.")
        return update(next, sealed = protector.seal(password, aad(next)))
    }

    @Synchronized fun disable(message: String = "Automatic backup is off. Existing Drive backups were kept."): DriveBackupRecord =
        update(mutable.value.copy(enabled = false, generation = UUID.randomUUID().toString(), phase = "off", progress = null, message = message), removePassword = true)

    @Synchronized fun accountChanged(owner: String?) {
        val current = mutable.value
        if (current.owner.isNotEmpty() && current.owner != owner) update(DriveBackupRecord(generation = UUID.randomUUID().toString(),
            message = "Account changed. Connect Drive and explicitly enable backup for the current account."), removePassword = true)
    }

    @Synchronized fun password(expected: DriveBackupRecord): CharArray {
        requireCurrent(expected)
        return protector.open(storage.getString("password", null) ?: error("Choose a backup password again."), aad(expected)).also {
            try { BackupCipher.validatePassword(it) } catch (failure: Exception) { it.fill('\u0000'); throw failure }
        }
    }

    @Synchronized fun requireCurrent(expected: DriveBackupRecord, enabled: Boolean = true) {
        val current = mutable.value
        check(current.owner == expected.owner && current.generation == expected.generation && current.connected && (!enabled || current.enabled)) {
            "Backup permission or account changed. Nothing else was uploaded or deleted."
        }
    }

    @Synchronized fun report(expected: DriveBackupRecord, phase: String, message: String, progress: Int? = null) {
        if (mutable.value.generation != expected.generation || mutable.value.owner != expected.owner) return
        update(mutable.value.copy(phase = phase, message = message, progress = progress))
    }
    @Synchronized fun success(expected: DriveBackupRecord, fileId: String, time: Long, size: Long? = null) {
        requireCurrent(expected)
        update(mutable.value.copy(lastSuccess = time, lastFileId = fileId, lastSize = size, phase = "success", progress = null,
            message = "Encrypted Drive backup verified. Keeping the latest 3 completed backups."))
    }

    private fun update(next: DriveBackupRecord, sealed: String? = null, removePassword: Boolean = false): DriveBackupRecord {
        validate(next)
        val json = JSONObject().put("backend", BuildConfig.DUA_API_BASE_URL).put("owner", next.owner).put("email", next.email)
            .put("connected", next.connected).put("enabled", next.enabled).put("generation", next.generation).put("phase", next.phase)
            .put("message", next.message).put("lastSuccess", next.lastSuccess).put("lastFileId", next.lastFileId).put("progress", next.progress ?: -1)
            .put("lastSize", next.lastSize ?: -1)
        val previousRecord = storage.getString("record", null)
        val previousPassword = storage.getString("password", null)
        val editor = storage.edit().putString("record", json.toString())
        if (removePassword) editor.remove("password") else if (sealed != null) editor.putString("password", sealed)
        if (!editor.commit()) {
            storage.edit().putString("record", previousRecord).putString("password", previousPassword).apply()
            error("Backup settings could not be saved. Retry.")
        }
        mutable.value = next
        return next
    }
    private fun aad(record: DriveBackupRecord) = "${BuildConfig.DUA_API_BASE_URL}\u0000${record.owner}\u0000${record.generation}\u0000drive-backup-password-v1"
    private fun validate(value: DriveBackupRecord) {
        require(value.owner.isEmpty() || value.owner.matches(Regex("[0-9a-f-]{36}")))
        require(value.email.length <= 320 && (!value.connected || value.owner.isNotEmpty() && value.email.contains('@')))
        require(!value.enabled || value.connected)
        require(value.generation.length <= 50 && value.lastSuccess >= 0 && value.message.length <= 1000 && value.lastFileId.length <= 200)
        require(value.lastSize == null || value.lastSize in 1..DriveBackupApi.MAX_ENCRYPTED_BYTES)
        require(value.phase in setOf("off", "ready", "queued", "running", "success", "error", "setup", "warning"))
    }
    companion object { const val PREFS = "dua_drive_backup" }
}

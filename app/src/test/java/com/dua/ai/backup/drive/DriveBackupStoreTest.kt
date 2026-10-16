package com.dua.ai.backup.drive

import android.app.Application
import android.content.Context
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Standard-JCA stand-in for AndroidKeyStore; exercises authenticated storage and AAD without device keys. */
internal class TestPasswordProtector : BackupPasswordProtector {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun seal(password: CharArray, associatedData: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key); updateAAD(associatedData.toByteArray()) }
        return JSONObject().put("iv", Base64.getEncoder().encodeToString(cipher.iv))
            .put("data", Base64.getEncoder().encodeToString(cipher.doFinal(String(password).toByteArray()))).toString()
    }
    override fun open(value: String, associatedData: String): CharArray {
        val json = JSONObject(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.getDecoder().decode(json.getString("iv"))))
            updateAAD(associatedData.toByteArray())
        }
        return String(cipher.doFinal(Base64.getDecoder().decode(json.getString("data")))).toCharArray()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveBackupStoreTest {
    private val owner = "11111111-2222-4333-8444-555555555555"
    private val email = "owner@example.test"
    private val password get() = "private backup password".toCharArray()
    private lateinit var app: Context
    private lateinit var store: DriveBackupStore
    private val protector = TestPasswordProtector()
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(DriveBackupStore.PREFS, 0).edit().clear().commit()
        store = DriveBackupStore(app, protector)
    }
    @Test fun consentAndEncryptedPasswordPersistWithoutPlaintextOrGoogleTokens() {
        assertFalse(store.state.value.enabled)
        store.connect(owner, email)
        assertFalse(store.state.value.enabled)
        val record = store.enable(owner, password)
        val raw = app.getSharedPreferences(DriveBackupStore.PREFS, 0).all.toString()
        assertFalse(raw.contains(String(password))); assertFalse(raw.contains("accessToken")); assertFalse(raw.contains("refreshToken"))
        val reloaded = DriveBackupStore(app, protector)
        assertEquals(record, reloaded.state.value)
        assertArrayEquals(password, reloaded.password(record))
    }
    @Test fun disablingRemovesPasswordAndInvalidatesInFlightGenerationWithoutDeletingLastSuccess() {
        store.connect(owner, email); val record = store.enable(owner, password)
        store.success(record, "file-id", 1234)
        store.disable()
        assertFalse(store.state.value.enabled)
        assertEquals(1234L, store.state.value.lastSuccess)
        assertFalse(app.getSharedPreferences(DriveBackupStore.PREFS, 0).contains("password"))
        assertThrows(IllegalStateException::class.java) { store.password(record) }
        store.report(record, "success", "stale completion")
        assertEquals("off", store.state.value.phase)
    }
    @Test fun signOutOrAnotherOwnerClearsConsentAndCannotReuseTheSavedPassword() {
        store.connect(owner, email); val record = store.enable(owner, password)
        store.accountChanged("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
        assertFalse(store.state.value.connected); assertFalse(store.state.value.enabled)
        assertEquals("", store.state.value.owner)
        assertThrows(IllegalStateException::class.java) { store.password(record) }
    }
    @Test fun changingMetadataGenerationCannotDecryptThePasswordAndDamagedRecordsFailClosed() {
        store.connect(owner, email); store.enable(owner, password)
        val prefs = app.getSharedPreferences(DriveBackupStore.PREFS, 0)
        val raw = JSONObject(prefs.getString("record", null)!!).put("generation", "different-generation").toString()
        prefs.edit().putString("record", raw).commit()
        val changed = DriveBackupStore(app, protector)
        assertThrows(Exception::class.java) { changed.password(changed.state.value) }
        prefs.edit().putString("record", "damaged").commit()
        val damaged = DriveBackupStore(app, protector)
        assertFalse(damaged.state.value.enabled); assertEquals("setup", damaged.state.value.phase)
    }
    @Test fun reconnectingSameAccountKeepsPasswordBindingButNewEnableNeedsANewPassword() {
        store.connect(owner, email); val record = store.enable(owner, password)
        store.connect(owner, email)
        assertArrayEquals(password, store.password(store.state.value))
        store.disable(); store.enable(owner, "another backup password".toCharArray())
        assertNotEquals(record.generation, store.state.value.generation)
        assertArrayEquals("another backup password".toCharArray(), store.password(store.state.value))
    }
    @Test fun encryptedPasswordAndConsentAreExcludedFromBothSystemBackupAndDeviceTransfer() {
        val entries = mutableListOf<String>()
        for (resource in listOf(com.dua.ai.R.xml.account_backup_rules, com.dua.ai.R.xml.account_data_extraction_rules)) {
            app.resources.getXml(resource).use { xml ->
                while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "exclude" &&
                        xml.getAttributeValue(null, "domain") == "sharedpref" && xml.getAttributeValue(null, "path") == "dua_drive_backup.xml") entries += "excluded"
                    xml.next()
                }
            }
        }
        assertEquals(3, entries.size) // legacy backup, Android 12+ cloud backup, device transfer
    }
}

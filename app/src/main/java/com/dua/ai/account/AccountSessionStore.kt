package com.dua.ai.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dua.ai.BuildConfig
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Session tokens stay encrypted on this installation and are excluded from backup. */
class AccountSessionStore(context: Context) : SessionPersistence {
    private val storage = context.getSharedPreferences("dua_account_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("dua-account-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("dua-account-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
    }
    override fun read(): AccountSession? {
        val raw = storage.getString("encrypted", null) ?: return null
        val data = JSONObject(raw); require(data.getString("backend") == BuildConfig.DUA_API_BASE_URL)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(data.getString("iv"), Base64.NO_WRAP))) }
        cipher.updateAAD(BuildConfig.DUA_API_BASE_URL.toByteArray(Charsets.UTF_8))
        return AccountSessionCodec.decode(JSONObject(String(cipher.doFinal(Base64.decode(data.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8)))
    }
    override fun write(value: AccountSession?) {
        val previous = storage.getString("encrypted", null)
        val raw = value?.let {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            cipher.updateAAD(BuildConfig.DUA_API_BASE_URL.toByteArray(Charsets.UTF_8))
            JSONObject().put("backend", BuildConfig.DUA_API_BASE_URL).put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).put("data", Base64.encodeToString(cipher.doFinal(AccountSessionCodec.encode(it).toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).toString()
        }
        if (!storage.edit().putString("encrypted", raw).commit()) { storage.edit().putString("encrypted", previous).apply(); error("Session could not be saved. Retry.") }
    }
}

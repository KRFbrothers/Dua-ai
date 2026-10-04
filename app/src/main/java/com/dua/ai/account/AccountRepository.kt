package com.dua.ai.account

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class AccountState(val configured: Boolean = false, val user: AccountUser? = null, val busy: Boolean = false, val notice: String = "")
class AccountRepository(private val api: AccountApi, private val store: SessionPersistence) {
    constructor(context: Context) : this(AccountApi(), AccountSessionStore(context))
    private var session = runCatching { store.read() }.getOrNull()
    private val mutable = MutableStateFlow(AccountState(user = session?.user)); val state = mutable.asStateFlow()
    private val mutex = Mutex()
    fun notice(text: String) { mutable.value = mutable.value.copy(notice = text) }
    fun busy(value: Boolean) { mutable.value = mutable.value.copy(busy = value) }
    private fun save(value: AccountSession?) { store.write(value); session = value; mutable.value = mutable.value.copy(user = value?.user) }
    private suspend fun token(): String = mutex.withLock {
        var value = session ?: error("Sign in from Profile to continue.")
        if (value.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
            val result = api.request("/api/account/refresh", "POST", JSONObject().put("refreshToken", value.refreshToken)); value = AccountSessionCodec.decode(result); save(value)
        }
        value.accessToken
    }
    suspend fun load() {
        val config = api.request("/api/account/session")
        mutable.value = mutable.value.copy(configured = config.optBoolean("configured"))
        if (!config.optBoolean("configured")) { notice("Supabase setup is required. Local data is unchanged."); return }
        if (session == null) return
        try {
            val result = api.request("/api/account/session", token = token())
            val user = result.optJSONObject("user")?.let(AccountSessionCodec::user)
            if (user != null) save(session!!.copy(user = user))
            else { save(null); notice("Session expired. Sign in again.") }
        } catch (failure: AccountException) {
            if (failure.status == 401 || failure.status == 403) { save(null); notice("Session expired. Sign in again.") } else throw failure
        }
    }
    suspend fun signIn(email: String, password: String, signup: Boolean) {
        val result = api.request(if (signup) "/api/account/signup" else "/api/account/signin", "POST", JSONObject().put("email", email.trim()).put("password", password))
        if (result.has("session")) { save(AccountSessionCodec.decode(result)); mutable.value = mutable.value.copy(configured = true); notice("Signed in. Local data was not uploaded.") }
        else notice("Check your email, confirm your account, then sign in.")
    }
    suspend fun profile(name: String) {
        val result = api.request("/api/account/profile", "PATCH", JSONObject().put("displayName", name.trim()), token()); val user = AccountSessionCodec.user(result.getJSONObject("user")); save(session!!.copy(user = user)); notice("Profile updated.")
    }
    suspend fun logout() {
        var revoked = false
        try { revoked = api.request("/api/account/logout", "POST", token = token()).optBoolean("revoked") } catch (_: Exception) { }
        save(null); notice(if (revoked) "Signed out on this device. Local data stays saved." else "Signed out locally. Server revocation could not be confirmed.")
    }
    suspend fun cloud(): CloudReview {
        val result = api.request("/api/cloud", token = token()); val revision = result.getLong("revision"); require(revision >= 0)
        return CloudReview(revision, CloudSnapshotCodec.decode(result.getJSONObject("snapshot")))
    }
    suspend fun upload(review: CloudReview, clear: Boolean = false): Long {
        val body = JSONObject().put("expectedRevision", review.revision); if (!clear) body.put("snapshot", CloudSnapshotCodec.encode(review.snapshot))
        return api.request("/api/cloud", if (clear) "DELETE" else "PUT", body, token()).getLong("revision")
    }
}

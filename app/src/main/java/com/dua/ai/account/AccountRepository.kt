package com.dua.ai.account

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class AccountState(val configured: Boolean = false, val user: AccountUser? = null, val busy: Boolean = false, val notice: String = "", val googleConfigured: Boolean = false, val deletionConfigured: Boolean = false, val recoveryVerified: Boolean = false)
class AccountRepository(private val api: AccountApi, private val store: SessionPersistence) {
    constructor(context: Context) : this(AccountApi(), AccountSessionStore(context))
    private var session = runCatching { store.read() }.getOrNull()
    private val mutable = MutableStateFlow(AccountState(user = session?.user)); val state = mutable.asStateFlow()
    private val mutex = Mutex()
    fun notice(text: String) { mutable.value = mutable.value.copy(notice = text) }
    fun busy(value: Boolean) { mutable.value = mutable.value.copy(busy = value) }
    private fun save(value: AccountSession?) {
        val sameAccount = value != null && value.user.id == session?.user?.id
        store.write(value); session = value
        mutable.value = mutable.value.copy(user = value?.user, recoveryVerified = sameAccount && mutable.value.recoveryVerified)
    }
    private suspend fun token(): String = mutex.withLock {
        var value = session ?: error("Sign in from Profile to continue.")
        if (value.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
            val owner = value.user.id
            val result = api.request("/api/account/refresh", "POST", JSONObject().put("refreshToken", value.refreshToken))
            value = AccountSessionCodec.decode(result)
            check(session?.user?.id == owner && value.user.id == owner) { "Account changed. Retry from the current account." }
            save(value)
        }
        value.accessToken
    }
    private suspend fun refresh(expectedId: String): String = mutex.withLock {
        val value = session ?: error("Sign in from Profile to continue.")
        check(value.user.id == expectedId) { "Account changed. Retry from the current account." }
        val result = api.request("/api/account/refresh", "POST", JSONObject().put("refreshToken", value.refreshToken))
        val next = AccountSessionCodec.decode(result)
        check(session?.user?.id == expectedId && next.user.id == expectedId) { "Account changed. Retry from the current account." }
        save(next)
        next.accessToken
    }
    /** Optional authentication for chat/search; a configured owner policy is enforced by the server. */
    suspend fun <T> withToken(action: suspend (String?) -> T): T {
        val owner = session?.user?.id ?: return action(null)
        val first = token()
        check(session?.user?.id == owner) { "Account changed. Retry." }
        val result = try { action(first) } catch (failure: Exception) {
            val unauthorized = failure is AccountException && failure.status == 401 || failure is com.dua.ai.network.DuaApiException && failure.status == 401
            if (!unauthorized) throw failure
            action(refresh(owner))
        }
        check(session?.user?.id == owner) { "Account changed. Retry from the current account." }
        return result
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = false): JSONObject {
        if (authenticated) check(session != null) { "Sign in from Profile to continue." }
        return withToken { access -> api.request(path, method, body, access) }
    }
    suspend fun resetPassword(email: String) {
        api.request("/api/account/reset-password", "POST", JSONObject().put("email", email.trim()))
        notice("If the account exists, a recovery email was requested. Check your inbox and spam folder.")
    }
    suspend fun verifyRecovery(email: String, code: String) {
        val result = api.request("/api/account/verify-recovery", "POST", JSONObject().put("email", email.trim()).put("token", code.trim()))
        check(result.optBoolean("recoveryVerified")) { "Recovery code could not be verified." }
        save(AccountSessionCodec.decode(result)); mutable.value = mutable.value.copy(recoveryVerified = true); notice("Recovery verified. Choose a new password.")
    }
    suspend fun changePassword(password: String, currentPassword: String = "") {
        val body = JSONObject().put("password", password)
        if (currentPassword.isNotBlank()) body.put("currentPassword", currentPassword)
        request("/api/account/password", "PUT", body, authenticated = true)
        mutable.value = mutable.value.copy(recoveryVerified = false); notice("Password updated.")
    }
    suspend fun deleteAccount(confirmation: String, expectedAccountId: String? = null) {
        require(confirmation == "DELETE MY ACCOUNT") { "Type DELETE MY ACCOUNT to confirm." }
        val owner = expectedAccountId ?: session?.user?.id ?: error("Sign in from Profile to continue.")
        check(session?.user?.id == owner) { "Account changed. Review account deletion again." }
        request("/api/account/delete", "DELETE", JSONObject().put("confirmation", confirmation).put("expectedAccountId", owner), authenticated = true)
        save(null); notice("Account and its cloud data deleted. Local chats and memories stay on this device.")
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
    suspend fun loadFeatures() {
        try {
            val features = api.request("/api/account/features")
            mutable.value = mutable.value.copy(googleConfigured = features.optBoolean("googleLogin"), deletionConfigured = features.optBoolean("accountDeletion"))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = mutable.value.copy(googleConfigured = false, deletionConfigured = false) }
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
        val owner = session?.user?.id ?: error("Sign in from Profile to continue.")
        val result = request("/api/cloud", authenticated = true); val revision = result.getLong("revision"); require(revision >= 0)
        check(session?.user?.id == owner && (!result.has("ownerId") || result.optString("ownerId") == owner)) { "Cloud account changed. Review again." }
        return CloudReview(revision, CloudSnapshotCodec.decode(result.getJSONObject("snapshot")), owner)
    }
    suspend fun upload(review: CloudReview, clear: Boolean = false, expectedAccountId: String? = null): Long {
        val owner = expectedAccountId ?: review.ownerId ?: session?.user?.id ?: error("Sign in from Profile to continue.")
        check(session?.user?.id == owner) { "Cloud account changed. Review again." }
        val body = JSONObject().put("expectedRevision", review.revision).put("expectedAccountId", owner); if (!clear) body.put("snapshot", CloudSnapshotCodec.encode(review.snapshot))
        return request("/api/cloud", if (clear) "DELETE" else "PUT", body, authenticated = true).getLong("revision")
    }
}

package com.dua.ai.account

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class AccountState(val configured: Boolean = false, val user: AccountUser? = null, val busy: Boolean = false, val notice: String = "", val deletionConfigured: Boolean = false)
class AccountRepository(private val api: AccountApi, private val store: SessionPersistence) {
    constructor(context: Context) : this(AccountApi(), AccountSessionStore(context))
    private var session = runCatching { store.read() }.getOrNull()
    private val mutable = MutableStateFlow(AccountState(user = session?.user)); val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private val sessionLock = Any()
    private var authGeneration = 0L
    private var sessionRevision = 0L
    private data class Snapshot(val value: AccountSession?, val generation: Long, val revision: Long)
    private fun snapshot() = synchronized(sessionLock) { Snapshot(session, authGeneration, sessionRevision) }
    private fun matches(value: Snapshot) = authGeneration == value.generation && session?.user?.id == value.value?.user?.id
    private fun checkIdentity(value: Snapshot) = synchronized(sessionLock) {
        check(matches(value)) { "Account changed. Retry from the current account." }
    }
    fun notice(text: String) { mutable.update { it.copy(notice = text) } }
    fun busy(value: Boolean) { mutable.update { it.copy(busy = value) } }
    /** Call only while holding sessionLock. Persistence and the generation guard commit together. */
    private fun writeSession(value: AccountSession?, authenticationChange: Boolean = false) {
        store.write(value); session = value; sessionRevision++
        if (authenticationChange) authGeneration++
        mutable.update { it.copy(user = value?.user) }
    }
    private fun beginAuthentication() = synchronized(sessionLock) { ++authGeneration }
    private fun installAuthentication(ticket: Long, value: AccountSession) = synchronized(sessionLock) {
        check(authGeneration == ticket) { "Account changed. Retry from the current account." }
        writeSession(value, authenticationChange = true)
        mutable.update { it.copy(configured = true) }
        Snapshot(session, authGeneration, sessionRevision)
    }
    private fun noticeIfCurrent(value: Snapshot, text: String) = synchronized(sessionLock) { if (matches(value)) notice(text) }
    private fun credentialsMatch(value: Snapshot): Boolean = matches(value) && session?.accessToken == value.value?.accessToken && session?.refreshToken == value.value?.refreshToken
    private fun installRefresh(value: Snapshot, next: AccountSession): String = synchronized(sessionLock) {
        check(credentialsMatch(value) && next.user.id == value.value?.user?.id) { "Account changed. Retry from the current account." }
        // Preserve any profile update that completed while the credential refresh was in flight.
        writeSession(next.copy(user = session!!.user))
        next.accessToken
    }
    private suspend fun token(expected: Snapshot): String = mutex.withLock {
        val current = snapshot(); checkIdentity(expected)
        val value = current.value ?: error("Sign in from Profile to continue.")
        if (value.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
            val result = api.request("/api/account/refresh", "POST", JSONObject().put("refreshToken", value.refreshToken))
            installRefresh(current, AccountSessionCodec.decode(result))
        } else value.accessToken
    }
    private suspend fun refresh(expected: Snapshot, rejectedToken: String): String = mutex.withLock {
        val current = snapshot(); checkIdentity(expected)
        val value = current.value ?: error("Sign in from Profile to continue.")
        // Another request may already have refreshed the rejected access token.
        if (value.accessToken != rejectedToken) return@withLock value.accessToken
        val result = api.request("/api/account/refresh", "POST", JSONObject().put("refreshToken", value.refreshToken))
        installRefresh(current, AccountSessionCodec.decode(result))
    }
    private suspend fun <T> withSnapshot(expected: Snapshot, action: suspend (String?) -> T): T {
        checkIdentity(expected)
        if (expected.value == null) {
            val result = action(null); checkIdentity(expected); return result
        }
        val first = token(expected); checkIdentity(expected)
        val result = try { action(first) } catch (failure: Exception) {
            val unauthorized = failure is AccountException && failure.status == 401 || failure is com.dua.ai.network.DuaApiException && failure.status == 401
            if (!unauthorized) throw failure
            action(refresh(expected, first))
        }
        checkIdentity(expected)
        return result
    }
    /** Optional authentication for chat/search; a configured owner policy is enforced by the server. */
    suspend fun <T> withToken(action: suspend (String?) -> T): T = withSnapshot(snapshot(), action)
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = false): JSONObject {
        val expected = snapshot()
        if (authenticated) check(expected.value != null) { "Sign in from Profile to continue." }
        return withSnapshot(expected) { access -> api.request(path, method, body, access) }
    }
    suspend fun deleteAccount(confirmation: String, expectedAccountId: String? = null) {
        require(confirmation == "DELETE MY ACCOUNT") { "Type DELETE MY ACCOUNT to confirm." }
        val expected = snapshot()
        val owner = expectedAccountId ?: expected.value?.user?.id ?: error("Sign in from Profile to continue.")
        check(expected.value?.user?.id == owner) { "Account changed. Review account deletion again." }
        withSnapshot(expected) { api.request("/api/account/delete", "DELETE", JSONObject().put("confirmation", confirmation).put("expectedAccountId", owner), it) }
        synchronized(sessionLock) {
            check(matches(expected)) { "Account changed. Review account deletion again." }
            writeSession(null, authenticationChange = true)
            notice("Account and its cloud data deleted. Local chats and memories stay on this device.")
        }
    }
    private fun applyLoadedUser(expected: Snapshot, user: AccountUser?): Boolean = synchronized(sessionLock) {
        if (!matches(expected) || sessionRevision != expected.revision) return@synchronized false
        check(user == null || user.id == expected.value?.user?.id) { "Account response changed. Sign in again." }
        if (user == null) {
            writeSession(null, authenticationChange = true); notice("Session expired. Sign in again.")
        } else writeSession(session!!.copy(user = user))
        true
    }
    suspend fun load() {
        val initial = snapshot()
        val config = api.request("/api/account/session")
        synchronized(sessionLock) {
            if (!matches(initial)) return
            mutable.update { it.copy(configured = config.optBoolean("configured")) }
            if (!config.optBoolean("configured")) { notice("Supabase setup is required. Local data is unchanged."); return }
        }
        if (initial.value == null) return
        var authenticated = initial
        try {
            val access = token(initial)
            authenticated = snapshot(); checkIdentity(initial)
            if (authenticated.value?.accessToken != access) return
            val result = api.request("/api/account/session", token = access)
            applyLoadedUser(authenticated, result.optJSONObject("user")?.let(AccountSessionCodec::user))
        } catch (failure: AccountException) {
            if (failure.status == 401 || failure.status == 403) applyLoadedUser(authenticated, null) else throw failure
        }
    }
    suspend fun loadFeatures() {
        try {
            val features = api.request("/api/account/features")
            mutable.update { it.copy(deletionConfigured = features.optBoolean("accountDeletion")) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(deletionConfigured = false) } }
    }
    suspend fun signInWithGoogle(idToken: String, nonce: String) {
        val ticket = beginAuthentication()
        val result = api.request("/api/account/google-token", "POST", JSONObject().put("idToken", idToken).put("nonce", nonce))
        val installed = installAuthentication(ticket, AccountSessionCodec.decode(result))
        noticeIfCurrent(installed, "Signed in with Google. Local data was not uploaded.")
    }
    suspend fun profile(name: String) {
        val expected = snapshot(); check(expected.value != null) { "Sign in from Profile to continue." }
        val result = withSnapshot(expected) { api.request("/api/account/profile", "PATCH", JSONObject().put("displayName", name.trim()), it) }
        val user = AccountSessionCodec.user(result.getJSONObject("user"))
        synchronized(sessionLock) {
            check(matches(expected) && user.id == expected.value.user.id) { "Account changed. Retry from the current account." }
            writeSession(session!!.copy(user = user)); notice("Profile updated.")
        }
    }
    suspend fun logout() {
        // Forget locally before waiting on the network, invalidating pending sign-in responses too.
        val (prior, signedOut) = synchronized(sessionLock) {
            val prior = session
            writeSession(null, authenticationChange = true)
            prior to Snapshot(session, authGeneration, sessionRevision)
        }
        var revoked = false
        try { if (prior != null) revoked = api.request("/api/account/logout", "POST", token = prior.accessToken).optBoolean("revoked") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        noticeIfCurrent(signedOut, if (revoked) "Signed out on this device. Local data stays saved." else "Signed out locally. Server revocation could not be confirmed.")
    }
    suspend fun cloud(): CloudReview {
        val expected = snapshot(); val owner = expected.value?.user?.id ?: error("Sign in from Profile to continue.")
        val result = withSnapshot(expected) { api.request("/api/cloud", token = it) }; val revision = result.getLong("revision"); require(revision >= 0)
        checkIdentity(expected)
        check(!result.has("ownerId") || result.optString("ownerId") == owner) { "Cloud account changed. Review again." }
        return CloudReview(revision, CloudSnapshotCodec.decode(result.getJSONObject("snapshot")), owner)
    }
    suspend fun upload(review: CloudReview, clear: Boolean = false, expectedAccountId: String? = null): Long {
        val expected = snapshot()
        val owner = expectedAccountId ?: review.ownerId ?: expected.value?.user?.id ?: error("Sign in from Profile to continue.")
        check(expected.value?.user?.id == owner) { "Cloud account changed. Review again." }
        val body = JSONObject().put("expectedRevision", review.revision).put("expectedAccountId", owner); if (!clear) body.put("snapshot", CloudSnapshotCodec.encode(review.snapshot))
        return withSnapshot(expected) { api.request("/api/cloud", if (clear) "DELETE" else "PUT", body, it) }.getLong("revision")
    }
}

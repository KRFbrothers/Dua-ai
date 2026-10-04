package com.dua.ai.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccountRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: AccountApi
    private val userA = AccountUser("11111111-1111-4111-8111-111111111111", "a@example.com", "A")
    private val userB = AccountUser("22222222-2222-4222-8222-222222222222", "b@example.com", "B")
    private fun session(user: AccountUser, access: String = "access-${user.displayName}", expiresAt: Long = System.currentTimeMillis() / 1000 + 3600) = AccountSession(user, access, "refresh-$access", expiresAt)
    private fun json(value: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    private fun account(user: AccountUser?) = JSONObject().put("configured", true).put("user", user?.let { JSONObject().put("id", it.id).put("email", it.email).put("displayName", it.displayName) } ?: JSONObject.NULL)
    private class MemoryStore(var value: AccountSession?) : SessionPersistence {
        override fun read() = value
        override fun write(value: AccountSession?) { this.value = value }
    }
    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server = MockWebServer(); server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false); server.start()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client = OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager).followRedirects(false).build()
        api = AccountApi(server.url("/").toString(), client)
    }
    @After fun teardown() { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); server.shutdown(); client.dispatcher.executorService.shutdown() }
    private fun delayedDispatcher(path: String, response: MockResponse, arrived: CompletableDeferred<Unit>, release: CountDownLatch, signIn: AccountSession = session(userB), recovery: Boolean = false) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == path && (path != "/api/account/session" || request.getHeader("Authorization") != null) -> {
                    arrived.complete(Unit)
                    if (!release.await(4, TimeUnit.SECONDS)) json(JSONObject().put("error", "Test release timed out")).setResponseCode(500)
                    else response
                }
                request.path == "/api/account/signin" -> json(AccountSessionCodec.encode(signIn))
                request.path == "/api/account/verify-recovery" -> json(AccountSessionCodec.encode(signIn).put("recoveryVerified", recovery))
                request.path == "/api/account/logout" -> json(JSONObject().put("revoked", true))
                request.path == "/api/account/session" -> json(account(null))
                else -> json(JSONObject().put("error", "Unexpected test path")).setResponseCode(404)
            }
        }
    }
    @Test fun lateSessionLoadCannotMixNewOwnerTokensWithOldOwnerLabel() = runBlocking {
        val initial = session(userA); val signedIn = session(userB); val store = MemoryStore(initial); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/session", json(account(userA)), arrived, release, signedIn)
        val oldLoad = async { repository.load() }
        try { withTimeout(3000) { arrived.await() }; repository.signIn(userB.email, "password", false) } finally { release.countDown() }
        withTimeout(3000) { oldLoad.await() }
        assertEquals(signedIn, store.value); assertEquals(userB, repository.state.value.user)
    }
    @Test fun lateUnauthorizedLoadDoesNotClearReplacementSession() = runBlocking {
        val replacement = session(userB); val store = MemoryStore(session(userA)); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/session", json(JSONObject().put("error", "Expired")).setResponseCode(401), arrived, release, replacement)
        val oldLoad = async { repository.load() }
        try { withTimeout(3000) { arrived.await() }; repository.signIn(userB.email, "password", false) } finally { release.countDown() }
        withTimeout(3000) { oldLoad.await() }
        assertEquals(replacement, store.value); assertEquals(userB, repository.state.value.user)
    }
    @Test fun lateProfileResponseCannotSaveOverRecoveryForAnotherOwner() = runBlocking {
        val replacement = session(userB); val store = MemoryStore(session(userA)); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/profile", json(account(userA.copy(displayName = "Updated A"))), arrived, release, replacement, recovery = true)
        val oldProfile = async { runCatching { repository.profile("Updated A") } }
        try { withTimeout(3000) { arrived.await() }; repository.verifyRecovery(userB.email, "123456") } finally { release.countDown() }
        assertTrue(withTimeout(3000) { oldProfile.await() }.exceptionOrNull() is IllegalStateException)
        assertEquals(replacement, store.value); assertEquals(userB, repository.state.value.user); assertTrue(repository.state.value.recoveryVerified)
    }
    @Test fun logoutClearsImmediatelyAndLateLoadCannotResurrectSession() = runBlocking {
        val store = MemoryStore(session(userA)); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/session", json(account(userA)), arrived, release)
        val oldLoad = async { repository.load() }
        try { withTimeout(3000) { arrived.await() }; repository.logout(); assertNull(store.value); assertNull(repository.state.value.user) } finally { release.countDown() }
        withTimeout(3000) { oldLoad.await() }; assertNull(store.value); assertNull(repository.state.value.user)
    }
    @Test fun sameOwnerReloginInvalidatesAnOlderPrivateResult() = runBlocking {
        val store = MemoryStore(session(userA)); val repository = AccountRepository(api, store); val replacement = session(userA, "replacement-access")
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/private", json(JSONObject().put("secret", "old login result")), arrived, release, replacement)
        val oldRequest = async { runCatching { repository.request("/api/private", authenticated = true) } }
        try { withTimeout(3000) { arrived.await() }; repository.signIn(userA.email, "password", false) } finally { release.countDown() }
        assertTrue(withTimeout(3000) { oldRequest.await() }.exceptionOrNull() is IllegalStateException)
        assertEquals(replacement, store.value)
    }
    @Test fun pendingLoginCannotUndoLogout() = runBlocking {
        val oldStore = MemoryStore(session(userA)); val repository = AccountRepository(api, oldStore)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/signin", json(AccountSessionCodec.encode(session(userB))), arrived, release)
        val pending = async { runCatching { repository.signIn(userB.email, "password", false) } }
        try { withTimeout(3000) { arrived.await() }; repository.logout() } finally { release.countDown() }
        assertTrue(withTimeout(3000) { pending.await() }.exceptionOrNull() is IllegalStateException)
        assertNull(oldStore.value); assertNull(repository.state.value.user)
    }
    @Test fun anEarlierSignInCannotReplaceALaterSuccessfulSignIn() = runBlocking {
        val replacement = session(userB); val store = MemoryStore(null); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (JSONObject(request.body.readUtf8()).getString("email") == userA.email) {
                    arrived.complete(Unit)
                    if (!release.await(4, TimeUnit.SECONDS)) return json(JSONObject().put("error", "Test release timed out")).setResponseCode(500)
                    return json(AccountSessionCodec.encode(session(userA)))
                }
                return json(AccountSessionCodec.encode(replacement))
            }
        }
        val earlier = async { runCatching { repository.signIn(userA.email, "password", false) } }
        try { withTimeout(3000) { arrived.await() }; repository.signIn(userB.email, "password", false) } finally { release.countDown() }
        assertTrue(withTimeout(3000) { earlier.await() }.exceptionOrNull() is IllegalStateException)
        assertEquals(replacement, store.value); assertEquals(userB, repository.state.value.user)
    }
    @Test fun pendingRefreshCannotOverwriteSameOwnerNewLoginCredentials() = runBlocking {
        val old = session(userA, "old-access", expiresAt = 1); val replacement = session(userA, "replacement-access")
        val store = MemoryStore(old); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/refresh", json(AccountSessionCodec.encode(session(userA, "late-refreshed-access"))), arrived, release, replacement)
        val pending = async { runCatching { repository.withToken { "private result" } } }
        try { withTimeout(3000) { arrived.await() }; repository.signIn(userA.email, "password", false) } finally { release.countDown() }
        assertTrue(withTimeout(3000) { pending.await() }.exceptionOrNull() is IllegalStateException)
        assertEquals(replacement, store.value)
    }
    @Test fun aMismatchedSessionResponseDoesNotRelabelExistingCredentials() = runBlocking {
        val original = session(userA); val store = MemoryStore(original); val repository = AccountRepository(api, store)
        server.enqueue(json(account(null))); server.enqueue(json(account(userB)))
        assertTrue(runCatching { repository.load() }.exceptionOrNull() is IllegalStateException)
        assertEquals(original, store.value); assertEquals(userA, repository.state.value.user)
    }
}

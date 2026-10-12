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
    @Test fun emailSignInUsesNativeTlsApiAndStoresOnlyTheReturnedSession() = runBlocking {
        val store = MemoryStore(null); val repository = AccountRepository(api, store)
        server.enqueue(json(AccountSessionCodec.encode(session(userA))))
        assertTrue(repository.signInWithEmail(" ${userA.email.uppercase()} ", " password spaces "))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/account/email-login", request.path); assertEquals("1", request.getHeader("x-dua-native"))
        assertEquals(" password spaces ", JSONObject(request.body.readUtf8()).getString("password"))
        assertEquals(userA, store.value!!.user)
        assertFalse(AccountSessionCodec.encode(store.value!!).toString().contains("password spaces"))
    }
    @Test fun signupConfirmationNeverInstallsASessionAndPasswordSetupRequiresTheCurrentAccount() = runBlocking {
        val store = MemoryStore(null); val repository = AccountRepository(api, store)
        server.enqueue(json(JSONObject().put("confirmationRequired", true).put("message", "Check your email.")))
        assertFalse(repository.signInWithEmail(userA.email, "new-password", signup = true))
        assertNull(store.value); assertNull(repository.state.value.user)
        assertEquals("Check your email.", repository.state.value.notice)
        assertEquals("/api/account/email-signup", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        assertTrue(runCatching { repository.setEmailPassword("new-password", userA.id) }.isFailure)
        assertEquals(0, server.requestCount - 1)
    }
    @Test fun invalidCredentialsAndUnexpectedEmailCannotInstallAuthentication() = runBlocking {
        val store = MemoryStore(null); val repository = AccountRepository(api, store)
        assertTrue(runCatching { repository.signInWithEmail("invalid", "password") }.isFailure)
        assertTrue(runCatching { repository.signInWithEmail(userA.email, "short", signup = true) }.isFailure)
        assertEquals(0, server.requestCount)
        server.enqueue(json(AccountSessionCodec.encode(session(userB))))
        assertTrue(runCatching { repository.signInWithEmail(userA.email, "password") }.isFailure)
        assertNull(store.value)
    }
    @Test fun passwordUpdateSendsOwnerBindingWithoutReplacingSessionOrSavingThePassword() = runBlocking {
        val original = session(userA)
        val store = MemoryStore(original); val repository = AccountRepository(api, store)
        // Exercise a response crossing a second boundary without recomputing the expected expiry.
        server.enqueue(json(JSONObject().put("ownerId", userA.id).put("passwordUpdated", true)).setHeadersDelay(1100, TimeUnit.MILLISECONDS))
        repository.setEmailPassword("new-password", userA.id)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("PUT", request.method); assertEquals("/api/account/password", request.path)
        assertEquals(userA.id, JSONObject(request.body.readUtf8()).getString("expectedAccountId"))
        assertEquals(original, store.value)
        server.enqueue(json(JSONObject().put("ownerId", userB.id).put("passwordUpdated", true)))
        assertTrue(runCatching { repository.setEmailPassword("new-password", userA.id) }.isFailure)
    }
    @Test fun aLateEmailSignInCannotUndoLogoutOrCancellation() = runBlocking {
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val store = MemoryStore(null); val repository = AccountRepository(api, store)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrived.complete(Unit); release.await(4, TimeUnit.SECONDS)
                return json(AccountSessionCodec.encode(session(userA)))
            }
        }
        val pending = async { runCatching { repository.signInWithEmail(userA.email, "password") } }
        withTimeout(3000) { arrived.await() }; repository.logout(); repository.cancelAuthentication(); release.countDown()
        assertTrue(pending.await().isFailure); assertNull(store.value); assertNull(repository.state.value.user)
    }
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: AccountApi
    private val userA = AccountUser("11111111-1111-4111-8111-111111111111", "a@example.com", "A")
    private val userB = AccountUser("22222222-2222-4222-8222-222222222222", "b@example.com", "B")
    private fun session(user: AccountUser, access: String = "access-${user.displayName}", expiresAt: Long = System.currentTimeMillis() / 1000 + 3600) = AccountSession(user, access, "refresh-$access", expiresAt)
    private fun json(value: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    private fun account(user: AccountUser?) = JSONObject().put("configured", true).put("user", user?.let { JSONObject().put("id", it.id).put("email", it.email).put("displayName", it.displayName) } ?: JSONObject.NULL)
    /** Opaque stand-ins for a Google ID token and the raw nonce; the mock backend only routes on them. */
    private fun idToken(user: AccountUser) = "header.${user.displayName}.signature"
    private val nonce = "raw-nonce-0123456789abcdef"
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
    private fun delayedDispatcher(path: String, response: MockResponse, arrived: CompletableDeferred<Unit>, release: CountDownLatch, signIn: AccountSession = session(userB)) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == path && (path != "/api/account/session" || request.getHeader("Authorization") != null) -> {
                    arrived.complete(Unit)
                    if (!release.await(4, TimeUnit.SECONDS)) json(JSONObject().put("error", "Test release timed out")).setResponseCode(500)
                    else response
                }
                request.path == "/api/account/google-token" -> json(AccountSessionCodec.encode(signIn))
                request.path == "/api/account/logout" -> json(JSONObject().put("revoked", true))
                request.path == "/api/account/session" -> json(account(null))
                else -> json(JSONObject().put("error", "Unexpected test path")).setResponseCode(404)
            }
        }
    }
    private fun deletionResult(user: AccountUser = userA, chat: String = "target", revision: Long = 4) = JSONObject()
        .put("ownerId", user.id).put("chatId", chat).put("revision", revision).put("deleted", true)
        .put("deletedChatIds", org.json.JSONArray().put(chat))
    @Test fun chatDeletionUsesReviewedOwnerAndRevisionAndRequiresConfirmedMarker() = runBlocking {
        val repository = AccountRepository(api, MemoryStore(session(userA)))
        val review = CloudReview(3, CloudSnapshot(emptyList(), emptyList()), userA.id)
        server.enqueue(json(deletionResult()))
        val result = repository.deleteChat(review, "target")
        assertEquals(listOf("target"), result.deletedChatIds)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/cloud/chat", request.path); assertEquals("DELETE", request.method)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(3L, body.getLong("expectedRevision")); assertEquals(userA.id, body.getString("expectedAccountId"))
        assertEquals("DELETE CHAT", body.getString("confirmation"))
        server.enqueue(json(deletionResult().put("deletedChatIds", org.json.JSONArray())))
        assertTrue(runCatching { repository.deleteChat(review, "target") }.isFailure)
        server.enqueue(json(deletionResult(userB)))
        assertTrue(runCatching { repository.deleteChat(review, "target") }.isFailure)
        assertTrue(runCatching { repository.deleteChat(review.copy(ownerId = userB.id), "target") }.isFailure)
    }
    @Test fun aLateDeletionResponseCannotBeAppliedAfterAccountReplacement() = runBlocking {
        val repository = AccountRepository(api, MemoryStore(session(userA)))
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/cloud/chat", json(deletionResult()), arrived, release)
        val pending = async { runCatching { repository.deleteChat(CloudReview(3, CloudSnapshot(emptyList(), emptyList()), userA.id), "target") } }
        try { withTimeout(3000) { arrived.await() }; repository.signInWithGoogle(idToken(userB), nonce) } finally { release.countDown() }
        assertTrue(withTimeout(3000) { pending.await() }.isFailure)
        assertEquals(userB, repository.state.value.user)
    }
    @Test fun lateSessionLoadCannotMixNewOwnerTokensWithOldOwnerLabel() = runBlocking {
        val initial = session(userA); val signedIn = session(userB); val store = MemoryStore(initial); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/session", json(account(userA)), arrived, release, signedIn)
        val oldLoad = async { repository.load() }
        try { withTimeout(3000) { arrived.await() }; repository.signInWithGoogle(idToken(userB), nonce) } finally { release.countDown() }
        withTimeout(3000) { oldLoad.await() }
        assertEquals(signedIn, store.value); assertEquals(userB, repository.state.value.user)
    }
    @Test fun lateUnauthorizedLoadDoesNotClearReplacementSession() = runBlocking {
        val replacement = session(userB); val store = MemoryStore(session(userA)); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/session", json(JSONObject().put("error", "Expired")).setResponseCode(401), arrived, release, replacement)
        val oldLoad = async { repository.load() }
        try { withTimeout(3000) { arrived.await() }; repository.signInWithGoogle(idToken(userB), nonce) } finally { release.countDown() }
        withTimeout(3000) { oldLoad.await() }
        assertEquals(replacement, store.value); assertEquals(userB, repository.state.value.user)
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
        try { withTimeout(3000) { arrived.await() }; repository.signInWithGoogle(idToken(userA), nonce) } finally { release.countDown() }
        assertTrue(withTimeout(3000) { oldRequest.await() }.exceptionOrNull() is IllegalStateException)
        assertEquals(replacement, store.value)
    }
    @Test fun pendingLoginCannotUndoLogout() = runBlocking {
        val oldStore = MemoryStore(session(userA)); val repository = AccountRepository(api, oldStore)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/google-token", json(AccountSessionCodec.encode(session(userB))), arrived, release)
        val pending = async { runCatching { repository.signInWithGoogle(idToken(userB), nonce) } }
        try { withTimeout(3000) { arrived.await() }; repository.logout() } finally { release.countDown() }
        assertTrue(withTimeout(3000) { pending.await() }.exceptionOrNull() is IllegalStateException)
        assertNull(oldStore.value); assertNull(repository.state.value.user)
    }
    @Test fun anEarlierSignInCannotReplaceALaterSuccessfulSignIn() = runBlocking {
        val replacement = session(userB); val store = MemoryStore(null); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (JSONObject(request.body.readUtf8()).getString("idToken") == idToken(userA)) {
                    arrived.complete(Unit)
                    if (!release.await(4, TimeUnit.SECONDS)) return json(JSONObject().put("error", "Test release timed out")).setResponseCode(500)
                    return json(AccountSessionCodec.encode(session(userA)))
                }
                return json(AccountSessionCodec.encode(replacement))
            }
        }
        val earlier = async { runCatching { repository.signInWithGoogle(idToken(userA), nonce) } }
        try { withTimeout(3000) { arrived.await() }; repository.signInWithGoogle(idToken(userB), nonce) } finally { release.countDown() }
        assertTrue(withTimeout(3000) { earlier.await() }.exceptionOrNull() is IllegalStateException)
        assertEquals(replacement, store.value); assertEquals(userB, repository.state.value.user)
    }
    @Test fun pendingRefreshCannotOverwriteSameOwnerNewLoginCredentials() = runBlocking {
        val old = session(userA, "old-access", expiresAt = 1); val replacement = session(userA, "replacement-access")
        val store = MemoryStore(old); val repository = AccountRepository(api, store)
        val arrived = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        delayedDispatcher("/api/account/refresh", json(AccountSessionCodec.encode(session(userA, "late-refreshed-access"))), arrived, release, replacement)
        val pending = async { runCatching { repository.withToken { "private result" } } }
        try { withTimeout(3000) { arrived.await() }; repository.signInWithGoogle(idToken(userA), nonce) } finally { release.countDown() }
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

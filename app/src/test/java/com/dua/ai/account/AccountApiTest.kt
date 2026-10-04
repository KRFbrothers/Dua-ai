package com.dua.ai.account

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class AccountApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: AccountApi
    private val user = JSONObject().put("id", "11111111-1111-4111-8111-111111111111").put("email", "person@example.com").put("displayName", "Dua")
    private fun json(value: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server = MockWebServer(); server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false); server.start()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client = OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager).followRedirects(false).build()
        api = AccountApi(server.url("/").toString(), client)
    }
    @After fun teardown() { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); server.shutdown(); client.dispatcher.executorService.shutdown() }
    @Test fun nativeRequestContractUsesBearerAndNoEmbeddedProviderKey() = runBlocking {
        server.enqueue(json(JSONObject().put("configured", true).put("user", user)))
        api.request("/api/account/session", token = "fake-access")
        val request = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertEquals("1", request.getHeader("x-dua-native")); assertEquals("Bearer fake-access", request.getHeader("Authorization")); assertNull(request.getHeader("apikey")); assertEquals("/api/account/session", request.path)
    }
    private class MemoryStore(var value: AccountSession?) : SessionPersistence { override fun read() = value; override fun write(value: AccountSession?) { this.value = value } }
    @Test fun expiredSessionRefreshesBeforeCheckingCurrentUser() = runBlocking {
        val old = AccountSession(AccountSessionCodec.user(user), "old-access", "old-refresh", 1)
        val store = MemoryStore(old); val repository = AccountRepository(api, store)
        server.enqueue(json(JSONObject().put("configured", true).put("user", JSONObject.NULL)))
        server.enqueue(json(AccountSessionCodec.encode(old.copy(accessToken = "new-access", refreshToken = "new-refresh", expiresAt = System.currentTimeMillis() / 1000 + 3600))))
        server.enqueue(json(JSONObject().put("configured", true).put("user", user)))
        repository.load()
        val requests = (0..2).map { server.takeRequest(3, TimeUnit.SECONDS)!! }
        assertEquals("/api/account/refresh", requests[1].path); assertEquals("old-refresh", JSONObject(requests[1].body.readUtf8()).getString("refreshToken")); assertEquals("Bearer new-access", requests[2].getHeader("Authorization")); assertEquals("new-refresh", store.value!!.refreshToken)
    }
    @Test fun logoutClearsSavedSessionEvenIfServerCannotConfirmRevocation() = runBlocking {
        val store = MemoryStore(AccountSession(AccountSessionCodec.user(user), "fake-access", "fake-refresh", System.currentTimeMillis() / 1000 + 3600)); val repository = AccountRepository(api, store)
        server.enqueue(json(JSONObject().put("error", "offline")).setResponseCode(502))
        repository.logout(); assertNull(store.value); assertNull(repository.state.value.user); assertTrue(repository.state.value.notice.contains("could not be confirmed"))
    }
    @Test fun cancellationStopsPendingAccountSocket() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { api.request("/api/account/session") }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) }); withTimeout(3000) { job.cancelAndJoin() }; assertTrue(job.isCancelled)
    }
}

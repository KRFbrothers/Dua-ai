package com.dua.ai.voice

import com.dua.ai.account.AccountException
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class GeneratedAudioApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: GeneratedAudioApi
    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server = MockWebServer(); server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false); server.start()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client = OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .followRedirects(false).followSslRedirects(false).readTimeout(3, TimeUnit.SECONDS).build()
        api = GeneratedAudioApi(server.url("/").toString(), client)
    }
    @After fun teardown() { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); server.shutdown(); client.dispatcher.executorService.shutdown() }
    private fun audio(bytes: ByteArray = byteArrayOf(1,2,3)) = MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(Buffer().write(bytes))
    @Test fun authenticatedFixedEndpointSendsOnlySelectedReplyAndReturnsMp3() = runBlocking {
        server.enqueue(audio()); assertArrayEquals(byteArrayOf(1,2,3), api.generate(" Selected ", "valid.token"))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/voice/audio", request.path); assertEquals("POST", request.method)
        assertEquals("Bearer valid.token", request.getHeader("Authorization")); assertEquals("1", request.getHeader("x-dua-native"))
        val json = JSONObject(request.body.readUtf8()); assertEquals(setOf("text"), json.keys().asSequence().toSet()); assertEquals("Selected", json.getString("text"))
    }
    @Test fun invalidTextAndTokenAreRejectedBeforeTransport() = runBlocking {
        for ((text, token) in listOf(" " to "valid", "x".repeat(2001) to "valid", "Reply" to "")) {
            assertNotNull(runCatching { api.generate(text, token) }.exceptionOrNull())
        }
        assertEquals(0, server.requestCount)
        assertNotNull(runCatching { GeneratedAudioApi("http://localhost", client).generate("Reply", "valid") }.exceptionOrNull())
    }
    @Test fun providerErrorContentNeverAppearsInUserFacingFailureAnd401RemainsRefreshable() = runBlocking {
        for (status in listOf(401,403,429,503,502)) {
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody("{\"error\":\"private-provider-key\"}"))
            val failure = runCatching { api.generate("Reply", "valid") }.exceptionOrNull() as AccountException
            assertEquals(status, failure.status); assertFalse(failure.message.orEmpty().contains("private-provider-key"))
        }
    }
    @Test fun redirectsAreNotFollowedAndUnexpectedOrEmptyBodiesAreRejected() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/leak")))
        assertNotNull(runCatching { api.generate("Reply", "valid") }.exceptionOrNull()); assertEquals(1, server.requestCount)
        server.enqueue(audio().setHeader("Content-Type", "text/html")); assertNotNull(runCatching { api.generate("Reply", "valid") }.exceptionOrNull())
        server.enqueue(audio(ByteArray(0))); assertNotNull(runCatching { api.generate("Reply", "valid") }.exceptionOrNull())
    }
    @Test fun declaredAndChunkedOversizedAudioAreRejected() = runBlocking {
        server.enqueue(audio().setHeader("Content-Length", GeneratedAudioApi.MAX_BYTES + 1))
        assertNotNull(runCatching { api.generate("Reply", "valid") }.exceptionOrNull())
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setChunkedBody(Buffer().write(ByteArray(GeneratedAudioApi.MAX_BYTES + 1)), 8192))
        assertNotNull(runCatching { api.generate("Reply", "valid") }.exceptionOrNull())
    }
    @Test fun cancellationStopsPendingTransportWithoutReceivingOrPlayingAudio() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { api.generate("Reply", "valid") }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); pending.cancelAndJoin(); assertTrue(pending.isCancelled)
    }
}

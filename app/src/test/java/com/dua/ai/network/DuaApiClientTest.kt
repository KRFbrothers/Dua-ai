package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import com.dua.ai.attachments.ChatAttachment
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DuaApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: OkHttpClient
    private lateinit var api: DuaApiClient
    private val history = listOf(ChatMessage(text = "Hi", isUser = true))

    @Before fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        transport = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .build()
        api = DuaApiClient(server.url("/").toString(), transport)
    }

    @After fun tearDown() {
        transport.dispatcher.cancelAll()
        transport.connectionPool.evictAll()
        server.shutdown()
        transport.dispatcher.executorService.shutdown()
    }

    private fun response(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json").setBody(body)

    private suspend fun awaitTransportStopped() = withTimeout(3000) {
        while (transport.dispatcher.runningCallsCount() != 0) delay(10)
    }

    @Test fun stopCancelsWaitingRequestAndNextReplyWorks() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val reply = async { api.reply("Dua", history) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
        withTimeout(3000) { reply.cancelAndJoin() }
        awaitTransportStopped()
        assertTrue(reply.isCancelled)
        server.enqueue(response("{\"text\":\"New reply\"}"))
        assertEquals("New reply", withTimeout(3000) { api.reply("Dua", history) })
    }

    @Test fun stopCancelsBodyRead() = runBlocking {
        val headersReceived = CountDownLatch(1)
        transport = transport.newBuilder().eventListener(object : EventListener() {
            override fun responseHeadersEnd(call: Call, response: Response) {
                headersReceived.countDown()
            }
        }).build()
        api = DuaApiClient(server.url("/").toString(), transport)
        server.enqueue(response("{\"text\":\"" + "x".repeat(10000) + "\"}")
            .throttleBody(100, 200, TimeUnit.MILLISECONDS))
        val reply = async { api.reply("Gemini", history) }
        assertTrue(withContext(Dispatchers.IO) { headersReceived.await(3, TimeUnit.SECONDS) })
        // Headers have arrived; the body takes several seconds to finish.
        delay(100)
        withTimeout(3000) { reply.cancelAndJoin() }
        awaitTransportStopped()
        assertTrue(reply.isCancelled)
    }

    @Test fun requestContractAndUnicodeReplyArePreserved() = runBlocking {
        server.enqueue(response("{\"text\":\"नमस्ते\"}"))
        assertEquals("नमस्ते", api.reply("Gemini", listOf(ChatMessage(text = "Response stopped", isUser = false, state = "stopped")) + history))
        val request = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertEquals("/api/chat", request.path)
        assertEquals("POST", request.method)
        val json = JSONObject(request.body.readUtf8())
        assertEquals("Gemini", json.getString("model"))
        assertEquals(1, json.getJSONArray("messages").length())
        assertEquals("Hi", json.getJSONArray("messages").getJSONObject(0).getString("text"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun enabledMemoryIsPassedAndOversizedMemoryRejectedBeforeNetwork() = runBlocking {
        server.enqueue(response("{\"text\":\"Reply\"}"))
        api.reply("Dua", history, memory = "- Prefer Hindi")
        val request = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertEquals("- Prefer Hindi", JSONObject(request.body.readUtf8()).getString("memory"))
        try { api.reply("Dua", history, memory = "x".repeat(3001)); fail("Oversized memory accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun fileContentIsIncludedInRequest() = runBlocking {
        server.enqueue(response("{\"text\":\"Read file\"}"))
        assertEquals("Read file", api.reply("Dua", history, listOf(ChatAttachment("notes.txt", "text/plain", "aGk="))))
        val json = JSONObject(server.takeRequest(3, TimeUnit.SECONDS)!!.body.readUtf8())
        val file = json.getJSONArray("attachments").getJSONObject(0)
        assertEquals("aGk=", file.getString("data"))
        assertEquals("text/plain", file.getString("mimeType"))
    }

    @Test fun serverErrorIsPreserved() = runBlocking {
        server.enqueue(response("{\"error\":\"Quota reached\"}").setResponseCode(429))
        val error = runCatching { api.reply("Dua", history) }.exceptionOrNull()
        assertTrue(error is DuaApiException)
        assertEquals("Quota reached", error?.message)
    }

    @Test fun oversizedResponseIsRejected() = runBlocking {
        server.enqueue(response("x".repeat(1048577)))
        val error = runCatching { api.reply("Dua", history) }.exceptionOrNull()
        assertTrue(error is DuaApiException)
        assertEquals("Dua response bahut bada hai.", error?.message)
    }
}

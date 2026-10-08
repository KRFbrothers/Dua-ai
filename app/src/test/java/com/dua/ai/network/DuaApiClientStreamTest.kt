package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.OkHttpClient
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
import java.io.IOException
import java.util.concurrent.TimeUnit

class DuaApiClientStreamTest {
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
        transport = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        api = DuaApiClient(server.url("/").toString(), transport)
    }

    @After fun tearDown() {
        transport.dispatcher.cancelAll()
        transport.connectionPool.evictAll()
        server.shutdown()
        transport.dispatcher.executorService.shutdown()
    }

    private fun event(type: String, body: String) = "event: $type\ndata: {\"type\":\"$type\"$body}\n\n"
    private fun stream(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream; charset=utf-8").setBody(body)

    @Test fun streamsEventsAndSendsStreamingFields() = runBlocking {
        server.enqueue(stream(": stream\n\n" + event("thinking", ",\"delta\":\"Hmm\"") + ": ping\n\n" +
            event("text", ",\"delta\":\"Hel\"") + event("text", ",\"delta\":\"lo\"") + event("done", ",\"truncated\":false")))
        val events = withTimeout(5000) { api.streamReply("Gemini", history, reasoning = true, web = "search").toList() }
        assertEquals(listOf(ReplyEvent.Thinking("Hmm"), ReplyEvent.Text("Hel"), ReplyEvent.Text("lo"), ReplyEvent.Done(false)), events)
        val request = server.takeRequest(3, TimeUnit.SECONDS)!!
        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.getBoolean("stream")); assertEquals("reasoning", body.getString("mode")); assertEquals("search", body.getString("web"))
        assertTrue(request.getHeader("Accept")!!.startsWith("text/event-stream"))
    }

    @Test fun eventsArriveBeforeTheStreamEnds() = runBlocking {
        server.enqueue(stream(event("text", ",\"delta\":\"first\"") + " ".repeat(64) + "\n" + event("text", ",\"delta\":\"second\"") + event("done", ""))
            .throttleBody(40, 150, TimeUnit.MILLISECONDS))
        val started = System.nanoTime()
        val firstAt = CompletableDeferred<Long>()
        val events = withTimeout(10000) {
            val collected = mutableListOf<ReplyEvent>()
            api.streamReply("Grok", history).collect { if (collected.isEmpty()) firstAt.complete(System.nanoTime()); collected += it }
            collected
        }
        val finished = System.nanoTime()
        assertEquals(3, events.size)
        assertTrue("first event should arrive well before the end", finished - firstAt.await() > TimeUnit.MILLISECONDS.toNanos(300))
        assertTrue(firstAt.await() > started)
    }

    @Test fun errorAfterPartialTextKeepsEarlierEvents() = runBlocking {
        server.enqueue(stream(event("text", ",\"delta\":\"Partial\"") + event("error", ",\"message\":\"Grok stopped early.\",\"status\":502")))
        val received = mutableListOf<ReplyEvent>()
        val error = runCatching { withTimeout(5000) { api.streamReply("Grok", history).collect { received += it } } }.exceptionOrNull()
        assertEquals(listOf(ReplyEvent.Text("Partial")), received)
        assertTrue(error is DuaApiException); assertEquals("Grok stopped early.", error!!.message); assertEquals(502, (error as DuaApiException).status)
    }

    @Test fun jsonErrorsBeforeTheStreamKeepStatusAndJsonRepliesStillWork() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Content-Type", "application/json").setHeader("Retry-After", "30")
            .setBody("{\"error\":\"Gemini quota reached. Try again in 30 seconds.\"}"))
        val error = runCatching { withTimeout(5000) { api.streamReply("Gemini", history).toList() } }.exceptionOrNull()
        assertTrue(error is DuaApiException); assertEquals(429, (error as DuaApiException).status)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"text\":\"Old server reply\"}"))
        assertEquals(listOf(ReplyEvent.Text("Old server reply"), ReplyEvent.Done(false)), withTimeout(5000) { api.streamReply("Gemini", history).toList() })
    }

    @Test fun streamEndingWithoutDoneIsAnInterruption() = runBlocking {
        server.enqueue(stream(event("text", ",\"delta\":\"cut\"")).setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        val received = mutableListOf<ReplyEvent>()
        val error = runCatching { withTimeout(5000) { api.streamReply("Dua", history).collect { received += it } } }.exceptionOrNull()
        assertEquals(listOf(ReplyEvent.Text("cut")), received)
        assertTrue(error is IOException)
    }

    @Test fun cancellingCollectionCancelsTheCall() = runBlocking {
        server.enqueue(stream(event("text", ",\"delta\":\"x\"") + ": ping\n\n".repeat(200)).throttleBody(16, 100, TimeUnit.MILLISECONDS))
        val first = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) { api.streamReply("Dua", history).collect { first.complete(Unit) } }
        withTimeout(5000) { first.await() }
        withTimeout(3000) { job.cancelAndJoin() }
        withTimeout(3000) { while (transport.dispatcher.runningCallsCount() != 0) delay(10) }
        assertTrue(job.isCancelled)
    }

    @Test fun historySkipsStoppedRepliesAndCarriesEarlierFileTextWithinBudget() {
        val messages = listOf(
            ChatMessage(text = "Summarise\nFiles: a.csv", isUser = true, attachmentText = "Attached text file (a.csv):\nx,y"),
            ChatMessage(text = "Partial", isUser = false, state = "stopped"),
            ChatMessage(text = "", isUser = false, state = "stopped"),
            ChatMessage(text = "And now?", isUser = true, attachmentText = "current file"),
            ChatMessage(text = "", isUser = false, state = ChatMessage.STREAMING),
        )
        val json = DuaApiClient.historyJson(messages, includeFileText = true)
        assertEquals(2, json.length())
        assertEquals("Attached text file (a.csv):\nx,y", json.getJSONObject(0).getString("attachmentText"))
        assertFalse("current turn sends its file as an attachment", json.getJSONObject(1).has("attachmentText"))
        assertFalse(DuaApiClient.historyJson(messages, includeFileText = false).getJSONObject(0).has("attachmentText"))
        val big = (0 until 6).flatMap { listOf(ChatMessage(text = "q$it", isUser = true, attachmentText = "y".repeat(30_000)), ChatMessage(text = "a$it", isUser = false)) } +
            ChatMessage(text = "last", isUser = true)
        val budgeted = DuaApiClient.historyJson(big, includeFileText = true)
        val withText = (0 until budgeted.length()).filter { budgeted.getJSONObject(it).has("attachmentText") }
        assertEquals("newest files fit the budget", listOf(6, 8, 10), withText)
    }
}

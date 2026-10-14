package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Both chat engines verify the owner session; Cloud Run may additionally require an app token. */
class DuaApiClientEngineTest {
    private val history = listOf(ChatMessage(text = "Hi", isUser = true))
    private fun engine() = DuaApiClient("https://engine.example.run.app", apiBaseUrl = "https://base.example.app")
    private fun json(request: okhttp3.Request): JSONObject = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())

    @Test fun separateEngineGetsOwnerTokenAndOptionalAppToken() {
        val api = DuaApiClient("https://engine.example.run.app", appToken = "abc", apiBaseUrl = "https://base.example.app")
        val request = api.buildRequest("Dua", history, emptyList(), "", "session.token", null, reasoning = false, stream = true)
        assertEquals("https://engine.example.run.app/api/chat", request.url.toString())
        assertEquals("abc", request.header("X-Dua-App-Token"))
        assertEquals("Bearer session.token", request.header("Authorization"))
    }

    @Test fun defaultClientSendsAccountTokenAndNoAppToken() {
        val api = DuaApiClient("https://dua.example.app")
        val request = api.buildRequest("Dua", history, emptyList(), "", "session.token", null, reasoning = false, stream = true)
        assertEquals("Bearer session.token", request.header("Authorization"))
        assertNull(request.header("X-Dua-App-Token"))
    }

    @Test fun baseBackendWithoutProviderKeysDoesNotDisableSeparateEngineChat() {
        val empty = IntegrationCapabilities.configuration(JSONObject("""{"providers":[]} """), IntegrationState())
        assertTrue(empty.providers.none { it.configured })
        val api = engine()
        val chat = api.chatConfiguration(empty)
        for (model in listOf("Dua", "Gemini")) {
            assertTrue(chat.providers.single { it.name == model }.configured)
            assertTrue(api.chatProvider(model, empty.providers)!!.configured)
        }
        assertFalse(chat.filesEnabled) // No false claim about base-backend storage/setup.
        assertEquals(empty, DuaApiClient("https://base.example.app").chatConfiguration(empty))
        assertNull(DuaApiClient("https://base.example.app").chatProvider("Gemini", empty.providers))
    }

    @Test fun chatRoutingIgnoresTrailingSlashAndSendsOwnerTokenWithoutEnablingPersistence() {
        val same = DuaApiClient("https://base.example.app/", apiBaseUrl = "https://base.example.app")
        assertFalse(same.separateChatEngine)
        assertFalse(same.usesInlineChatFiles(false))
        assertTrue(engine().usesInlineChatFiles(false))
        assertFalse(engine().usesInlineChatFiles(true)) // Imagine still uses its existing base-backend storage.
        val request = engine().buildRequest("Gemini", history, emptyList(), "", "session.token", null, false, true)
        assertEquals("Bearer session.token", request.header("Authorization"))
        assertFalse(json(request).has("persistence"))
    }

    @Test fun signedOutEngineRequestCannotSubstituteAppTokenForOwnerSession() {
        val api = DuaApiClient("https://engine.example.run.app", appToken = "abc", apiBaseUrl = "https://base.example.app")
        val request = api.buildRequest("Gemini", history, emptyList(), "", null, null, false, true)
        assertNull(request.header("Authorization"))
        assertEquals("abc", request.header("X-Dua-App-Token"))
    }

    @Test fun followUpsKeepInlineContentsOnTheirOriginalTurnsWithoutStorageReferences() {
        val photo = ChatAttachment("photo.png", "image/png", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
        val turns = listOf(
            ChatMessage(text = "Explain this file", isUser = true, attachmentText = "name,total\nSales,42", inlineAttachments = listOf(photo)),
            ChatMessage(text = "The total is 42", isUser = false),
            ChatMessage(text = "What about its chart?", isUser = true),
        )
        val body = json(engine().buildRequest("Gemini", turns, emptyList(), "", null, null, false, true))
        val messages = body.getJSONArray("messages")
        assertEquals(photo.data, messages.getJSONObject(0).getJSONArray("attachments").getJSONObject(0).getString("data"))
        assertEquals(turns[0].attachmentText, messages.getJSONObject(0).getString("attachmentText"))
        assertFalse(messages.getJSONObject(0).has("files"))
        assertFalse(messages.getJSONObject(2).has("attachments"))
        val same = json(DuaApiClient("https://base.example.app").buildRequest("Gemini", turns, emptyList(), "", null, null, false, true))
        assertFalse(same.getJSONArray("messages").getJSONObject(0).has("attachments"))
    }

    @Test fun initialAttachmentIsNotDuplicatedAndRetryStillHasItsBytes() {
        val pdf = ChatAttachment("a.pdf", "application/pdf", "AQID")
        val turn = ChatMessage(text = "Read", isUser = true, inlineAttachments = listOf(pdf))
        val api = engine()
        val initial = json(api.buildRequest("Gemini", listOf(turn), listOf(pdf), "", null, null, false, true))
        assertEquals(1, initial.getJSONArray("attachments").length())
        assertFalse(initial.getJSONArray("messages").getJSONObject(0).has("attachments"))
        val retry = json(api.buildRequest("Gemini", listOf(turn), emptyList(), "", null, null, false, true))
        assertEquals(pdf.data, retry.getJSONArray("messages").getJSONObject(0).getJSONArray("attachments").getJSONObject(0).getString("data"))
    }

    @Test fun restoredTextContextIsSentEvenWithoutTransientAttachmentBytes() {
        val turn = ChatMessage(text = "Retry", isUser = true, attachmentText = "Attached text file (a.csv):\nRevenue,42")
        val body = json(engine().buildRequest("Gemini", listOf(turn), emptyList(), "", null, null, false, true))
        assertEquals(turn.attachmentText, body.getJSONArray("messages").getJSONObject(0).getString("attachmentText"))
    }

    @Test fun inlineAudioDoesNotNeedBaseStorageAndIsAcceptedByTheEngineProvider() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = com.dua.ai.attachments.PhotoPolicy.readSource("voice.m4a", "application/octet-stream", bytes.inputStream(), allowAudio = true)
        val files = com.dua.ai.attachments.PhotoAttachmentReader.prepare(listOf(source))
        assertEquals("audio/mp4", files.single().mimeType)
        assertNull(IntegrationCapabilities.attachmentError(engine().chatProvider("Gemini", emptyList())!!, files.map { it.mimeType }))
        val body = json(engine().buildRequest("Gemini", history, files, "", null, null, false, true))
        assertEquals(Base64.getEncoder().encodeToString(bytes), body.getJSONArray("attachments").getJSONObject(0).getString("data"))
    }

    @Test fun historyKeepsTextFallbackButDoesNotPersistTransientMediaBytes() {
        val file = ChatAttachment("a.pdf", "application/pdf", Base64.getEncoder().encodeToString("private transient bytes".toByteArray()))
        val turn = ChatMessage(text = "Read", isUser = true, attachmentText = "Saved text context", inlineAttachments = listOf(file))
        val chat = com.dua.ai.history.SavedChat("chat", "Gemini", "", listOf(turn), 1)
        val encoded = com.dua.ai.history.ChatHistoryCodec.encode(listOf(chat), "chat")
        assertFalse(encoded.contains(file.data))
        val restored = com.dua.ai.history.ChatHistoryCodec.decode(encoded).second.single().messages.single()
        assertEquals(turn.attachmentText, restored.attachmentText)
        assertTrue(restored.inlineAttachments.isEmpty())
        val refreshed = engine().keepInlineContext(listOf(restored), listOf(turn)).single()
        assertEquals(turn.inlineAttachments, refreshed.inlineAttachments)
        assertTrue(engine().keepInlineContext(listOf(restored.copy(text = "Edited")), listOf(turn)).single().inlineAttachments.isEmpty())
        assertTrue(engine().keepInlineContext(emptyList(), listOf(turn)).isEmpty())
        assertTrue(DuaApiClient("https://base.example.app").keepInlineContext(listOf(restored), listOf(turn)).single().inlineAttachments.isEmpty())
    }

    @Test fun heartbeatsKeepReadTimeoutAliveWhileWaitingForText() = runBlocking {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val tls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val transport = OkHttpClient.Builder().sslSocketFactory(tls.sslSocketFactory(), tls.trustManager)
            .protocols(listOf(Protocol.HTTP_1_1)).readTimeout(500, TimeUnit.MILLISECONDS).build()
        val server = MockWebServer().apply { protocols = listOf(Protocol.HTTP_1_1); useHttps(serverTls.sslSocketFactory(), false); start() }
        try {
            // Establish TLS outside the short read-timeout assertion, then reuse that connection.
            server.enqueue(MockResponse().setBody("ready"))
            transport.newBuilder().readTimeout(10, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(server.url("/")).build()).execute().use { assertEquals("ready", it.body!!.string()) }
            val body = ": heartbeat\n\n".repeat(100) +
                "event: text\ndata: {\"type\":\"text\",\"delta\":\"Hello\"}\n\n" +
                "event: done\ndata: {\"type\":\"done\",\"truncated\":false}\n\n"
            // MockWebServer throttles request reads too. A full request fits in two fast batches;
            // the heartbeat prefix delays the first text longer than the 500ms read timeout.
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body).throttleBody(128, 100, TimeUnit.MILLISECONDS))
            val api = DuaApiClient(server.url("/").toString(), transport, apiBaseUrl = "https://base.example.app")
            val started = System.nanoTime()
            var firstTextAt = 0L
            val events = withTimeout(10000) { api.streamReply("Gemini", history, token = "session.token").onEach {
                if (it is ReplyEvent.Text && firstTextAt == 0L) firstTextAt = System.nanoTime()
            }.toList() }
            assertEquals(listOf(ReplyEvent.Text("Hello"), ReplyEvent.Done(false)), events)
            server.takeRequest() // TLS warm-up request.
            assertEquals("Bearer session.token", server.takeRequest().getHeader("Authorization"))
            assertTrue(firstTextAt - started > TimeUnit.MILLISECONDS.toNanos(500))
        } finally {
            transport.dispatcher.cancelAll(); transport.connectionPool.evictAll(); server.shutdown()
            transport.dispatcher.executorService.shutdown()
        }
    }
}

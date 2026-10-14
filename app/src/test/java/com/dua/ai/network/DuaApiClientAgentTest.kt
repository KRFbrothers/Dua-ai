package com.dua.ai.network

import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ReplySources
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Agent mode (Cloud Run engine) sends mode "agent" on the existing streaming /api/chat path. */
class DuaApiClientAgentTest {
    private val history = listOf(ChatMessage(text = "Latest AI news?", isUser = true))
    private val api = DuaApiClient("https://engine.example.run.app", sendAccountToken = false)

    private fun body(agent: Boolean, reasoning: Boolean = false, build: String? = null): JSONObject {
        val request = api.buildRequest("Dua", history, emptyList(), "", null, null, reasoning = reasoning, stream = true, build = build, agent = agent)
        return JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
    }

    @Test fun agentSendsAgentMode() {
        assertEquals("agent", body(agent = true).getString("mode"))
        assertEquals("agent", body(agent = true, reasoning = true).getString("mode"))
    }

    @Test fun withoutAgentModesAreUnchanged() {
        assertEquals("auto", body(agent = false).getString("mode"))
        assertEquals("reasoning", body(agent = false, reasoning = true).getString("mode"))
    }

    /** Wire events exactly as the Cloud Run agent sends them: sources event, answer text, footer text chunk, done. */
    @Test fun groundedAgentReplyKeepsClickableSourcesInFinalText() {
        val wire = listOf(
            """{"type":"thinking","delta":"**Step 1 - Plan**\n"}""",
            """{"type":"sources","sources":[{"id":1,"title":"AI news","url":"https://news.example.com/a","domain":"news.example.com"}]}""",
            """{"type":"text","delta":"Gemini 3.8 Flash is out [1]."}""",
            """{"type":"text","delta":"\n\nSources:\n[1] AI news\nhttps://news.example.com/a"}""",
            """{"type":"done","truncated":false}""",
        )
        val accumulator = StreamAccumulator(0L) { 1000L }
        val parser = ChatStreamParser()
        wire.forEach { data -> listOf("data: $data", "").forEach { line -> parser.line(line)?.let(accumulator::apply) } }
        val parts = ReplySources.split(accumulator.snapshot().text)
        assertEquals("Gemini 3.8 Flash is out [1].", parts.body)
        assertEquals(listOf("https://news.example.com/a"), parts.sources.map { it.url })
        assertEquals("AI news", parts.sources.single().title)
    }

    @Test fun agentCapabilityNeedsExplicitTrue() {
        assertTrue(DuaApiClient.parseAgentCapability("""{"status":"ok","agent":true}"""))
        assertFalse(DuaApiClient.parseAgentCapability("""{"status":"ok"}"""))
        assertFalse(DuaApiClient.parseAgentCapability("""{"agent":"true"}"""))
        assertFalse(DuaApiClient.parseAgentCapability("<html>Vercel</html>"))
    }

    @Test fun engineCapabilityIsFetchedFromRootAndFailureHidesAgent() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val transport = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        try {
            val engine = DuaApiClient(server.url("/").toString(), transport, appToken = "abc", sendAccountToken = false)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"status":"ok","agent":true}"""))
            assertTrue(engine.engineAgentAvailable())
            val request = server.takeRequest()
            assertEquals("/", request.path)
            assertEquals("GET", request.method)
            assertEquals("abc", request.getHeader("X-Dua-App-Token"))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"agent":true}"""))
            assertFalse(engine.engineAgentAvailable())
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"status":"ok"}"""))
            assertFalse(engine.engineAgentAvailable())
        } finally {
            server.shutdown()
        }
        assertFalse(DuaApiClient("http://insecure.example").engineAgentAvailable())
    }
}

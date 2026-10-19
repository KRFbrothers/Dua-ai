package com.dua.ai.network

import com.dua.ai.model.AgentApproval
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Agent approvals on the wire, the approve/reject call, and the Agent timeout. */
class AgentApprovalStreamTest {
    private val id = "AbCdEfGhIjKlMnOpQrStUvWx"

    @Test fun approvalEventReachesTheSnapshot() {
        val wire = listOf(
            """{"type":"approval","id":"$id","tool":"propose_changes","title":"Open a pull request","details":["Title: Fix","app/A.kt (10 bytes)"],"expiresIn":900}""",
            """{"type":"text","delta":"**Approval needed.**"}""",
            """{"type":"done","truncated":false}""",
        )
        val accumulator = StreamAccumulator(0L) { 1000L }
        val parser = ChatStreamParser()
        wire.forEach { data -> listOf("data: $data", "").forEach { line -> parser.line(line)?.let(accumulator::apply) } }
        val approval = accumulator.snapshot().approvals.single()
        assertEquals(id, approval.id)
        assertEquals(listOf("Title: Fix", "app/A.kt (10 bytes)"), approval.details)
        assertEquals(AgentApproval.Status.Pending, approval.status)
    }

    @Test fun agentRepliesWaitLongerThanTheEngineLimits() {
        val api = DuaApiClient("https://engine.example.run.app")
        val agent = api.clientFor(agent = true).callTimeoutMillis
        val chat = api.clientFor(agent = false).callTimeoutMillis
        assertEquals(TimeUnit.SECONDS.toMillis(DuaApiClient.AGENT_CALL_TIMEOUT_SECONDS), agent.toLong())
        // Above Cloud Run's 900 s request limit (and the 600 s agent loop), so the server always ends first.
        assertTrue(agent > 900_000)
        assertEquals(330_000, chat)
        assertEquals(api.clientFor(agent = false).readTimeoutMillis, api.clientFor(agent = true).readTimeoutMillis)
    }

    @Test fun approveAndRejectSendOnlyTheIdWithTheOwnerSession() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val transport = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        try {
            val engine = DuaApiClient(server.url("/").toString(), transport, appToken = "abc")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"status":"done","text":"Issue created: #5"}"""))
            assertEquals(AgentApproval.Status.Done to "Issue created: #5", engine.agentAction(id, approve = true, token = "tok.en"))
            val request = server.takeRequest()
            assertEquals("/api/agent/action", request.path)
            assertEquals("Bearer tok.en", request.getHeader("Authorization"))
            assertEquals("abc", request.getHeader("X-Dua-App-Token"))
            val sent = JSONObject(request.body.readUtf8())
            assertEquals(id, sent.getString("id")); assertEquals("approve", sent.getString("decision")); assertEquals(2, sent.length())

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"status":"rejected","text":"You declined"}"""))
            assertEquals(AgentApproval.Status.Rejected, engine.agentAction(id, approve = false, token = "tok").first)
            assertEquals("reject", JSONObject(server.takeRequest().body.readUtf8()).getString("decision"))

            server.enqueue(MockResponse().setResponseCode(410).setHeader("Content-Type", "application/json").setBody("""{"error":"This approval expired (15 minutes)."}"""))
            try { engine.agentAction(id, approve = true, token = "tok"); fail("expected an error") }
            catch (error: DuaApiException) { assertEquals(410, error.status); assertTrue(error.message!!.contains("expired")) }
        } finally {
            server.shutdown()
        }
    }
}

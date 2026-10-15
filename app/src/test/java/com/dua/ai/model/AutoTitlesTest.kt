package com.dua.ai.model

import com.dua.ai.network.DuaApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTitlesTest {
    private val user = ChatMessage(text = "Dark mode kaise add karun?", isUser = true)
    private val reply = ChatMessage(text = "Use MaterialTheme with dark colors.", isUser = false)

    @Test fun onlyFirstFinishedExchangeGetsATitle() {
        assertTrue(AutoTitles.shouldTitle(listOf(user, reply)))
        assertFalse(AutoTitles.shouldTitle(listOf(user)))
        assertFalse(AutoTitles.shouldTitle(listOf(user, reply.copy(state = ChatMessage.STREAMING))))
        assertFalse(AutoTitles.shouldTitle(listOf(user, reply, user.copy(id = "u2"), reply.copy(id = "r2"))))
        assertFalse(AutoTitles.shouldTitle(listOf(user, reply.copy(text = ""))))
    }

    @Test fun titleParsingRejectsBadAnswers() {
        assertEquals("Dark Mode in Compose", DuaApiClient.parseTitle("""{"title":"  Dark  Mode in\nCompose "}"""))
        assertNull(DuaApiClient.parseTitle("""{"title":""}"""))
        assertNull(DuaApiClient.parseTitle("""{"error":"x"}"""))
        assertNull(DuaApiClient.parseTitle("<html>"))
        assertTrue(DuaApiClient.parseTitle("""{"title":"${"x".repeat(200)}"}""")!!.length <= 60)
    }

    @Test fun titleRequestUsesOwnerSessionAndFailureIsNull() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val transport = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        try {
            val api = DuaApiClient(server.url("/").toString(), transport)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"title":"Dark Mode Help"}"""))
            assertEquals("Dark Mode Help", api.chatTitle(user.text, reply.text, "session.token"))
            val request = server.takeRequest()
            assertEquals("/api/title", request.path)
            assertEquals("Bearer session.token", request.getHeader("Authorization"))
            assertEquals(user.text, JSONObject(request.body.readUtf8()).getString("user"))
            server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"x"}"""))
            assertNull(api.chatTitle(user.text, reply.text, "session.token"))
        } finally { server.shutdown() }
    }
}

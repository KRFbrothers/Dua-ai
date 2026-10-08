package com.dua.ai.imagine

import com.dua.ai.account.AccountException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ImagineApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: ImagineApi
    private val uid = "11111111-2222-4333-8444-555555555555"
    private fun json(value: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(value)

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server = MockWebServer(); server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false); server.start()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client = OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager).followRedirects(false).build()
        api = ImagineApi(server.url("/").toString(), client)
    }
    @After fun teardown() { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); server.shutdown(); client.dispatcher.executorService.shutdown() }

    @Test fun imageRequestSendsOwnerTokenAndOptions() = runBlocking {
        server.enqueue(json("""{"images":[{"path":"$uid/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg","mimeType":"image/jpeg","size":5,"url":"https://proj.supabase.co/s","expiresIn":3600}],"provider":"gemini","model":"gemini-nano-banana-2.1","edit":true,"notice":"Grok Imagine was unavailable"}"""))
        val result = api.image("Make it night", ImagineOptions(aspectRatio = "3:4", count = 1, provider = "gemini"), listOf("$uid/cccccccc-bbbb-4ccc-8ddd-eeeeeeeeeeee.png"), "owner-token")
        val request = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertEquals("/api/imagine/image", request.path)
        assertEquals("Bearer owner-token", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("3:4", body.getString("aspectRatio")); assertEquals("gemini", body.getString("provider"))
        assertEquals("gemini", result.provider); assertTrue(result.edit); assertEquals("Grok Imagine was unavailable", result.notice)
    }

    @Test fun contentPolicyAndCapErrorsSurfaceTheServerMessage() = runBlocking {
        server.enqueue(json("""{"error":"The provider declined this request under its content policy. Try a different description.","code":"content_policy"}""", 422))
        val failure = runCatching { api.image("x", ImagineOptions(), emptyList(), "t") }.exceptionOrNull() as AccountException
        assertEquals(422, failure.status); assertTrue(failure.message!!.contains("content policy"))
        server.enqueue(json("""{"error":"Daily Imagine limit reached: 20 of 20 images used.","code":"cap"}""", 429))
        assertEquals(429, (runCatching { api.image("x", ImagineOptions(), emptyList(), "t") }.exceptionOrNull() as AccountException).status)
    }

    @Test fun videoJobThenStatusPolling() = runBlocking {
        val token = "eyJ2IjoxfQ.abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"
        server.enqueue(json("""{"jobId":"$token","status":"pending","seconds":8,"resolution":"720p","aspectRatio":"16:9","model":"grok-imagine-video-1.5","pollAfterSeconds":5}"""))
        server.enqueue(json("""{"status":"pending","progress":55,"pollAfterSeconds":5}"""))
        server.enqueue(json("""{"status":"done","video":{"path":"$uid/dddddddd-bbbb-4ccc-8ddd-eeeeeeeeeeee.mp4","mimeType":"video/mp4","size":1234,"duration":8,"url":"https://proj.supabase.co/v","expiresIn":3600}}"""))
        val job = api.video("Waves", ImagineOptions(kind = "video", aspectRatio = "16:9", duration = 8), null, "t")
        assertEquals(token, job.jobId)
        assertEquals(55, api.status(job.jobId, "t").progress)
        val done = api.status(job.jobId, "t")
        assertEquals("done", done.status); assertEquals(8, done.media!!.duration); assertTrue(done.media!!.isVideo)
        server.takeRequest(); assertEquals(token, JSONObject(server.takeRequest().body.readUtf8()).getString("jobId"))
        assertEquals("/api/imagine/status", server.takeRequest().path)
    }
}

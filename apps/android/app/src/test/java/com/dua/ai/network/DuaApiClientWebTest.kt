package com.dua.ai.network

import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.model.ChatMessage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DuaApiClientWebTest {
    private val requests = CopyOnWriteArrayList<Request>()
    private val callsStarted = AtomicInteger()
    private lateinit var transport: OkHttpClient
    private lateinit var api: DuaApiClient
    private val history = listOf(ChatMessage(text = "Hi", isUser = true))

    @Before fun setUp() {
        transport = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    callsStarted.incrementAndGet()
                }
            })
            .addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "application/json")
                    .body("{\"text\":\"Reply\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        api = DuaApiClient("https://dua.example/", transport)
    }

    @After fun tearDown() {
        transport.dispatcher.cancelAll()
        transport.connectionPool.evictAll()
        transport.dispatcher.executorService.shutdown()
    }

    private fun requestJson(request: Request): JSONObject {
        val buffer = Buffer()
        requireNotNull(request.body).writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test fun defaultAndExplicitlyDisabledWebAreOmitted() = runBlocking {
        assertEquals("Reply", withTimeout(3000) { api.reply("Dua", history) })
        assertEquals("Reply", withTimeout(3000) { api.reply("Dua", history, web = null) })

        assertEquals(2, requests.size)
        requests.forEach { request ->
            val json = requestJson(request)
            assertFalse(json.has("web"))
            assertEquals(setOf("model", "messages", "attachments", "memory"), json.keys().asSequence().toSet())
        }
    }

    @Test fun selectedWebModesPreserveTheExistingRequestContract() = runBlocking {
        val messages = history + listOf(
            ChatMessage(text = "Hello", isUser = false),
            ChatMessage(text = "Response stopped", isUser = false, state = "stopped")
        )
        val attachment = ChatAttachment("notes.txt", "text/plain", "aGk=")
        listOf("search", "page").forEach { mode ->
            assertEquals("Reply", withTimeout(3000) {
                api.reply("Gemini", messages, listOf(attachment), "- Prefer Hindi", "fake-session", mode)
            })

            val request = requests.last()
            val json = requestJson(request)
            assertEquals("https://dua.example/api/chat", request.url.toString())
            assertEquals("POST", request.method)
            assertEquals("application/json", request.header("Accept"))
            assertEquals("1", request.header("x-dua-native"))
            assertEquals("Bearer fake-session", request.header("Authorization"))
            assertEquals(setOf("model", "messages", "attachments", "memory", "web"), json.keys().asSequence().toSet())
            assertEquals(mode, json.getString("web"))
            assertEquals("Gemini", json.getString("model"))
            assertEquals("- Prefer Hindi", json.getString("memory"))
            val sentMessages = json.getJSONArray("messages")
            assertEquals(2, sentMessages.length())
            assertEquals("user", sentMessages.getJSONObject(0).getString("role"))
            assertEquals("Hi", sentMessages.getJSONObject(0).getString("text"))
            assertEquals("model", sentMessages.getJSONObject(1).getString("role"))
            assertEquals("Hello", sentMessages.getJSONObject(1).getString("text"))
            val files = json.getJSONArray("attachments")
            assertEquals(1, files.length())
            val file = files.getJSONObject(0)
            assertEquals(attachment.name, file.getString("name"))
            assertEquals(attachment.mimeType, file.getString("mimeType"))
            assertEquals(attachment.data, file.getString("data"))
        }
        assertEquals(2, callsStarted.get())
    }

    @Test fun invalidWebModesAreRejectedBeforeStartingTransport() = runBlocking {
        listOf("", "off", "SEARCH", "Page", " search ", "auto").forEach { mode ->
            val error = runCatching { api.reply("Dua", history, web = mode) }.exceptionOrNull()
            assertTrue("Accepted invalid web mode: $mode", error is IllegalArgumentException)
        }

        assertEquals(0, callsStarted.get())
        assertTrue(requests.isEmpty())
    }
}

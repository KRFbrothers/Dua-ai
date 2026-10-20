package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.AccountUser
import com.dua.ai.account.SessionPersistence
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.model.BuildInfo
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.BuildModeConfig
import com.dua.ai.setup.IntegrationState
import com.dua.ai.setup.ProviderCapability
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * retryResponse (ChatLifecycle.kt) driven through the real ViewModel and a real streaming client,
 * with HTTP intercepted before the network. Five behaviours the split plan called out:
 * a normal retry, Stop during a retry, a retry that fails, a Build reply, and a stale generation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class RetryResponseTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var client: OkHttpClient
    private val store = ViewModelStore()
    /** Chat request bodies, in order. Only /api/chat is recorded; anything else is answered empty. */
    private val requests = CopyOnWriteArrayList<JSONObject>()
    private val prompt = ChatMessage(id = "prompt", text = "Explain coroutines", isUser = true, model = "Gemini")

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            application.getSharedPreferences(name, 0).edit().clear().commit()
        DevicePreferences(application).update(LocalPreferences(memoryEnabled = false))
        runCatching { kotlinx.coroutines.runBlocking { AppearanceStore(application).reset() } }
        model = ChatViewModel(application).also { store.put("chat", it) }
        for (name in listOf("engineCheck", "cloudImagineCheck")) (field(name).get(model) as? Job)?.cancel()
    }

    @After fun teardown() {
        store.clear()
        if (::client.isInitialized) {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    // ---- harness ----

    private fun field(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> flow(name: String) = field(name).get(model) as MutableStateFlow<T>
    private fun setField(name: String, value: Any?) { field(name).set(model, value) }

    /** Points the ViewModel at an intercepted client; [handler] answers each /api/chat request. */
    private fun intercept(handler: (JSONObject, okhttp3.Request) -> Response) {
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.encodedPath != "/api/chat") {
                return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("Content-Type", "application/json").body("{}".toResponseBody("application/json".toMediaType())).build()
            }
            val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            requests += body
            handler(body, request)
        }.build()
        setField("api", DuaApiClient("https://chat.test", client, apiBaseUrl = "https://chat.test"))
        setField("separateChatEngine", false)
        flow<IntegrationState>("_integrations").value = IntegrationState(personalityEnabled = false)
        flow<Boolean>("_agentAvailable").value = false
    }

    private fun sse(text: String) = "data: {\"type\":\"text\",\"delta\":\"$text\"}\n\ndata: {\"type\":\"done\",\"truncated\":false}\n\n"

    private fun reply(text: String, state: String = "complete", id: String = "reply") =
        ChatMessage(id = id, text = text, isUser = false, model = "Gemini", state = state)

    /** Seeds the retryable state: one finished turn, exactly what Retry accepts. */
    private fun seed(original: ChatMessage = reply("Original answer")) {
        flow<List<ChatMessage>>("_messages").value = listOf(prompt, original)
        flow<Boolean>("_isTyping").value = false
        await("preferences loaded") { field("appearanceLoaded").getBoolean(model) }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val until = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idleFor(25, TimeUnit.MILLISECONDS)
        }
        assertTrue("Timed out: $description; requests=${requests.size}, typing=${model.isTyping.value}, " +
            "error=${model.error.value}, messages=${model.messages.value.map { it.state }}", condition())
    }

    /** Bounded idle: lets anything already queued run, without asserting a timing-dependent outcome. */
    private fun settle(milliseconds: Long) {
        val until = System.nanoTime() + milliseconds * 1_000_000L
        while (System.nanoTime() < until) shadowOf(Looper.getMainLooper()).idleFor(25, TimeUnit.MILLISECONDS)
    }

    private fun finished() = await("retry finished") { !model.isTyping.value && model.messages.value.lastOrNull()?.streaming != true }

    // ---- 1. normal retry ----

    @Test fun aNormalRetryReplacesTheOldReplyAndSavesHistory() {
        intercept { _, request -> Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Type", "text/event-stream").body(sse("Retried answer").toResponseBody("text/event-stream".toMediaType())).build() }
        seed()

        model.retryResponse("reply")
        finished()

        assertEquals(listOf("Explain coroutines", "Retried answer"), model.messages.value.map { it.text })
        assertEquals(2, model.messages.value.size)
        val reply = model.messages.value.last()
        assertEquals("reply", reply.id)
        assertEquals("complete", reply.state)
        assertNull(model.error.value)
        assertNull(field("retryOriginal").get(model))
        // The old reply is not resent: only the original prompt goes up, under the reply's own model.
        assertEquals(1, requests.size)
        assertEquals("Gemini", requests[0].getString("model"))
        assertEquals(1, requests[0].getJSONArray("messages").length())
        assertEquals("Explain coroutines", requests[0].getJSONArray("messages").getJSONObject(0).getString("text"))
        // History is written, not just the in-memory list.
        model.flushHistory()
        val saved = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second.single()
        assertEquals(listOf("Explain coroutines", "Retried answer"), saved.messages.map { it.text })
        assertEquals(listOf("complete", "complete"), saved.messages.map { it.state })
    }

    // ---- 2. Stop in the middle of a retry ----

    @Test fun stoppingARetryBeforeTextRestoresTheOriginalReply() {
        val gate = CountDownLatch(1)
        intercept { _, request ->
            gate.await(10, TimeUnit.SECONDS) // hold the stream open with no text
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "text/event-stream").body(sse("Too late").toResponseBody("text/event-stream".toMediaType())).build()
        }
        val original = reply("Original answer")
        seed(original)

        try {
            model.retryResponse("reply")
            await("retry started") { model.isTyping.value }
            assertEquals(ChatMessage.STREAMING, model.messages.value.last().state)

            model.stopReply()

            // No text ever arrived, so the turn is exactly what it was before the retry.
            assertEquals(listOf(prompt, original), model.messages.value)
            assertEquals("Response stopped.", model.error.value)
            assertNull(field("retryOriginal").get(model))
            assertFalse(model.isTyping.value)
        } finally {
            gate.countDown()
        }
        settle(100)
        assertEquals(listOf(prompt, original), model.messages.value)
        assertFalse(model.isTyping.value)
    }

    // ---- 3. retry fails before any text ----

    @Test fun aRetryThatFailsBeforeTextRestoresTheOriginalAndDoesNotQueue() {
        intercept { _, request -> Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(500).message("Server Error")
            .header("Content-Type", "application/json").body("{\"error\":\"Provider down\"}".toResponseBody("application/json".toMediaType())).build() }
        val original = reply("Original answer")
        seed(original)

        model.retryResponse("reply")
        finished()

        assertEquals(listOf(prompt, original), model.messages.value)
        assertEquals("Retry failed. Original response kept.", model.error.value)
        assertNull(field("retryOriginal").get(model))
        assertFalse(model.isTyping.value)
        // Documents the current behaviour: only sendMessage queues offline; Retry does not.
        assertFalse((field("offlineRetry").get(model) as com.dua.ai.network.OfflineRetry).hasPending)
    }

    // ---- 4. a Build reply keeps Build mode and its repository ----

    @Test fun aBuildReplyRetriesInBuildModeAgainstTheSameRepository() {
        val repository = "KRFbrothers/Dua-ai"
        setField("account", AccountRepository(AccountApi("https://account.test", signInClient()), object : SessionPersistence {
            override fun read() = AccountSession(AccountUser(UUID.randomUUID().toString(), "owner@example.com"),
                "token_abc", "refresh_abc", System.currentTimeMillis() / 1000 + 3600)
            override fun write(value: AccountSession?) = Unit
        }))
        intercept { _, request -> Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Type", "text/event-stream").body(sse("Repository answer").toResponseBody("text/event-stream".toMediaType())).build() }
        val current = flow<IntegrationState>("_integrations").value
        flow<IntegrationState>("_integrations").value = current.copy(
            providers = listOf(ProviderCapability("Gemini", true, "Gemini", true)),
            build = BuildModeConfig(true, providers = setOf("Gemini")))
        seed(reply("Old repository answer").copy(build = BuildInfo(repository)))

        model.retryResponse("reply")
        finished()

        assertEquals(1, requests.size)
        assertEquals("build", requests[0].getString("mode"))
        assertEquals(repository, requests[0].getString("repository"))
        val reply = model.messages.value.last()
        assertEquals("Repository answer", reply.text)
        assertEquals(repository, reply.build?.repository)
        assertNull(model.error.value)
    }

    // ---- 5. a stale retry after the chat was switched away ----

    @Test fun aRetryThatFinishesAfterTheChatWasLeftWritesNothing() {
        val gate = CountDownLatch(1)
        intercept { _, request ->
            gate.await(10, TimeUnit.SECONDS)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "text/event-stream").body(sse("Stale answer").toResponseBody("text/event-stream".toMediaType())).build()
        }
        seed()

        try {
            model.retryResponse("reply")
            await("retry started") { requests.size == 1 }
            val startedIn = model.activeChatId.value

            model.startNewChat() // bumps the generation and cancels the retry job
            assertTrue("the retry must not survive the switch", !model.isTyping.value)
            assertTrue(model.messages.value.isEmpty())

            gate.countDown()
            settle(400) // let the old stream finish late

            assertTrue("a stale retry must not write into the new chat", model.messages.value.isEmpty())
            assertFalse(model.isTyping.value)
            assertTrue("no turn from the old chat may leak", model.messages.value.none { it.text.contains("Stale") })
            assertFalse("the new chat must stay a fresh chat", model.activeChatId.value == startedIn)
        } finally {
            gate.countDown()
        }
    }

    /** The account calls in this test never reach HTTP; a plain client keeps the repository constructible. */
    private fun signInClient() = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Type", "application/json").body("{}".toResponseBody("application/json".toMediaType())).build()
    }.build()
}

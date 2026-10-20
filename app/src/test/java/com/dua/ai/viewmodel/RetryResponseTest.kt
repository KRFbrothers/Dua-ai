package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.SessionPersistence
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.model.BuildInfo
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.OfflineRetry
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
 * retryResponse (ChatLifecycle.kt) through the real ViewModel and the real streaming client,
 * with HTTP intercepted before the network — the same harness as PersonalityPromptingTest.
 *
 * These are the five behaviours the split report listed as unasserted: a normal retry, Stop
 * mid-retry, a retry that fails, a Build reply, and a retry left stale by a chat switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class RetryResponseTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var client: OkHttpClient
    private val store = ViewModelStore()
    /** Every /api/chat body, in order. The interceptor fails on any other URL, so nothing escapes. */
    private val requests = CopyOnWriteArrayList<JSONObject>()
    private val prompt = ChatMessage(id = "prompt", text = "Explain coroutines", isUser = true, model = "Gemini")

    private fun field(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> flow(name: String) = field(name).get(model) as MutableStateFlow<T>

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            application.getSharedPreferences(name, 0).edit().clear().commit()
        runBlocking { AppearanceStore(application).reset() }
        DevicePreferences(application).update(LocalPreferences(memoryEnabled = false))
        model = ChatViewModel(application).also { store.put("chat", it) }
        for (name in listOf("engineCheck", "cloudImagineCheck")) (field(name).get(model) as? Job)?.cancel()
        // Same account wiring as PersonalityPromptingTest: signed out. The server-replies owner flag
        // then stays off, so a retry follows the plain local path and no extra cloud call is made.
        field("account").set(model, AccountRepository(AccountApi("https://account.test", rejectingClient()), object : SessionPersistence {
            override fun read(): AccountSession? = null
            override fun write(value: AccountSession?) = Unit
        }))
        await("preferences loaded") { field("appearanceLoaded").getBoolean(model) }
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

    /** Answers each /api/chat request with [handler]; any other host or path is a test bug. */
    private fun intercept(handler: (okhttp3.Request) -> Response) {
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host == "chat.test" && request.url.encodedPath == "/api/chat") { "Unexpected test HTTP request: ${request.url}" }
            requests += JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            handler(request)
        }.build()
        field("api").set(model, DuaApiClient("https://chat.test", client, apiBaseUrl = "https://chat.test"))
        flow<Boolean>("_agentAvailable").value = false
    }

    private fun sse(text: String, delayMs: Long = 0) =
        "data: {\"type\":\"text\",\"delta\":\"$text\",\"delayMs\":$delayMs}\n\ndata: {\"type\":\"done\",\"truncated\":false}\n\n"

    private fun stream(text: String, request: okhttp3.Request, delayMs: Long = 0) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        .code(200).message("OK").header("Content-Type", "text/event-stream")
        .body(sse(text, delayMs).toResponseBody("text/event-stream".toMediaType())).build()

    private fun reply(text: String, state: String = "complete", id: String = "reply") =
        ChatMessage(id = id, text = text, isUser = false, model = "Gemini", state = state)

    /** Seeds one finished turn — exactly the state Retry accepts. */
    private fun seed(original: ChatMessage = reply("Original answer")) {
        flow<List<ChatMessage>>("_messages").value = listOf(prompt, original)
        flow<Boolean>("_isTyping").value = false
    }

    /**
     * Advances Robolectric's paused clock AND yields the JVM thread. Both are required: streaming
     * paces on Main (virtual time) while the SSE body is read on an OkHttp thread (real time).
     */
    private fun await(description: String, condition: () -> Boolean) {
        val until = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            Thread.sleep(2)
        }
        assertTrue("Timed out: $description; requests=${requests.size}, typing=${model.isTyping.value}, " +
            "error=${model.error.value}, messages=${model.messages.value.map { "${it.text}/${it.state}" }}", condition())
    }

    /** Bounded idle for "nothing should have happened" assertions. */
    private fun settle(milliseconds: Long) {
        val until = System.nanoTime() + milliseconds * 1_000_000L
        while (System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            Thread.sleep(2)
        }
    }

    private fun finished() = await("retry finished") { !model.isTyping.value && model.messages.value.lastOrNull()?.streaming != true }

    /** The account API is never used by these tests; refusing loudly keeps a stray call visible. */
    private fun rejectingClient() = OkHttpClient.Builder().addInterceptor { chain ->
        throw AssertionError("Unexpected account request: ${chain.request().url}")
    }.build()

    // ---- 1. normal retry ----

    @Test fun aNormalRetryReplacesTheOldReplyAndSavesHistory() {
        intercept { request -> stream("Retried answer", request) }
        seed()

        model.retryResponse("reply")
        finished()

        assertEquals(listOf("Explain coroutines", "Retried answer"), model.messages.value.map { it.text })
        assertEquals(2, model.messages.value.size)
        val retried = model.messages.value.last()
        assertEquals("reply", retried.id)
        assertEquals("complete", retried.state)
        assertNull(model.error.value)
        assertNull(field("retryOriginal").get(model))
        // The old reply is not resent: only the original prompt goes up, under the reply's own model.
        assertEquals(1, requests.size)
        assertEquals("Gemini", requests[0].getString("model"))
        assertEquals(1, requests[0].getJSONArray("messages").length())
        assertEquals("Explain coroutines", requests[0].getJSONArray("messages").getJSONObject(0).getString("text"))
        // History reached disk, not just the in-memory list.
        model.flushHistory()
        val saved = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0)
            .getString("history", null)!!).second.single()
        assertEquals(listOf("Explain coroutines", "Retried answer"), saved.messages.map { it.text })
        assertEquals(listOf("complete", "complete"), saved.messages.map { it.state })
    }

    // ---- 2. Stop in the middle of a retry ----

    @Test fun stoppingARetryBeforeTextRestoresTheOriginalReply() {
        // A 30 s server-side delay keeps the text chunk away for the whole test, so this needs no
        // clock advance, no sleep and no latch: everything asserted below happens synchronously.
        intercept { request -> stream("Too late", request, delayMs = 30_000) }
        val original = reply("Original answer")
        seed(original)

        // retryResponse sets the streaming turn synchronously, before the request coroutine runs.
        model.retryResponse("reply")
        assertEquals(ChatMessage.STREAMING, model.messages.value.last().state)
        assertTrue(model.isTyping.value)

        model.stopReply()

        // No text had arrived, so the turn is exactly what it was before the retry.
        assertEquals(listOf(prompt, original), model.messages.value)
        assertEquals("Response stopped.", model.error.value)
        assertNull(field("retryOriginal").get(model))
        assertFalse(model.isTyping.value)
    }

    // ---- 3. retry fails before any text ----

    @Test fun aRetryThatFailsBeforeTextRestoresTheOriginalAndDoesNotQueue() {
        intercept { request -> Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(500).message("Server Error")
            .header("Content-Type", "application/json")
            .body("{\"error\":\"Provider down\"}".toResponseBody("application/json".toMediaType())).build() }
        val original = reply("Original answer")
        seed(original)

        model.retryResponse("reply")
        finished()

        assertEquals(listOf(prompt, original), model.messages.value)
        assertEquals("Retry failed. Original response kept.", model.error.value)
        assertNull(field("retryOriginal").get(model))
        assertFalse(model.isTyping.value)
        // Documents today's behaviour: only sendMessage queues offline; Retry does not.
        assertFalse((field("offlineRetry").get(model) as OfflineRetry).hasPending)
    }

    // ---- 4. a Build reply retries in Build mode, against the same repository ----

    @Test fun aBuildReplyRetriesInBuildModeAgainstTheSameRepository() {
        val repository = "KRFbrothers/Dua-ai"
        intercept { request -> stream("Repository answer", request) }
        seed(reply("Old repository answer").copy(build = BuildInfo(repository)))

        model.retryResponse("reply")
        finished()

        assertEquals(1, requests.size)
        assertEquals("build", requests[0].getString("mode"))
        assertEquals(repository, requests[0].getString("repository"))
        val retried = model.messages.value.last()
        assertEquals("Repository answer", retried.text)
        assertEquals(repository, retried.build?.repository)
        assertNull(model.error.value)
    }

    // ---- 5. a retry left stale by a chat switch writes nothing ----

    @Test fun aRetryThatFinishesAfterTheChatWasLeftWritesNothing() {
        // Same 30 s server-side delay: the stale response cannot arrive mid-test, so no thread is
        // blocked and nothing depends on wall-clock timing.
        intercept { request -> stream("Stale answer", request, delayMs = 30_000) }
        seed()
        val startedIn = model.activeChatId.value
        val generationBefore = field("generation").getInt(model)

        model.retryResponse("reply")
        assertEquals(ChatMessage.STREAMING, model.messages.value.last().state)

        model.startNewChat() // cancels the retry job and bumps the generation
        assertFalse("the retry must not survive the switch", model.isTyping.value)
        assertTrue(model.messages.value.isEmpty())
        assertTrue("startNewChat must bump the generation", field("generation").getInt(model) > generationBefore)
        assertFalse("the new chat must stay a fresh chat", model.activeChatId.value == startedIn)
        assertTrue("no turn from the old chat may leak", model.messages.value.none { it.text.contains("Stale") })
    }
}

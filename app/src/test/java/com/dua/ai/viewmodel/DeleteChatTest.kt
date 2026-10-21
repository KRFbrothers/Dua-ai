package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.SessionPersistence
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ChatSummary
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
 * deleteChat (ChatHistory.kt) through the real ViewModel, the real save path and the real
 * SharedPreferences-backed history cache — same harness as RetryResponseTest / PersonalityPromptingTest.
 *
 * Covered: the active chat really disappears (memory, caches AND disk), deleting twice, deleting
 * while a retry streams (job cancelled, typing and retryOriginal reset), a cancelled stream never
 * writing again, an unknown id, and deleting a background chat.
 *
 * Fully synchronous: the paused looper is never pumped inside a test body, so nothing a cancelled
 * job dispatched can run between two assertions. That is the lesson of the RetryResponseTest hang
 * (a latch awaited inside an OkHttp interceptor, on Main) — there is no wait here to wedge on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeleteChatTest {
    // Deliberately no JUnit `Timeout` rule: it moves the body to another thread while Robolectric
    // binds the main Looper to the runner thread, and `idleFor()` from a foreign thread wedges
    // instead of failing. Nothing below waits, so there is nothing for it to rescue.
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var client: OkHttpClient
    private val store = ViewModelStore()
    /** The account stack from [setup]; held so [teardown] can shut down the pool it opened. */
    private var accountClient: OkHttpClient? = null

    private val prompt = ChatMessage(id = "prompt", text = "Explain coroutines", isUser = true, model = "Gemini")
    private val reply = ChatMessage(id = REPLY, text = "Original answer", isUser = false, model = "Gemini", state = "complete")
    private val activeChat = SavedChat(id = ACTIVE, model = "Gemini", draft = "Half-typed follow-up",
        messages = listOf(prompt, reply), updatedAt = 1_000L)
    private val otherChat = SavedChat(id = OTHER, model = "Gemini", draft = "",
        messages = listOf(
            ChatMessage(id = "other-prompt", text = "Another question", isUser = true, model = "Gemini"),
            ChatMessage(id = "other-reply", text = "Another answer", isUser = false, model = "Gemini")),
        updatedAt = 2_000L)

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
        // Same account wiring as PersonalityPromptingTest: signed out, and any stray account call is
        // a test bug rather than a quiet network hop.
        field("account").set(model, AccountRepository(AccountApi("https://account.test", rejectingClient()), object : SessionPersistence {
            override fun read(): AccountSession? = null
            override fun write(value: AccountSession?) = Unit
        }))
        // `appearance` starts from the store's initial value (the reset above), so marking it loaded
        // here is exactly the state "loaded with defaults" — no polling, no wait. retryResponse needs
        // this flag (test 3), and nothing read here depends on a later appearance emission.
        field("appearanceLoaded").set(model, true)
        intercept()
    }

    /**
     * Stops what this test started. Deliberately does NOT call [ViewModelStore.clear]: `onCleared()`
     * touches the drive rebinder, the backup delegate, the recorder and every poll job on the paused
     * Looper. No assertion depends on it, and one blocked step there would hang the Gradle worker
     * with no test-level failure to report.
     */
    @After fun teardown() {
        runCatching { (field("replyJob").get(model) as? Job)?.cancel() }
        runCatching { model.viewModelScope.cancel() }
        runCatching { repeat(25) { shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.MILLISECONDS) } }
        if (::client.isInitialized) closeHttp(client)
        accountClient?.let(::closeHttp)
    }

    /** Shuts an OkHttp stack down and waits (bounded) so no pool thread outlives this test. */
    private fun closeHttp(http: OkHttpClient) {
        runCatching {
            http.dispatcher.cancelAll()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
            http.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    // ---- harness ----

    /**
     * Answers any /api/chat request with one complete SSE turn. It never blocks, so a retry that
     * somehow survived its cancellation finishes on that thread instead of parking a pool thread.
     */
    private fun intercept() {
        client = OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request().url.host == "chat.test") { "Unexpected test HTTP request: ${chain.request().url}" }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "text/event-stream")
                .body("data: {\"type\":\"text\",\"delta\":\"Late answer\"}\n\ndata: {\"type\":\"done\",\"truncated\":false}\n\n"
                    .toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        field("api").set(model, DuaApiClient("https://chat.test", client, apiBaseUrl = "https://chat.test"))
        flow<Boolean>("_agentAvailable").value = false
    }

    /** The account API is never used here; refusing loudly keeps a stray call visible. */
    private fun rejectingClient() = OkHttpClient.Builder().addInterceptor { chain ->
        throw AssertionError("Unexpected account request: ${chain.request().url}")
    }.build().also { accountClient = it }

    /**
     * Seeds two saved chats with [ACTIVE] open, then writes them through the real save path, so every
     * test starts from a state that is already on disk. The non-default toggles are set before the
     * save, so their disappearance after a delete is a real assertion.
     */
    private fun seed() {
        flow<String>("_activeChatId").value = ACTIVE
        flow<List<SavedChat>>("_savedChats").value = listOf(activeChat, otherChat)
        flow<List<ChatMessage>>("_messages").value = activeChat.messages
        flow<String>("_draft").value = activeChat.draft
        flow<ChatSummary?>("_summary").value = ChatSummary("Earlier summary", REPLY)
        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_xSearch").value = true
        flow<String?>("_buildRepo").value = "KRFbrothers/Dua-ai"
        ChatViewModel::class.java.getDeclaredMethod("saveHistory").apply { isAccessible = true }.invoke(model)
        model.flushHistory()
    }

    /** Writes anything queued and returns the exact stored history string. */
    private fun rawHistory(): String {
        model.flushHistory()
        return application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
    }

    private fun savedOnDisk(): List<SavedChat> = ChatHistoryCodec.decode(rawHistory()).second

    // ---- 1. the active chat is really gone ----

    @Test fun deletingTheActiveChatClearsMessagesDraftCachesAndDisk() {
        seed()
        assertEquals("the seed must reach disk before deleting", listOf(ACTIVE, OTHER), savedOnDisk().map { it.id })

        model.deleteChat(ACTIVE)

        assertTrue("no turn may survive the delete", model.messages.value.isEmpty())
        assertEquals("", model.draft.value)
        assertNull("the long-context summary must be dropped with the chat", flow<ChatSummary?>("_summary").value)
        assertNull(flow<String?>("_webMode").value)
        assertNull(flow<String?>("_buildRepo").value)
        assertFalse(flow<Boolean>("_xSearch").value)
        assertEquals("only the other chat may remain", listOf(OTHER), model.savedChats.value.map { it.id })
        assertFalse("the deleted chat must not stay active", model.activeChatId.value == ACTIVE)
        val onDisk = savedOnDisk()
        assertEquals(listOf(OTHER), onDisk.map { it.id })
        assertEquals(otherChat.messages, onDisk.single().messages)
    }

    // ---- 2. deleting twice is a no-op the second time ----

    @Test fun deletingTheSameChatTwiceChangesNothingTheSecondTime() {
        seed()
        model.deleteChat(ACTIVE)
        val savedAfterFirst = model.savedChats.value
        val diskAfterFirst = rawHistory()
        val activeAfterFirst = model.activeChatId.value

        model.deleteChat(ACTIVE)

        assertEquals("a second delete must not rewrite the stored history", diskAfterFirst, rawHistory())
        assertEquals(savedAfterFirst, model.savedChats.value)
        assertEquals("a second delete must not rotate the active chat again", activeAfterFirst, model.activeChatId.value)
        assertTrue(model.messages.value.isEmpty())
        assertEquals(listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 3. deleting while a retry streams cancels it ----

    @Test fun deletingTheActiveChatWhileARetryStreamsCancelsItAndResetsTypingAndRetryOriginal() {
        seed()
        // retryResponse sets the streaming turn synchronously, before its coroutine is dispatched,
        // so this state is observed without pumping the paused looper.
        model.retryResponse(REPLY)
        assertEquals("the retry must be streaming", ChatMessage.STREAMING, model.messages.value.last().state)
        assertTrue("the retry must be marked as typing", model.isTyping.value)
        val running = field("replyJob").get(model) as Job
        val generationBefore = field("generation").getInt(model)

        model.deleteChat(ACTIVE)

        assertFalse("the streaming retry must be cancelled, not orphaned", running.isActive)
        assertNull("no reply job may outlive the chat it wrote into", field("replyJob").get(model))
        assertNull("retryOriginal must be cleared with the chat", field("retryOriginal").get(model))
        assertFalse("typing must not survive the delete", model.isTyping.value)
        assertTrue("deleting a streaming chat must bump the generation", field("generation").getInt(model) > generationBefore)
        assertTrue("the deleted chat must be empty", model.messages.value.isEmpty())
    }

    // ---- 4. a cancelled stream cannot write after the delete ----

    @Test fun aCancelledStreamFromADeletedChatNeverWritesAgain() {
        seed()
        model.retryResponse(REPLY)
        model.deleteChat(ACTIVE)

        // Run whatever the cancellation already queued. A resumed continuation would run here; it
        // must find an empty list and leave it empty.
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(25, TimeUnit.MILLISECONDS) }

        assertTrue("a stale turn must not recreate the chat", model.messages.value.isEmpty())
        assertNull("a cancelled retry must not report an error", model.error.value)
        assertFalse(model.isTyping.value)
        assertNull(field("retryOriginal").get(model))
        assertEquals(listOf(OTHER), model.savedChats.value.map { it.id })
        assertEquals(listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 5. an unknown id is safe ----

    @Test fun deletingAChatThatDoesNotExistIsSafeAndChangesNothing() {
        seed()
        val diskBefore = rawHistory()
        val savedBefore = model.savedChats.value
        val activeBefore = model.activeChatId.value
        val messagesBefore = model.messages.value

        model.deleteChat("chat-that-never-existed")

        assertEquals(diskBefore, rawHistory())
        assertEquals(savedBefore, model.savedChats.value)
        assertEquals(activeBefore, model.activeChatId.value)
        assertEquals(messagesBefore, model.messages.value)
        assertEquals("an unknown delete must not touch the open draft", "Half-typed follow-up", model.draft.value)
    }

    // ---- 6. deleting another chat keeps the open one intact ----

    @Test fun deletingABackgroundChatKeepsTheActiveConversationAndItsDraft() {
        seed()

        model.deleteChat(OTHER)

        assertEquals("the open conversation must be untouched", listOf(prompt, reply), model.messages.value)
        assertEquals("the open draft must be untouched", "Half-typed follow-up", model.draft.value)
        assertEquals(ACTIVE, model.activeChatId.value)
        assertEquals("only the other chat is removed", listOf(ACTIVE), savedOnDisk().map { it.id })
        assertEquals(activeChat.messages, savedOnDisk().single().messages)
    }

    private companion object {
        const val ACTIVE = "chat-active"
        const val OTHER = "chat-other"
        const val REPLY = "reply"
    }
}

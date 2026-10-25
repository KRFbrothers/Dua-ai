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
import com.dua.ai.history.Debouncer
import com.dua.ai.history.SavedChat
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.IntegrationState
import java.io.File
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
 * The cleanup half of `ChatHistory.deleteChat` — the side effects [DeleteChatTest] does not reach:
 * the `_dataBusy` guard, the debounced draft flush, poll-job cancellation, the per-reply metadata
 * maps, the media-cache sharing rule, and `deleteServerChat`'s guard while a reply streams.
 *
 * Same harness as [DeleteChatTest] / [RetryResponseTest], and the rule that came out of the
 * RetryResponseTest hang: **nothing in a test body waits**. No wall-clock pause, no latch, no looper
 * pump — the interceptor answers every request with one complete turn and never blocks, and every
 * assertion reads state that the call under test set synchronously. The only bounded wait in this
 * file is the OkHttp pool shutdown in teardown, exactly as in both passing siblings above.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeleteChatCleanupTest {
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
            ChatMessage(id = OTHER_PROMPT, text = "Another question", isUser = true, model = "Gemini"),
            ChatMessage(id = OTHER_REPLY, text = "Another answer", isUser = false, model = "Gemini")),
        updatedAt = 2_000L)

    /** One file referenced by both chats and one only by each, so `forget` has a real choice to make. */
    private val mediaActive = SavedChat(id = ACTIVE, model = "Gemini", draft = "",
        messages = listOf(
            ChatMessage(id = "media-prompt", text = "Draw something", isUser = true, model = "Gemini"),
            ChatMessage(id = "media-reply", text = "Here it is", isUser = false, model = "Gemini",
                media = listOf(GeneratedMedia(SHARED_MEDIA, "image/png"), GeneratedMedia(EXCLUSIVE_MEDIA, "image/png")))),
        updatedAt = 1_000L)
    private val mediaOther = SavedChat(id = OTHER, model = "Gemini", draft = "",
        messages = listOf(
            ChatMessage(id = "media-other-prompt", text = "Draw something else", isUser = true, model = "Gemini"),
            ChatMessage(id = "media-other-reply", text = "Here it is too", isUser = false, model = "Gemini",
                media = listOf(GeneratedMedia(SHARED_MEDIA, "image/png"), GeneratedMedia(OTHER_MEDIA, "image/png")))),
        updatedAt = 2_000L)

    private fun field(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> flow(name: String) = field(name).get(model) as MutableStateFlow<T>
    @Suppress("UNCHECKED_CAST") private fun <T> map(name: String) = field(name).get(model) as MutableMap<String, T>
    private fun draftSaver() = field("draftSaver").get(model) as Debouncer

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            application.getSharedPreferences(name, 0).edit().clear().commit()
        runBlocking { AppearanceStore(application).reset() }
        DevicePreferences(application).update(LocalPreferences(memoryEnabled = false))
        model = ChatViewModel(application).also { store.put("chat", it) }
        for (name in listOf("engineCheck", "cloudImagineCheck")) (field(name).get(model) as? Job)?.cancel()
        // Same account wiring as DeleteChatTest: signed out, and any stray account call is a test
        // bug rather than a quiet network hop.
        field("account").set(model, AccountRepository(AccountApi("https://account.test", rejectingClient()), object : SessionPersistence {
            override fun read(): AccountSession? = null
            override fun write(value: AccountSession?) = Unit
        }))
        // `appearance` starts from the store's initial value (reset above), so marking it loaded is
        // exactly "loaded with defaults" — `retryResponse` needs it (test 6), and nothing here waits
        // for a later appearance emission.
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
        runCatching { File(application.cacheDir, "generated-media").deleteRecursively() }
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
     * Seeds [chats] with `chats[0]` open, then writes them through the real save path so every test
     * starts from a state that is already on disk. The non-default toggles are set before the save,
     * so their disappearance after a delete would be a real assertion.
     */
    private fun seedWith(chats: List<SavedChat>) {
        val open = chats.first()
        flow<String>("_activeChatId").value = open.id
        flow<List<SavedChat>>("_savedChats").value = chats
        flow<List<ChatMessage>>("_messages").value = open.messages
        flow<String>("_draft").value = open.draft
        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_xSearch").value = true
        ChatViewModel::class.java.getDeclaredMethod("saveHistory").apply { isAccessible = true }.invoke(model)
        model.flushHistory()
    }

    private fun seed() = seedWith(listOf(activeChat, otherChat))

    /** Writes anything queued and returns the exact stored history string. */
    private fun rawHistory(): String {
        model.flushHistory()
        return application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
    }

    private fun savedOnDisk(): List<SavedChat> = ChatHistoryCodec.decode(rawHistory()).second

    // ---- 1. the _dataBusy guard really guards ----

    @Test fun deletingWhileDataIsBusyChangesNothingAndWorksAgainOnceTheBusyFlagClears() {
        seed()
        val diskBefore = rawHistory()
        val savedBefore = model.savedChats.value
        val activeBefore = model.activeChatId.value
        val messagesBefore = model.messages.value
        // A sync or clear-alls is in flight: _dataBusy is the flag every other data action sets.
        flow<Boolean>("_dataBusy").value = true

        model.deleteChat(ACTIVE)

        assertEquals("a delete during a data action must not rewrite the cache", diskBefore, rawHistory())
        assertEquals(savedBefore, model.savedChats.value)
        assertEquals("a refused delete must not rotate the open chat", activeBefore, model.activeChatId.value)
        assertEquals(messagesBefore, model.messages.value)

        // Proves the guard was the only reason nothing happened, not a broken seed.
        flow<Boolean>("_dataBusy").value = false
        model.deleteChat(ACTIVE)
        assertEquals("once the data action finishes the same delete must work", listOf(OTHER), savedOnDisk().map { it.id })
        assertTrue(model.messages.value.isEmpty())
    }

    // ---- 2. a queued draft is written before the chat list changes ----

    @Test fun deletingAnyChatFlushesThePendingTypedDraftToDiskFirst() {
        seed()
        model.updateDraft("Half-typed follow-up, still unsaved")
        assertTrue("typing must queue a debounced save, not write it", draftSaver().pending)

        model.deleteChat(OTHER)

        assertFalse("the delete must flush whatever the debounce was holding", draftSaver().pending)
        assertEquals("the queued draft must be on disk, not lost with the delete",
            "Half-typed follow-up, still unsaved", savedOnDisk().single { it.id == ACTIVE }.draft)
        assertEquals(listOf(ACTIVE), savedOnDisk().map { it.id })
    }

    // ---- 3. poll jobs of the deleted chat are cancelled, nobody else's ----

    @Test fun deletingTheActiveChatCancelsItsPollJobsAndLeavesTheSurvivingChatsPollsAlone() {
        seed()
        val polls = map<Job>("pollJobs")
        val promptPoll = Job()
        val replyPoll = Job()
        val otherPoll = Job()
        polls["prompt"] = promptPoll
        polls["reply"] = replyPoll
        polls[OTHER_PROMPT] = otherPoll

        model.deleteChat(ACTIVE)

        assertFalse("the deleted chat's prompt poll must be cancelled", promptPoll.isActive)
        assertFalse("the deleted chat's reply poll must be cancelled", replyPoll.isActive)
        assertTrue("another chat's poll must keep running", otherPoll.isActive)
        assertEquals("no poll may be kept for a deleted message",
            listOf(OTHER_PROMPT), polls.keys.toList())
    }

    // ---- 4. only the deleted chat's per-reply metadata is dropped ----

    @Test fun deletingTheActiveChatDropsOnlyItsPerReplyMetadata() {
        seed()
        val modes = map<String?>("replyWebModes")
        val reasoning = map<Boolean>("replyReasoning")
        val agent = map<Boolean>("replyAgent")
        val code = map<Boolean>("replyCode")
        val build = map<String>("replyBuild")
        modes[REPLY] = "search"; reasoning[REPLY] = true; agent[REPLY] = true; code[REPLY] = true; build[REPLY] = REPO
        modes[OTHER_REPLY] = null; reasoning[OTHER_REPLY] = false; agent[OTHER_REPLY] = false
        code[OTHER_REPLY] = true; build[OTHER_REPLY] = OTHER_REPO

        model.deleteChat(ACTIVE)

        assertFalse("the deleted reply's web mode must go", modes.containsKey(REPLY))
        assertFalse("the deleted reply's reasoning flag must go", reasoning.containsKey(REPLY))
        assertFalse("the deleted reply's agent flag must go", agent.containsKey(REPLY))
        assertFalse("the deleted reply's code flag must go", code.containsKey(REPLY))
        assertFalse("the deleted reply's build repository must go", build.containsKey(REPLY))
        // The surviving chat's metadata is what a later retry reads, so it must be untouched.
        assertTrue("the surviving reply's web mode must stay", modes.containsKey(OTHER_REPLY))
        assertEquals(false, reasoning[OTHER_REPLY])
        assertEquals(false, agent[OTHER_REPLY])
        assertEquals(true, code[OTHER_REPLY])
        assertEquals(OTHER_REPO, build[OTHER_REPLY])
    }

    // ---- 5. cached media is shared, so only exclusively-owned files are forgotten ----

    @Test fun deletingTheActiveChatForgetsItsExclusiveMediaButKeepsFilesOtherChatsStillUse() {
        seedWith(listOf(mediaActive, mediaOther))
        assertEquals("the media seed must reach disk before deleting",
            listOf(ACTIVE, OTHER), savedOnDisk().map { it.id })
        val cache = File(application.cacheDir, "generated-media").apply { mkdirs() }
        val shared = File(cache, "shared.png").apply { writeText("shared") }
        val exclusive = File(cache, "only-active.png").apply { writeText("exclusive") }
        val otherOnly = File(cache, "only-other.png").apply { writeText("other") }

        model.deleteChat(ACTIVE)

        assertTrue("a file another chat still references must survive", shared.exists())
        assertTrue("the surviving chat's own file must survive", otherOnly.exists())
        assertFalse("the deleted chat's exclusive file must be forgotten", exclusive.exists())
        assertEquals(listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 6. deleteServerChat refuses while a reply streams instead of cancelling it ----

    @Test fun deletingAServerChatWhileARetryStreamsIsRefusedInsteadOfCancellingTheRetry() {
        seed()
        flow<IntegrationState>("_integrations").value = IntegrationState(chatDeletionEnabled = true)
        // retryResponse sets the streaming turn synchronously, before its coroutine is dispatched.
        model.retryResponse(REPLY)
        assertEquals("the retry must be streaming", ChatMessage.STREAMING, model.messages.value.last().state)
        val running = field("replyJob").get(model) as Job
        val diskBefore = rawHistory()

        model.deleteServerChat(ACTIVE)

        assertEquals("a server delete must refuse, not silently drop the work in flight",
            "Finish the current reply or sync action, then try again.", model.chatDeletion.value.error)
        assertFalse("a refused delete must not report itself as running", model.chatDeletion.value.busy)
        assertEquals("a refused delete must name the chat it refused", ACTIVE, model.chatDeletion.value.id)
        assertTrue("the streaming retry must not be cancelled by a refused delete", running.isActive)
        assertEquals("the reply must still be streaming", ChatMessage.STREAMING, model.messages.value.last().state)
        assertEquals("a refused delete must not touch the cache", diskBefore, rawHistory())
    }

    private companion object {
        const val ACTIVE = "chat-active"
        const val OTHER = "chat-other"
        const val REPLY = "reply"
        const val OTHER_PROMPT = "other-prompt"
        const val OTHER_REPLY = "other-reply"
        const val REPO = "KRFbrothers/Dua-ai"
        const val OTHER_REPO = "KRFbrothers/other"
        const val SHARED_MEDIA = "media/shared.png"
        const val EXCLUSIVE_MEDIA = "media/only-active.png"
        const val OTHER_MEDIA = "media/only-other.png"
    }
}

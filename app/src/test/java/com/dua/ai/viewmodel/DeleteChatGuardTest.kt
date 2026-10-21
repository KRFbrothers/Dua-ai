package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.AccountUser
import com.dua.ai.account.CloudReview
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.SessionPersistence
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.BuildPrUiState
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
import org.junit.Assert.assertNotEquals
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
 * The local half of deletion — `deleteChat` in `ChatHistory.kt` — owns exactly the same buffers as
 * `deleteServerChat`: the message list, the draft, the saved-chat list and the history file. The
 * server half was gated on `syncBlocked()`; this one was gated on `_dataBusy` alone, so a delete
 * could land while the attachment picker was still preparing or reading files, while a cloud review
 * or an import preview was on screen, while a build PR was being created, while the account was busy,
 * or while live voice was starting — and then swap the active chat out from under that flow.
 *
 * What this file pins down, and what it deliberately does NOT:
 *
 * * Every condition of the shared guard is checked one at a time against `deleteChat`, with the
 *   refusal proven side-effect free (memory *and* the stored history string are unchanged).
 * * `_isTyping` is explicitly NOT part of the local guard: deleting the open chat is how a streaming
 *   reply is cancelled, so the guard must stay narrow enough to allow it. [DeleteChatTest] test 3
 *   depends on that, and the last test here keeps both halves honest at once.
 *
 * Determinism contract (same as [DeleteChatTest] / [DeleteChatCleanupTest] /
 * [DeleteServerChatGuardTest]): **nothing in a test body waits.** No wall-clock pause, no latch, no
 * interceptor that blocks, and the paused looper is never pumped inside a test. Every assertion reads
 * state the call under test sets synchronously — a refused delete returns before it touches anything,
 * and an accepted one rotates the active id and writes through the real save path. The account stack
 * is deliberately signed out and rejects any request, so a guard leak can never become a quiet
 * network hop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeleteChatGuardTest {
    // No JUnit `Timeout` rule on purpose: it moves the body to another thread while Robolectric binds
    // the main Looper to the runner thread, and `idleFor()` from a foreign thread wedges instead of
    // failing (the PR #316 hang). Nothing below waits, so there is nothing to rescue.
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var client: OkHttpClient
    private val store = ViewModelStore()
    /** The account stack from [setup]; held so [teardown] can shut down the pool it opened. */
    private var accountClient: OkHttpClient? = null

    private val owner = "11111111-1111-4111-8111-111111111111"
    private val session = AccountSession(
        AccountUser(owner, "owner@example.com", "Owner"), "fake-access", "fake-refresh", Long.MAX_VALUE / 1000)

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
        // Same account wiring as DeleteChatTest: signed in with a token that never needs a refresh,
        // but the transport rejects every request, so the guard tests never reach a real backend.
        accountClient = rejectingClient()
        field("account").set(model, AccountRepository(AccountApi("https://account.test", accountClient!!), object : SessionPersistence {
            override fun read(): AccountSession = session
            override fun write(value: AccountSession?) = Unit
        }))
        field("appearanceLoaded").set(model, true)
        flow<Boolean>("_agentAvailable").value = false
        intercept()
        seed()
    }

    /**
     * Stops what this test started. Deliberately does NOT call [ViewModelStore.clear]: `onCleared()`
     * touches the drive rebinder, the backup delegate, the recorder and every poll job on the paused
     * Looper, and one blocked step there would hang the Gradle worker with no test-level failure.
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
     * Answers any /api/chat request with one complete SSE turn. It never blocks, so a reply that
     * somehow survived its cancellation finishes on that thread instead of parking a pool thread.
     * Only the cancellation test needs this; every guard test returns before any request is built.
     */
    private fun intercept() {
        client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("chat.test", chain.request().url.host)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "text/event-stream")
                .body("data: {\"type\":\"text\",\"delta\":\"Late answer\"}\n\ndata: {\"type\":\"done\",\"truncated\":false}\n\n"
                    .toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        field("api").set(model, DuaApiClient("https://chat.test", client, apiBaseUrl = "https://chat.test"))
    }

    /** The account API is never used here; refusing loudly keeps a stray call visible. */
    private fun rejectingClient() = OkHttpClient.Builder().addInterceptor { chain ->
        throw AssertionError("Unexpected account request: ${chain.request().url}")
    }.build()

    /**
     * Seeds two saved chats with [ACTIVE] open, then writes them through the real save path, so every
     * test starts from a state that is already on disk and "untouched" can be checked byte for byte.
     */
    private fun seed() {
        flow<String>("_activeChatId").value = ACTIVE
        flow<List<SavedChat>>("_savedChats").value = listOf(activeChat, otherChat)
        flow<List<ChatMessage>>("_messages").value = activeChat.messages
        flow<String>("_draft").value = activeChat.draft
        ChatViewModel::class.java.getDeclaredMethod("saveHistory").apply { isAccessible = true }.invoke(model)
        model.flushHistory()
    }

    /** Writes anything queued and returns the exact stored history string. */
    private fun rawHistory(): String {
        model.flushHistory()
        return application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
    }

    private fun savedOnDisk(): List<SavedChat> = ChatHistoryCodec.decode(rawHistory()).second

    /** Sets the live-voice start flag, which lives on the ChatVoice feature, not on the ViewModel. */
    private fun setLiveVoiceStarting(starting: Boolean) {
        model.liveStart // Force the lazy feature to initialise before reaching into it.
        val voice = field("voiceFeature").get(model)!!
        @Suppress("UNCHECKED_CAST")
        val live = voice.javaClass.getDeclaredField("_liveStart").apply { isAccessible = true }
            .get(voice) as MutableStateFlow<LiveStartState>
        live.value = LiveStartState(busy = starting)
    }

    /**
     * The refusal contract, shared by every guard case: the open chat is still the same, nothing was
     * cleared, no history was rewritten, and nothing was removed from the saved list.
     */
    private fun assertRefusedAndUntouched(diskBefore: String) {
        assertEquals("a refused delete must not rewrite the stored history", diskBefore, rawHistory())
        assertEquals("a refused delete must not drop the chat from memory", listOf(ACTIVE, OTHER), model.savedChats.value.map { it.id })
        assertEquals("a refused delete must not rotate the active chat", ACTIVE, model.activeChatId.value)
        assertEquals("a refused delete must not clear an open conversation", listOf(prompt, reply), model.messages.value)
        assertEquals("a refused delete must not clear the open draft", "Half-typed follow-up", model.draft.value)
        assertEquals("a refused delete must not touch disk either", listOf(ACTIVE, OTHER), savedOnDisk().map { it.id })
    }

    // ---- 1. the regression: a pick in flight must hold the local delete ----

    @Test fun aLocalDeleteIsRefusedWhileTheAttachmentPickerIsStillPreparingFiles() {
        val diskBefore = rawHistory()
        flow<Boolean>("_preparingFiles").value = true

        model.deleteChat(ACTIVE)

        // Before the fix this call walked straight past `_dataBusy` and rotated the active chat; the
        // whole refusal contract (disk, saved list, active id, messages, draft) is asserted above.
        assertRefusedAndUntouched(diskBefore)
    }

    // ---- 2. same contract for the reader that runs after the picker returns ----

    @Test fun aLocalDeleteIsRefusedWhileAttachmentsAreStillBeingRead() {
        val diskBefore = rawHistory()
        flow<Boolean>("_readingFiles").value = true

        model.deleteChat(ACTIVE)

        assertRefusedAndUntouched(diskBefore)
    }

    // ---- 3. a cloud review or an import preview owns the screen ----

    @Test fun aLocalDeleteIsRefusedWhileACloudReviewOrAnImportPreviewIsOnScreen() {
        val diskBefore = rawHistory()
        flow<ChatViewModel.CloudPreview?>("_cloudPreview").value = ChatViewModel.CloudPreview(
            kind = "backup", userId = owner, review = CloudReview(revision = 1, snapshot = CloudSnapshot(chats = emptyList(), memory = emptyList())))
        model.deleteChat(ACTIVE)
        assertRefusedAndUntouched(diskBefore)

        // Same rule for the import preview: both are a deferred, reviewed write of the very list a
        // local delete would rewrite under them.
        flow<ChatViewModel.CloudPreview?>("_cloudPreview").value = null
        flow<List<SavedChat>?>("_importPreview").value = listOf(activeChat, otherChat)
        model.deleteChat(ACTIVE)
        assertRefusedAndUntouched(diskBefore)
    }

    // ---- 4. a build PR is being created for a reply in this chat ----

    @Test fun aLocalDeleteIsRefusedWhileABuildPullRequestIsBeingCreated() {
        val diskBefore = rawHistory()
        flow<Map<String, BuildPrUiState>>("_buildPr").value = mapOf(REPLY to BuildPrUiState(creating = true))

        model.deleteChat(ACTIVE)

        assertRefusedAndUntouched(diskBefore)

        // A build card that is only *showing* an error is not a live action and must not block.
        flow<Map<String, BuildPrUiState>>("_buildPr").value = mapOf(REPLY to BuildPrUiState(creating = false, error = "PR closed"))
        model.deleteChat(OTHER)
        assertEquals("a finished build card must not hold the delete", listOf(ACTIVE), model.savedChats.value.map { it.id })
    }

    // ---- 5. the account is mid-action ----

    @Test fun aLocalDeleteIsRefusedWhileTheAccountIsBusy() {
        val diskBefore = rawHistory()
        model.account.busy(true)

        model.deleteChat(ACTIVE)

        assertRefusedAndUntouched(diskBefore)

        // Clearing the flag is enough to proceed, so the refusal above was the account and not luck.
        model.account.busy(false)
        model.deleteChat(ACTIVE)
        assertNotEquals("with no blocker left the delete must go through", ACTIVE, model.activeChatId.value)
        assertTrue("an accepted delete clears the open conversation", model.messages.value.isEmpty())
        assertEquals(listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 6. live voice is starting ----

    @Test fun aLocalDeleteIsRefusedWhileLiveVoiceIsStarting() {
        val diskBefore = rawHistory()
        setLiveVoiceStarting(true)

        model.deleteChat(ACTIVE)

        assertRefusedAndUntouched(diskBefore)

        setLiveVoiceStarting(false)
        model.deleteChat(ACTIVE)
        assertNotEquals(ACTIVE, model.activeChatId.value)
        assertEquals(listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 7. refusal is free of side effects, and the guard is live rather than sticky ----

    @Test fun aRefusedLocalDeleteProceedsOnceThePickerFinishesAndTheDiskIsWrittenThen() {
        val diskBefore = rawHistory()
        flow<Boolean>("_preparingFiles").value = true
        model.deleteChat(OTHER)
        assertRefusedAndUntouched(diskBefore)

        // The picker's `finally` clears the flag; the very next call must be accepted. Half of this is
        // the proof that the flag caused the refusal, the other half that nothing was left half-done.
        flow<Boolean>("_preparingFiles").value = false
        model.deleteChat(OTHER)

        assertEquals("only the other chat is removed", listOf(ACTIVE), model.savedChats.value.map { it.id })
        assertEquals(listOf(ACTIVE), savedOnDisk().map { it.id })
        assertEquals("the open chat must survive a background delete", ACTIVE, model.activeChatId.value)
        assertEquals(listOf(prompt, reply), model.messages.value)
        assertEquals("Half-typed follow-up", model.draft.value)
    }

    // ---- 8. several blocking states are still one live refusal ----

    @Test fun severalBlockingStatesTogetherAreStillASingleLiveRefusal() {
        val diskBefore = rawHistory()
        flow<Boolean>("_preparingFiles").value = true
        flow<Boolean>("_readingFiles").value = true
        flow<Boolean>("_dataBusy").value = true

        model.deleteChat(ACTIVE)
        model.deleteChat(ACTIVE)
        assertRefusedAndUntouched(diskBefore)
        assertTrue("a second refused delete must not clear the pre-existing data lock", flow<Boolean>("_dataBusy").value)

        // Clearing only the pre-existing `_dataBusy` must still refuse on the picker flags.
        flow<Boolean>("_dataBusy").value = false
        model.deleteChat(ACTIVE)
        assertRefusedAndUntouched(diskBefore)

        // Clearing both picker flags is what releases it.
        flow<Boolean>("_readingFiles").value = false
        flow<Boolean>("_preparingFiles").value = false
        model.deleteChat(ACTIVE)
        assertNotEquals(ACTIVE, model.activeChatId.value)
        assertEquals(listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 9. the guard stays narrow: a streaming reply is cancelled *by* this delete ----

    @Test fun aStreamingReplyDoesNotBlockTheLocalDeleteBecauseThatIsHowItIsCancelled() {
        model.retryResponse(REPLY)
        assertEquals("the retry must be streaming", ChatMessage.STREAMING, model.messages.value.last().state)
        assertTrue("the retry must be marked as typing", model.isTyping.value)
        val running = field("replyJob").get(model) as Job

        model.deleteChat(ACTIVE)

        // If `_isTyping` ever joined the local guard this delete would be refused and the streaming
        // reply would be orphaned instead of cancelled — exactly the regression DeleteChatTest 3 locks.
        assertFalse("a streaming reply must be cancelled, not block the delete", running.isActive)
        assertTrue("the deleted chat must be empty", model.messages.value.isEmpty())
        assertFalse("typing must not survive the delete", model.isTyping.value)
        assertNull(field("replyJob").get(model))
        assertEquals(listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 10. a background chat is gated too: the picker owns the draft, not just the open chat ----

    @Test fun aBackgroundDeleteIsGatedWhileThePickerOwnsTheDraft() {
        val diskBefore = rawHistory()
        flow<Boolean>("_readingFiles").value = true
        model.deleteChat(OTHER)
        assertRefusedAndUntouched(diskBefore)

        flow<Boolean>("_readingFiles").value = false
        model.deleteChat(OTHER)
        assertEquals(listOf(ACTIVE), model.savedChats.value.map { it.id })
        assertEquals("the open conversation is untouched by a background delete", listOf(prompt, reply), model.messages.value)
    }

    private companion object {
        const val ACTIVE = "chat-active"
        const val OTHER = "chat-other"
        const val REPLY = "reply"
    }
}

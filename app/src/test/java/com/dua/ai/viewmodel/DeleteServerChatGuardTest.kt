package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.AccountUser
import com.dua.ai.account.SessionPersistence
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.IntegrationState
import java.io.IOException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
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
 * The `syncBlocked()` guard that `deleteServerChat` (and every other action that shares it) is
 * supposed to apply while the attachment picker is still working: `_preparingFiles` and
 * `_readingFiles`.
 *
 * Why these two flags matter: `selectAttachments` flips `_preparingFiles` before it launches, and
 * `loadFiles` flips `_readingFiles` before it launches — both then hand the draft, the selection
 * count and the attachment list to a worker. `syncBlocked()` gated the reply (`_isTyping`) but not
 * these two, so a server-chat delete could land in the middle of a pick and swap the active chat
 * under it. `ChatLifecycle` already treats the same pair as a blocking state
 * (`ChatLifecycle.kt:67`), and `ChatViewModel` treats it as a reply-shaped state for autosync and
 * offline resend — the guard was simply incomplete.
 *
 * Determinism contract (same as [DeleteChatTest] / [DeleteChatCleanupTest] / [DeleteChatCleanupTest]):
 * **nothing in a test body waits.** No wall-clock pause, no latch, no interceptor that blocks, and the
 * paused looper is never pumped inside a test. Account HTTP tasks are queued until teardown,
 * so a fast offline response cannot race the accepted-delete assertions. Every assertion reads state that the call under test
 * sets *synchronously, before it touches the network* — the refusal writes `_chatDeletion.error` and
 * returns, and the accepted path writes `_chatDeletion.busy` + `_dataBusy` before `viewModelScope`
 * launches. The account stack is deliberately offline: it fails with a plain `IOException`, so a
 * guard leak shows up as an accepted delete rather than as a silent success, and nothing here can
 * wait on a real backend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeleteServerChatGuardTest {
    // No JUnit `Timeout` rule on purpose: it moves the body to another thread while Robolectric
    // binds the main Looper to the runner thread, and `idleFor()` from a foreign thread wedges
    // instead of failing (the PR #316 hang). Nothing below waits, so there is nothing to rescue.
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var accountClient: OkHttpClient
    private val accountWork = DeferredAccountWork()
    private val store = ViewModelStore()

    private val owner = "11111111-1111-4111-8111-111111111111"
    private val session = AccountSession(
        AccountUser(owner, "owner@example.com", "Owner"), "fake-access", "fake-refresh", Long.MAX_VALUE / 1000)

    private val activeChat = SavedChat(id = ACTIVE, model = "Gemini", draft = "Half-typed follow-up",
        messages = listOf(
            ChatMessage(id = "active-prompt", text = "Explain coroutines", isUser = true, model = "Gemini"),
            ChatMessage(id = "active-reply", text = "Original answer", isUser = false, model = "Gemini", state = "complete")),
        updatedAt = 1_000L)
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

        // Signed in with a token that never needs a refresh, so the guard tests never reach the
        // refresh endpoint; the request itself fails at the transport layer (see `offlineAccount`).
        accountClient = offlineAccount()
        field("account").set(model, AccountRepository(
            AccountApi("https://account.test", accountClient),
            object : SessionPersistence {
                override fun read(): AccountSession = session
                override fun write(value: AccountSession?) = Unit
            }))
        // `retryResponse`-style features read this; marking it loaded means "loaded with defaults".
        field("appearanceLoaded").set(model, true)
        flow<Boolean>("_agentAvailable").value = false
        // Everything `deleteServerChat` checks *after* the guard has to be satisfied, otherwise a
        // refusal here would prove nothing about `syncBlocked()`.
        flow<IntegrationState>("_integrations").value = IntegrationState(
            chatDeletionEnabled = true, chatRenameEnabled = true, chatPinEnabled = true)
        seed()
    }

    /**
     * Stops what this test started. No `ViewModelStore.clear()`: `onCleared()` walks the drive
     * rebinder, backup delegate, recorder and every poll job on the paused Looper, and one blocked
     * step there would hang the Gradle worker with no test-level failure to report.
     */
    @After fun teardown() {
        runCatching { (field("replyJob").get(model) as? Job)?.cancel() }
        runCatching { model.viewModelScope.cancel() }
        // Run only after cancellation: release OkHttp's bookkeeping without a
        // background response racing the assertions or leaving suspended calls.
        accountWork.drain()
        runCatching { repeat(25) { shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.MILLISECONDS) } }
        closeHttp(accountClient)
    }

    /** Shuts the stack down and waits (bounded) so no pool thread outlives this test. */
    private fun closeHttp(http: OkHttpClient) {
        runCatching {
            http.dispatcher.cancelAll()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
            http.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    /**
     * The account backend is never the subject here: it fails on the transport, which
     * `deleteServerChat` catches as an ordinary `Exception`. If a guard ever lets a delete through,
     * the deletion is observably *accepted* (busy flag set) rather than quietly succeeding.
     */
    private fun offlineAccount() = OkHttpClient.Builder().dispatcher(Dispatcher(accountWork)).addInterceptor { _ ->
        throw IOException("account backend is offline in this test")
    }.build()

    /** No threads or waits: accepted requests stay pending until teardown. */
    private class DeferredAccountWork : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) = synchronized(this) {
            if (stopped) throw RejectedExecutionException("Test executor stopped")
            tasks.addLast(command)
        }
        fun drain() {
            while (true) {
                val task = synchronized(this) { tasks.removeFirstOrNull() } ?: return
                task.run()
            }
        }
        override fun shutdown() = synchronized(this) { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> = synchronized(this) {
            stopped = true
            tasks.toMutableList().also { tasks.clear() }
        }
        override fun isShutdown() = synchronized(this) { stopped }
        override fun isTerminated() = synchronized(this) { stopped && tasks.isEmpty() }
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    /** Writes both chats through the real save path, so "untouched" can be checked byte for byte. */
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

    /** What the UI shows for a refused action; the same string every `syncBlocked()` caller uses. */
    private val blockedMessage = "Finish the current reply or sync action, then try again."

    // ---- 1. the regression: a pick in flight must hold the server delete ----

    @Test fun aServerDeleteIsRefusedWhileTheAttachmentPickerIsStillPreparingFiles() {
        flow<Boolean>("_preparingFiles").value = true
        model.deleteServerChat(OTHER)

        // Before the fix this call walked straight past the guard and set busy = true.
        assertEquals(blockedMessage, model.chatDeletion.value.error)
        assertFalse("a refused delete must not enter its busy state", model.chatDeletion.value.busy)
        assertFalse("a refused delete must not hold the data lock", flow<Boolean>("_dataBusy").value)
        assertEquals("a refused delete must name the chat it was asked about", OTHER, model.chatDeletion.value.id)
        assertFalse("a refused delete must not report success", model.chatDeletion.value.completed)
    }

    // ---- 2. same contract for the reader that runs after the picker returns ----

    @Test fun aServerDeleteIsRefusedWhileAttachmentsAreStillBeingRead() {
        flow<Boolean>("_readingFiles").value = true
        model.deleteServerChat(OTHER)

        assertEquals(blockedMessage, model.chatDeletion.value.error)
        assertFalse(model.chatDeletion.value.busy)
        assertFalse(flow<Boolean>("_dataBusy").value)
    }

    // ---- 3. refusal is free of side effects, and the guard is live rather than sticky ----

    @Test fun aRefusedServerDeleteLeavesTheChatsOnDiskUntouchedAndProceedsOnceThePickFinishes() {
        val diskBefore = rawHistory()
        val savedBefore = model.savedChats.value
        val messagesBefore = model.messages.value

        flow<Boolean>("_preparingFiles").value = true
        model.deleteServerChat(OTHER)

        // Nothing was written, cleared or cancelled by the refusal.
        assertEquals(diskBefore, rawHistory())
        assertEquals(savedBefore, model.savedChats.value)
        assertEquals(messagesBefore, model.messages.value)
        assertEquals(ACTIVE, flow<String>("_activeChatId").value)
        assertEquals(2, model.savedChats.value.size)

        // The picker's `finally` clears the flag; the very next call must be accepted. This is the
        // half that proves the refusal above was caused by the flag and not by some other guard.
        flow<Boolean>("_preparingFiles").value = false
        model.deleteServerChat(OTHER)

        assertNull("once the pick finished the guard must not refuse", model.chatDeletion.value.error)
        assertTrue("an accepted delete enters its busy state", model.chatDeletion.value.busy)
        assertTrue("an accepted delete holds the data lock", flow<Boolean>("_dataBusy").value)
        assertEquals(OTHER, model.chatDeletion.value.id)
    }

    // ---- 4. the two flags together are one blocking state, not two ----

    @Test fun preparationAndReadingTogetherAreStillASingleLiveRefusal() {
        flow<Boolean>("_preparingFiles").value = true
        flow<Boolean>("_readingFiles").value = true

        model.deleteServerChat(OTHER)
        assertEquals(blockedMessage, model.chatDeletion.value.error)
        assertFalse(model.chatDeletion.value.busy)

        // A second attempt while still picking must not double-fail or sneak through.
        model.deleteServerChat(OTHER)
        assertEquals(blockedMessage, model.chatDeletion.value.error)
        assertFalse(model.chatDeletion.value.busy)
        assertFalse(flow<Boolean>("_dataBusy").value)
        assertEquals(2, savedOnDisk().size)

        // Clearing one of the two is enough to be blocking, clearing both is enough to proceed.
        flow<Boolean>("_readingFiles").value = false
        model.deleteServerChat(OTHER)
        assertEquals("one live flag must still refuse on its own", blockedMessage, model.chatDeletion.value.error)
        assertFalse(model.chatDeletion.value.busy)

        flow<Boolean>("_preparingFiles").value = false
        model.deleteServerChat(OTHER)
        assertNull(model.chatDeletion.value.error)
        assertTrue(model.chatDeletion.value.busy)
    }

    // ---- 5. the fix is on the shared guard, so the other callers move with it ----

    @Test fun renamingAndPinningWaitForFilePreparationTooBecauseTheyShareTheGuard() {
        flow<Boolean>("_readingFiles").value = true
        assertEquals(blockedMessage, model.renameChat(OTHER, "Fresh title"))
        assertEquals(blockedMessage, model.setChatPinned(OTHER, true))

        flow<Boolean>("_readingFiles").value = false
        flow<Boolean>("_preparingFiles").value = true
        assertEquals(blockedMessage, model.renameChat(OTHER, "Fresh title"))
        assertEquals(blockedMessage, model.setChatPinned(OTHER, true))
        // A refusal must not have written the title or the pin.
        assertNull(model.savedChats.value.first { it.id == OTHER }.customTitle)
        assertFalse(model.savedChats.value.first { it.id == OTHER }.pinned)

        // With both flags clear the same calls are accepted, so the message above was the guard.
        flow<Boolean>("_preparingFiles").value = false
        assertNull(model.renameChat(OTHER, "Fresh title"))
        assertNull(model.setChatPinned(OTHER, true))
        assertEquals("Fresh title", model.savedChats.value.first { it.id == OTHER }.customTitle)
        assertTrue(model.savedChats.value.first { it.id == OTHER }.pinned)
    }

    private companion object {
        const val ACTIVE = "active-chat"
        const val OTHER = "other-chat"
    }
}

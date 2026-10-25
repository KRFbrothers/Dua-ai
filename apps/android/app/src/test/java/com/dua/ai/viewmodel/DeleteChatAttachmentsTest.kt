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
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.Debouncer
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
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
import org.junit.Assert.assertNotEquals
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
 * The composer is part of the conversation it belongs to. `startNewChat()` and `clearAllChats()`
 * both call `clearAttachments()`; `deleteChat()` did not, so deleting the open chat left its draft
 * files, its pending uploads, its attachment error and its selection counter behind — and the next
 * conversation inherited them.
 *
 * What this file pins down:
 *
 * * Deleting the **open** chat clears the composer: `_attachments`, `_uploads`, `_attachmentError`,
 *   `_readingFiles` and the selection counter all reset, exactly as `startNewChat()` does.
 * * Deleting a **background** chat does not: the composer belongs to the chat that is still open.
 * * The pending draft is flushed before the chat goes, so nothing typed is lost silently and no
 *   queued save can resurrect the deleted chat afterwards.
 * * The media cache still forgets only the deleted chat's exclusive files.
 * * A refused delete (the shared stored-state guard) leaves the composer untouched.
 *
 * Determinism contract (same as [DeleteChatTest] / [DeleteChatCleanupTest] /
 * [DeleteChatGuardTest] / [DeleteServerChatGuardTest]): **nothing in a test body waits.** No
 * wall-clock pause, no latch, no interceptor that blocks, and the paused looper is never pumped
 * inside a test. Every assertion reads state the call under test sets synchronously. The account
 * stack is signed in with a token that never refreshes, but its transport rejects every request, so
 * a stray network hop fails loudly instead of quietly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeleteChatAttachmentsTest {
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

    private val exclusive = FileRef("$owner/exclusive.png", "exclusive.png", "image/png", 10)
    private val shared = FileRef("$owner/shared.png", "shared.png", "image/png", 10)

    private val prompt = ChatMessage(id = "prompt", text = "Explain coroutines", isUser = true, model = "Gemini",
        files = listOf(exclusive))
    private val reply = ChatMessage(id = REPLY, text = "Original answer", isUser = false, model = "Gemini", state = "complete")
    private val activeChat = SavedChat(id = ACTIVE, model = "Gemini", draft = "Half-typed follow-up",
        messages = listOf(prompt, reply), updatedAt = 1_000L)
    private val otherChat = SavedChat(id = OTHER, model = "Gemini", draft = "",
        messages = listOf(
            ChatMessage(id = "other-prompt", text = "Another question", isUser = true, model = "Gemini",
                files = listOf(shared)),
            ChatMessage(id = "other-reply", text = "Another answer", isUser = false, model = "Gemini")),
        updatedAt = 2_000L)

    private val draftFiles = listOf(
        ChatAttachment("notes.txt", "text/plain", "aGk="),
        ChatAttachment("photo.png", "image/png", "aGk="))
    /**
     * A pick that has not finished uploading yet: no [FileRef], so clearing it cannot queue a storage
     * delete. That keeps the test on the composer state and off the file-deletion path.
     */
    private val pendingUpload = PendingUpload("upload-1", "report.pdf", "application/pdf", 100)

    private fun field(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> flow(name: String) = field(name).get(model) as MutableStateFlow<T>
    private fun selectionVersion() = field("selectionVersion").get(model) as Int
    private fun draftSaver() = field("draftSaver").get(model) as Debouncer

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            application.getSharedPreferences(name, 0).edit().clear().commit()
        runBlocking { AppearanceStore(application).reset() }
        DevicePreferences(application).update(LocalPreferences(memoryEnabled = false))
        model = ChatViewModel(application).also { store.put("chat", it) }
        for (name in listOf("engineCheck", "cloudImagineCheck")) (field(name).get(model) as? Job)?.cancel()
        // Same account wiring as DeleteChatTest / DeleteChatGuardTest: signed in with a token that
        // never needs a refresh, but the transport rejects every request, so nothing reaches a backend.
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
     * No test here sends a message; the interceptor exists so a stray request cannot reach the network.
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

    /** Puts a draft file, a pending upload and a stale error in the composer, as a real pick would. */
    private fun seedComposer() {
        flow<List<ChatAttachment>>("_attachments").value = draftFiles
        flow<List<PendingUpload>>("_uploads").value = listOf(pendingUpload)
        flow<String>("_attachmentError").value = "File read nahi hui. Dobara select karein."
        field("selectionVersion").set(model, 7)
    }

    /** Writes anything queued and returns the exact stored history string. */
    private fun rawHistory(): String {
        model.flushHistory()
        return application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
    }

    private fun savedOnDisk(): List<SavedChat> = ChatHistoryCodec.decode(rawHistory()).second

    /** A real file in the media cache, named the way [com.dua.ai.imagine.MediaCache] names it. */
    private fun cacheFile(path: String): File {
        val dir = File(application.cacheDir, "generated-media").apply { mkdirs() }
        return File(dir, path.substringAfter('/')).apply { writeText("bytes") }
    }

    // ---- 1. the regression: the open chat's composer goes with it ----

    @Test fun deletingTheActiveChatClearsTheDraftAttachmentsUploadsAndSelection() {
        seedComposer()
        assertTrue("the seed must set the selection counter", selectionVersion() == 7)

        model.deleteChat(ACTIVE)

        // Before the fix every one of these still held the deleted chat's draft: the next conversation
        // opened with two files attached, a pending upload and a stale error under the composer.
        assertTrue("the deleted chat's draft files must not survive", model.attachments.value.isEmpty())
        assertTrue("the deleted chat's pending uploads must not survive", model.uploads.value.isEmpty())
        assertEquals("the attachment error must be cleared with the files", "", model.attachmentError.value)
        assertFalse("no file read may still be marked as running", model.readingFiles.value)
        assertTrue("the selection counter must be bumped so an in-flight load cannot land",
            selectionVersion() != 7)
        assertTrue("the counter must move forward, not reset", selectionVersion() > 7)
        assertEquals("only the other chat may remain", listOf(OTHER), savedOnDisk().map { it.id })
    }

    // ---- 2. a background delete must not touch the composer that is still on screen ----

    @Test fun deletingABackgroundChatKeepsTheComposersAttachmentsAndSelection() {
        seedComposer()
        val diskBefore = rawHistory()

        model.deleteChat(OTHER)

        assertEquals("the open chat's draft files belong to the open chat", draftFiles, model.attachments.value)
        assertEquals(listOf(pendingUpload), model.uploads.value)
        assertEquals("File read nahi hui. Dobara select karein.", model.attachmentError.value)
        assertTrue("a background delete must not invalidate the open selection", selectionVersion() == 7)
        assertEquals("the open chat must survive", ACTIVE, model.activeChatId.value)
        assertEquals(listOf(prompt, reply), model.messages.value)
        assertEquals("Half-typed follow-up", model.draft.value)
        assertEquals("only the other chat is removed", listOf(ACTIVE), savedOnDisk().map { it.id })
        assertNotEquals("the delete itself must still have happened", diskBefore, rawHistory())
    }

    // ---- 3. the pending draft is flushed, not dropped, and nothing is left queued ----

    @Test fun deletingTheActiveChatFlushesThePendingDraftAndLeavesNothingQueued() {
        model.updateDraft("Half-typed follow-up, edited")
        assertTrue("the draft must be queued before the delete", draftSaver().pending)

        model.deleteChat(ACTIVE)

        // `runNow()` runs the queued save before the chat is dropped, so the typed text is written
        // rather than lost — and no timer is left that could resurrect the deleted chat afterwards.
        assertFalse("a queued draft save must not outlive the chat it belongs to", draftSaver().pending)
        assertEquals("", model.draft.value)
        assertEquals("only the other chat may remain", listOf(OTHER), savedOnDisk().map { it.id })
        assertTrue("the deleted chat must not come back from a late save",
            savedOnDisk().none { it.id == ACTIVE })
    }

    // ---- 4. the media cache still forgets only what the deleted chat owned ----

    @Test fun deletingTheActiveChatForgetsItsCachedMediaButKeepsFilesOtherChatsUse() {
        val exclusiveFile = cacheFile(exclusive.path)
        val sharedFile = cacheFile(shared.path)
        assertTrue(exclusiveFile.exists() && sharedFile.exists())

        model.deleteChat(ACTIVE)

        assertFalse("the deleted chat's exclusive cached file must go", exclusiveFile.exists())
        assertTrue("a file another chat still references must stay", sharedFile.exists())
    }

    // ---- 5. the leak, end to end: the next conversation starts clean ----

    @Test fun aDraftAttachmentFromADeletedChatNeverReachesTheNextChat() {
        seedComposer()
        model.deleteChat(ACTIVE)

        model.startNewChat()

        assertTrue("a new chat must not inherit the deleted chat's files", model.attachments.value.isEmpty())
        assertTrue("a new chat must not inherit the deleted chat's uploads", model.uploads.value.isEmpty())
        assertEquals("", model.attachmentError.value)
        assertEquals("", model.draft.value)
        assertTrue(model.messages.value.isEmpty())
    }

    // ---- 6. a refused delete leaves the composer exactly as it was ----

    @Test fun aRefusedDeleteLeavesTheComposersAttachmentsUntouched() {
        seedComposer()
        val diskBefore = rawHistory()
        flow<Boolean>("_preparingFiles").value = true

        model.deleteChat(ACTIVE)

        assertEquals("a refused delete must not clear the draft files", draftFiles, model.attachments.value)
        assertEquals(listOf(pendingUpload), model.uploads.value)
        assertEquals("File read nahi hui. Dobara select karein.", model.attachmentError.value)
        assertTrue("a refused delete must not invalidate the selection", selectionVersion() == 7)
        assertEquals("a refused delete must not rewrite the stored history", diskBefore, rawHistory())
        assertEquals(listOf(ACTIVE, OTHER), savedOnDisk().map { it.id })
        assertEquals(ACTIVE, model.activeChatId.value)
    }

    private companion object {
        const val ACTIVE = "chat-active"
        const val OTHER = "chat-other"
        const val REPLY = "reply"
    }
}

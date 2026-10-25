package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.CloudReview
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ChatSummary
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.*
import com.dua.ai.ui.chat.BuildPrUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercise automatic sync with the real ViewModel cache port, including subsequent draft writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AutomaticSyncCacheTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var port: AutomaticCloudSync.LocalPort
    private val store = ViewModelStore()
    private val local = SavedChat("active", "Dua", "Cached draft", listOf(
        ChatMessage(id = "phone-message", text = "Phone question", isUser = true)), 123,
        summary = ChatSummary("Phone summary", "phone-message"), buildRepository = "owner/phone")
    private val remote = local.copy(model = "Gemini", draft = "Server draft", updatedAt = 456,
        messages = listOf(ChatMessage(id = "server-message", text = "Server question", isUser = true)),
        summary = ChatSummary("Server summary", "server-message"), buildRepository = "owner/server")

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) {
            application.getSharedPreferences(name, 0).edit().clear().commit()
        }
        application.getSharedPreferences("dua_chat_history", 0).edit()
            .putString("history", ChatHistoryCodec.encode(listOf(local), local.id)).commit()
        DevicePreferences(application).update(LocalPreferences(memoryEnabled = true, memory = listOf("Phone note")))
        model = ChatViewModel(application).also { store.put("chat", it) }
        val sync = ChatViewModel::class.java.getDeclaredField("automaticSync").apply { isAccessible = true }.get(model)
        port = AutomaticCloudSync::class.java.getDeclaredField("local").apply { isAccessible = true }
            .get(sync) as AutomaticCloudSync.LocalPort
    }

    @After fun teardown() { store.clear() }

    private fun persisted() = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0)
        .getString("history", null)!!).second
    @Test fun deletionDownloadClearsTheActiveBuffersAndPersistsItsIdsAcrossDraftSave() {
        port.read()
        port.commit(CloudSnapshot(emptyList(), emptyList(), deletedChatIds = listOf(local.id)))
        assertTrue(model.messages.value.isEmpty()); assertEquals("", model.draft.value)
        assertNotEquals(local.id, model.activeChatId.value)
        model.updateDraft("New chat after deletion"); model.flushHistory()
        assertFalse(persisted().any { it.id == local.id })
        assertEquals(listOf(local.id), port.read().deletedChatIds)
    }

    @Test fun coldStartRestoresBuildRepositoryBeforeAnyDraftSave() {
        assertEquals(local.buildRepository, model.buildRepo.value)
        model.updateDraft("First edit after restart")
        model.flushHistory()
        assertEquals(local.buildRepository, persisted().single().buildRepository)
        assertEquals(local.summary, persisted().single().summary)
    }

    @Test fun automaticCommitRebindsTheOpenChatAndNextDraftSaveKeepsServerContent() {
        port.read()
        port.commit(CloudSnapshot(listOf(remote), listOf("Server note")))
        assertEquals(remote.messages, model.messages.value)
        assertEquals(remote.draft, model.draft.value)
        assertEquals(remote.model, model.selectedModel.value)
        assertEquals(remote.buildRepository, model.buildRepo.value)
        assertEquals(listOf("Server note"), model.localSettings.value.memory)
        model.updateDraft("Edited server draft")
        model.flushHistory()
        assertEquals(remote.copy(draft = "Edited server draft", updatedAt = persisted().single().updatedAt), persisted().single())
    }

    @Test fun completedReplyStaysOpenWhenSyncPreservesItUnderARecoveryId() = runBlocking {
        val owner = "11111111-2222-4333-8444-555555555555"
        val serverChat = local.copy(draft = "Question", messages = emptyList(), summary = null)
        val initial = CloudSnapshot(listOf(serverChat), listOf("Phone note"))
        port.read(); port.commit(initial)
        var record = AutomationSyncRecord(true, owner, 1, AutomationSyncPolicy.baseline(initial))
        var cloud = initial
        var serverRevision = 1L
        var uploads = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val account = object : AutomaticCloudSync.AccountPort {
            override fun currentAccountId() = owner
            override suspend fun load() = AutomaticSyncAccount(true, owner)
            override suspend fun cloud(expectedAccountId: String) = CloudReview(serverRevision, cloud, owner)
            override suspend fun upload(expectedAccountId: String, revision: Long, snapshot: CloudSnapshot): Long {
                assertEquals(this@AutomaticSyncCacheTest.local.id, snapshot.chats.first().id)
                assertEquals(serverChat, snapshot.chats.first())
                assertEquals(serverRevision, revision)
                uploads++; cloud = snapshot; return ++serverRevision
            }
        }
        val records = object : AutomaticCloudSync.StorePort {
            override fun read() = record
            override fun write(value: AutomationSyncRecord) { record = value }
        }
        val sync = AutomaticCloudSync(scope, account, port, records, sleep = { awaitCancellation() })
        try {
            sync.setForeground(true); sync.restore()?.join()
            sync.setBusy(true)
            val messages = listOf(ChatMessage(id = "prompt", text = "Question", isUser = true),
                ChatMessage(id = "reply", text = "Completed answer", isUser = false))
            @Suppress("UNCHECKED_CAST")
            val visible = ChatViewModel::class.java.getDeclaredField("_messages").apply { isAccessible = true }
                .get(model) as MutableStateFlow<List<ChatMessage>>
            visible.value = messages
            model.updateDraft(""); model.flushHistory()
            sync.setBusy(false); sync.syncNow()?.join()

            val copy = model.savedChats.value.single { it.id.startsWith("unsynced-") }
            assertEquals(messages, copy.messages)
            assertEquals(copy.id, model.activeChatId.value)
            assertEquals(messages, model.messages.value)
            assertEquals("", model.draft.value)
            assertEquals(copy.id, ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0)
                .getString("history", null)!!).first)
            assertEquals(serverChat, cloud.chats.single { it.id == serverChat.id })
            assertEquals(1, uploads)

            sync.syncNow()?.join()
            assertEquals(1, uploads)
            assertEquals(2, persisted().size)
            model.updateDraft("Follow-up draft"); model.flushHistory()
            assertEquals(messages, persisted().single { it.id == copy.id }.messages)
            assertEquals("Follow-up draft", persisted().single { it.id == copy.id }.draft)
            assertEquals(serverChat, persisted().single { it.id == serverChat.id })
            store.clear()
            model = ChatViewModel(application).also { store.put("chat", it) }
            assertEquals(copy.id, model.activeChatId.value)
            assertEquals(messages, model.messages.value)
            assertEquals("Follow-up draft", model.draft.value)
        } finally { sync.close(); scope.cancel() }
    }

    @Test fun pendingKeystrokesStayOpenInTheRecoveryCopyWithoutRecreatingCopies() = runBlocking {
        val owner = "11111111-2222-4333-8444-555555555555"
        val initial = CloudSnapshot(listOf(local), listOf("Phone note"))
        var record = AutomationSyncRecord(true, owner, 1, AutomationSyncPolicy.baseline(initial))
        var cloud = CloudSnapshot(listOf(remote), listOf("Server note"))
        var serverRevision = 2L
        var uploads = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val account = object : AutomaticCloudSync.AccountPort {
            override fun currentAccountId() = owner
            override suspend fun load() = AutomaticSyncAccount(true, owner)
            override suspend fun cloud(expectedAccountId: String) = CloudReview(serverRevision, cloud, owner)
            override suspend fun upload(expectedAccountId: String, revision: Long, snapshot: CloudSnapshot): Long {
                assertEquals(this@AutomaticSyncCacheTest.local.id, snapshot.chats.first().id)
                assertEquals(this@AutomaticSyncCacheTest.remote.messages, snapshot.chats.first().messages)
                assertEquals(serverRevision, revision)
                uploads++; cloud = snapshot; return ++serverRevision
            }
        }
        val records = object : AutomaticCloudSync.StorePort {
            override fun read() = record
            override fun write(value: AutomationSyncRecord) { record = value }
        }
        val sync = AutomaticCloudSync(scope, account, port, records, sleep = { awaitCancellation() })
        try {
            model.updateDraft("Just typed; not yet debounced")
            sync.setForeground(true)
            sync.restore()?.join()
            assertEquals("synced", sync.state.value.phase)
            val copy = model.savedChats.value.single { it.id.startsWith("unsynced-") }
            assertEquals("Just typed; not yet debounced", copy.draft)
            assertEquals(local.messages, copy.messages)
            assertEquals(copy.id, model.activeChatId.value)
            assertEquals(copy.messages, model.messages.value)
            assertEquals(copy.draft, model.draft.value)
            assertEquals(1, uploads)
            model.flushHistory()
            sync.syncNow()?.join()
            assertEquals(1, uploads)
            assertEquals(2, persisted().size)
            assertEquals(copy, persisted().single { it.id == copy.id })
            assertEquals(remote, persisted().single { it.id == remote.id })
        } finally { sync.close(); scope.cancel() }
    }

    @Test fun firstEnableKeepsPendingTypingDuringCloudReadAndKeepsItsRecoveryCopyOpen() = runBlocking {
        val owner = "11111111-2222-4333-8444-555555555555"
        var record = AutomationSyncRecord()
        var cloud = CloudSnapshot(listOf(remote), listOf("Server note"))
        var serverRevision = 2L
        var uploads = 0
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val account = object : AutomaticCloudSync.AccountPort {
            override fun currentAccountId() = owner
            override suspend fun load() = AutomaticSyncAccount(true, owner)
            override suspend fun cloud(expectedAccountId: String): CloudReview {
                val reply = CloudReview(serverRevision, cloud, owner)
                gate.await()
                return reply
            }
            override suspend fun upload(expectedAccountId: String, revision: Long, snapshot: CloudSnapshot): Long {
                assertEquals(serverRevision, revision)
                uploads++; cloud = snapshot; return ++serverRevision
            }
        }
        val records = object : AutomaticCloudSync.StorePort {
            override fun read() = record
            override fun write(value: AutomationSyncRecord) { record = value }
        }
        val sync = AutomaticCloudSync(scope, account, port, records, sleep = { awaitCancellation() })
        try {
            sync.setForeground(true)
            val job = sync.enable()
            model.updateDraft("First-sync pending edit")
            gate.complete(Unit); job?.join()
            assertEquals("synced", sync.state.value.phase)
            val copy = persisted().single { it.id.startsWith("unsynced-") }
            assertEquals("First-sync pending edit", copy.draft)
            assertEquals(local.messages, copy.messages)
            assertEquals(local.summary, copy.summary)
            assertEquals(local.buildRepository, copy.buildRepository)
            assertEquals(remote, persisted().single { it.id == remote.id })
            assertEquals(copy.id, model.activeChatId.value)
            assertEquals(copy.messages, model.messages.value)
            assertEquals(copy.draft, model.draft.value)
            assertEquals(listOf("Server note"), model.localSettings.value.memory)
            model.flushHistory(); sync.syncNow()?.join()
            assertEquals(1, uploads); assertEquals(2, persisted().size)
        } finally { sync.close(); scope.cancel() }
    }

    @Test fun draftTypedAfterReadBlocksCommitWithoutChangingHistoryMemoryOrBuffers() {
        port.read()
        val previous = application.getSharedPreferences("dua_chat_history", 0).getString("history", null)
        model.updateDraft("Newer typing")
        try { port.commit(CloudSnapshot(listOf(remote), listOf("Server note"))); fail("Pending edit overwritten") }
        catch (_: IllegalStateException) { }
        assertEquals(previous, application.getSharedPreferences("dua_chat_history", 0).getString("history", null))
        assertEquals("Newer typing", model.draft.value)
        assertEquals(local.messages, model.messages.value)
        assertEquals(listOf("Phone note"), model.localSettings.value.memory)
        assertEquals("Newer typing", port.read().chats.single().draft)
    }

    @Test fun invalidSnapshotCannotReplacePersistedStateOrActiveBuffers() {
        val previous = persisted()
        try { port.commit(CloudSnapshot((0..100).map { remote.copy(id = "remote-$it") }, listOf("Server note"))); fail("Invalid snapshot accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(previous, persisted())
        assertEquals(local.messages, model.messages.value)
        assertEquals(local.draft, model.draft.value)
        assertEquals(listOf("Phone note"), model.localSettings.value.memory)
    }

    @Test fun phoneOnlyActiveChatRemainsOpenWhenAnotherServerChatIsAdded() {
        port.read()
        port.commit(CloudSnapshot(listOf(local, remote.copy(id = "server-only")), emptyList()))
        assertEquals(local.messages, model.messages.value)
        assertEquals(local.draft, model.draft.value)
        assertEquals(local.buildRepository, model.buildRepo.value)
        assertTrue(model.localSettings.value.memory.isEmpty())
    }

    @Test fun recoveryOfAnotherChatDoesNotChangeTheOpenConversation() {
        val other = local.copy(id = "other")
        val recovered = other.copy(id = "unsynced-other")
        port.read()
        port.commit(AutomationMergePlan(CloudSnapshot(listOf(local, other, recovered), emptyList()), false,
            mapOf(other.id to recovered.id)))
        assertEquals(local.id, model.activeChatId.value)
        assertEquals(local.messages, model.messages.value)
        assertEquals(local.draft, model.draft.value)
        assertEquals(local.id, ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0)
            .getString("history", null)!!).first)
    }

    @Test fun pullRequestCreationBlocksBothReadAndCommit() {
        @Suppress("UNCHECKED_CAST")
        val prs = ChatViewModel::class.java.getDeclaredField("_buildPr").apply { isAccessible = true }
            .get(model) as MutableStateFlow<Map<String, BuildPrUiState>>
        prs.value = mapOf("reply" to BuildPrUiState(creating = true))
        try { port.read(); fail("Read during PR creation") } catch (_: IllegalStateException) { }
        try { port.commit(CloudSnapshot(listOf(remote), emptyList())); fail("Commit during PR creation") }
        catch (_: IllegalStateException) { }
        assertEquals(local.messages, model.messages.value)
        assertEquals(listOf(local), persisted())
    }
}

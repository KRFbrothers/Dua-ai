package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ChatSummary
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.lang.reflect.InvocationTargetException

/** Exercise the real cache/memory transaction and subsequent draft save without a live account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class CloudDownloadRecoveryTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val local = SavedChat("active", "Dua", "Phone draft",
        listOf(ChatMessage(id = "phone-message", text = "Phone question", isUser = true)), 123)

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) {
            application.getSharedPreferences(name, 0).edit().clear().commit()
        }
        application.getSharedPreferences("dua_chat_history", 0).edit()
            .putString("history", ChatHistoryCodec.encode(listOf(local), local.id)).commit()
        DevicePreferences(application).update(LocalPreferences(memoryEnabled = true, memory = listOf("Phone note")))
        model = ChatViewModel(application).also { store.put("chat", it) }
    }

    @After fun teardown() { store.clear() }

    private fun download(snapshot: CloudSnapshot) = kotlinx.coroutines.runBlocking {
        kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
            try {
                val result = ChatViewModel::class.java.getDeclaredMethod("downloadCloudSnapshot",
                    CloudSnapshot::class.java, kotlin.coroutines.Continuation::class.java)
                    .apply { isAccessible = true }.invoke(model, snapshot, continuation)
                if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) continuation.resumeWith(Result.success(Unit))
            } catch (wrapped: InvocationTargetException) { continuation.resumeWith(Result.failure(wrapped.targetException)) }
        }
    }

    @Test fun reviewedServerDeletionCannotReturnFromAnActiveDraftSave() {
        download(CloudSnapshot(emptyList(), emptyList(), deletedChatIds = listOf(local.id)))
        assertTrue(model.messages.value.isEmpty()); assertEquals("", model.draft.value)
        assertNotEquals(local.id, model.activeChatId.value)
        model.updateDraft("Fresh draft"); model.flushHistory()
        val cached = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second
        assertFalse(cached.any { it.id == local.id })
    }

    @Test fun serverRestoreRebindsActiveBuffersAndSurvivesTheNextDraftSave() {
        val message = ChatMessage(id = "server-message", text = "Server question", isUser = true, model = "Gemini")
        val remote = local.copy(model = "Gemini", draft = "Server draft", messages = listOf(message),
            updatedAt = 456, summary = ChatSummary("Server summary", message.id), buildRepository = "owner/repo")

        download(CloudSnapshot(listOf(remote), listOf("Server note")))

        assertEquals(local.id, model.activeChatId.value)
        assertEquals(remote.messages, model.messages.value)
        assertEquals(remote.model, model.selectedModel.value)
        assertEquals(remote.draft, model.draft.value)
        assertEquals(listOf("Server note"), model.localSettings.value.memory)
        assertTrue(model.localSettings.value.memoryEnabled)
        val copy = model.savedChats.value.single { it.id.startsWith("unsynced-") }
        assertEquals(local.copy(id = copy.id), copy)

        model.updateDraft("Edited restored draft")
        model.flushHistory()
        val persisted = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0)
            .getString("history", null)!!).second
        val restored = persisted.single { it.id == local.id }
        assertEquals(remote.messages, restored.messages)
        assertEquals(remote.summary, restored.summary)
        assertEquals(remote.buildRepository, restored.buildRepository)
        assertEquals("Edited restored draft", restored.draft)
        assertEquals(copy, persisted.single { it.id == copy.id })
    }

    @Test fun repeatDownloadDoesNotCreateCopiesAndPreservesPhoneOnlyActiveChat() {
        val remote = local.copy(id = "server-only", draft = "Server draft")
        val snapshot = CloudSnapshot(listOf(remote), emptyList())
        download(snapshot)
        download(snapshot)
        assertEquals(listOf(local, remote), model.savedChats.value)
        assertEquals(local.messages, model.messages.value)
        assertEquals(local.draft, model.draft.value)
        assertTrue(model.localSettings.value.memory.isEmpty())
        assertEquals(emptyList<String>(), DevicePreferences(application).state.value.memory)
    }

    @Test fun invalidCombinedSnapshotLeavesPersistedChatsMemoryAndActiveBuffersUntouched() {
        val history = application.getSharedPreferences("dua_chat_history", 0)
        val before = history.getString("history", null)
        val remote = CloudSnapshot((0 until 100).map { local.copy(id = "server-$it") }, listOf("Server note"))
        try { download(remote); fail("Oversized combined snapshot accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, history.getString("history", null))
        assertEquals(listOf(local), model.savedChats.value)
        assertEquals(local.messages, model.messages.value)
        assertEquals(local.draft, model.draft.value)
        assertEquals(listOf("Phone note"), model.localSettings.value.memory)
        assertEquals(listOf("Phone note"), DevicePreferences(application).state.value.memory)
    }

    @Test fun runningReplyBlocksRestoreWithoutChangingChatOrMemory() {
        @Suppress("UNCHECKED_CAST")
        val typing = ChatViewModel::class.java.getDeclaredField("_isTyping").apply { isAccessible = true }
            .get(model) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        typing.value = true
        try { download(CloudSnapshot(listOf(local.copy(draft = "Server draft")), listOf("Server note"))); fail("Active reply overwritten") }
        catch (_: IllegalStateException) { }
        assertEquals(listOf(local), model.savedChats.value)
        assertEquals(local.draft, model.draft.value)
        assertEquals(listOf("Phone note"), model.localSettings.value.memory)
    }
}

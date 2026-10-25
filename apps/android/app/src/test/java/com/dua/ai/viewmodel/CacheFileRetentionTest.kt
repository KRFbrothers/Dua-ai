package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Cached chats are disposable; clearing them must not queue deletion of restorable Storage objects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class CacheFileRetentionTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val folder = "11111111-2222-4333-8444-555555555555/"
    private val upload = FileRef(folder + "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf", "test.pdf", "application/pdf", 10)
    private val image = GeneratedMedia(folder + "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.png", "image/png")
    private val unsent = upload.copy(path = folder + "cccccccc-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf")
    private val active = SavedChat("active", "Dua", "Cached draft", listOf(
        ChatMessage(id = "question", text = "Test file", isUser = true, model = "Dua", files = listOf(upload)),
        ChatMessage(id = "reply", text = "Test image", isUser = false, model = "Dua", media = listOf(image))
    ), 123, buildRepository = "owner/repo")

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) {
            application.getSharedPreferences(name, 0).edit().clear().commit()
        }
        application.getSharedPreferences("dua_chat_history", 0).edit()
            .putString("history", ChatHistoryCodec.encode(listOf(active), active.id)).commit()
        model = ChatViewModel(application).also { store.put("chat", it) }
        model.openChat(active.id)
    }

    @After fun teardown() { store.clear() }

    private fun pendingPaths(): List<String> {
        val raw = application.getSharedPreferences("dua_files", 0).getString("pending_deletes", null) ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun waitForClear() {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (model.dataBusy.value && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertFalse("Cache clear did not finish", model.dataBusy.value)
    }

    @Test fun deletingTheOpenCachedChatNeverQueuesItsStoredUploadOrGeneratedMedia() {
        model.deleteChat(active.id)
        model.flushHistory()
        assertTrue(model.savedChats.value.isEmpty())
        assertTrue(model.messages.value.isEmpty())
        assertTrue(pendingPaths().isEmpty())
    }

    @Test fun deletingAClosedCachedChatPreservesTheOpenChatAndNeverQueuesServerFiles() {
        model.startNewChat()
        model.updateDraft("Keep this open draft")
        model.flushHistory()
        val openId = model.activeChatId.value
        model.deleteChat(active.id)
        model.flushHistory()
        assertEquals(openId, model.activeChatId.value)
        assertEquals("Keep this open draft", model.draft.value)
        assertEquals(listOf(openId), model.savedChats.value.map { it.id })
        assertTrue(pendingPaths().isEmpty())
    }

    @Test fun clearingCachedChatsPersistsAnEmptyCacheWithoutQueueingServerFileDeletes() {
        model.clearAllChats()
        waitForClear()
        model.flushHistory()
        assertTrue(model.savedChats.value.isEmpty())
        assertTrue(model.messages.value.isEmpty())
        assertEquals("", model.draft.value)
        assertTrue(pendingPaths().isEmpty())
        val raw = application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
        assertTrue(ChatHistoryCodec.decode(raw).second.isEmpty())
        @Suppress("UNCHECKED_CAST")
        val repository = ChatViewModel::class.java.getDeclaredField("_buildRepo").apply { isAccessible = true }
            .get(model) as MutableStateFlow<String?>
        assertNull(repository.value)
        assertTrue(model.dataStatus.value.contains("Server chats and their stored files remain"))
    }

    @Test fun clearingTheCacheStillCleansUpAnUnsentTemporaryUploadOnly() {
        @Suppress("UNCHECKED_CAST")
        val uploads = ChatViewModel::class.java.getDeclaredField("_uploads").apply { isAccessible = true }
            .get(model) as MutableStateFlow<List<PendingUpload>>
        uploads.value = listOf(PendingUpload("unsent", unsent.name, unsent.mimeType, unsent.size,
            state = PendingUpload.DONE, ref = unsent))
        model.clearAllChats()
        waitForClear()
        assertEquals(listOf(unsent.path), pendingPaths())
        assertTrue(model.uploads.value.isEmpty())
        assertTrue(model.savedChats.value.isEmpty())
    }
}

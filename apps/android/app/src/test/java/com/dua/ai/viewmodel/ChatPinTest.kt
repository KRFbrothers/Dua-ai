package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.setup.IntegrationState
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatPinTest {
    private lateinit var app: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val active = SavedChat("active", "Dua", "Draft", listOf(ChatMessage("u", "Original question", true)), 1, customTitle = "Notes")
    private val other = active.copy(id = "other", draft = "Other draft", customTitle = "Other")
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            app.getSharedPreferences(name, 0).edit().clear().commit()
        app.getSharedPreferences("dua_chat_history", 0).edit().putString("history", ChatHistoryCodec.encode(listOf(active, other), active.id)).commit()
        model = ChatViewModel(app).also { store.put("chat", it) }
    }
    @After fun teardown() { store.clear() }
    @Suppress("UNCHECKED_CAST") private fun enable() {
        val flow = ChatViewModel::class.java.getDeclaredField("_integrations").apply { isAccessible = true }.get(model) as MutableStateFlow<IntegrationState>
        flow.value = flow.value.copy(chatPinEnabled = true, chatRenameEnabled = true)
    }
    private fun persisted(): List<SavedChat> {
        model.flushHistory()
        return ChatHistoryCodec.decode(app.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second
    }
    @Test fun activePinSurvivesDraftSaveSwitchRenameAndRestartAndUnpinKeepsTheChat() {
        enable(); assertNull(model.setChatPinned(active.id, true))
        assertEquals(active.updatedAt, persisted().first { it.id == active.id }.updatedAt)
        model.updateDraft("Next question"); model.openChat(other.id); model.openChat(active.id)
        assertNull(model.renameChat(active.id, "Renamed notes"))
        val pinned = persisted().first { it.id == active.id }
        assertTrue(pinned.pinned); assertEquals(active.messages, pinned.messages); assertEquals("Next question", pinned.draft)
        store.clear(); model = ChatViewModel(app).also { store.put("chat", it) }; enable()
        assertTrue(persisted().first { it.id == active.id }.pinned)
        assertNull(model.setChatPinned(active.id, false))
        model.updateDraft("After restart")
        val unpinned = persisted().first { it.id == active.id }
        assertFalse(unpinned.pinned); assertEquals("Renamed notes", unpinned.title); assertEquals(active.messages, unpinned.messages)
    }
    @Test fun anotherChatPinFlushesPendingDraftWithoutChangingSelectionOrTimestamps() {
        enable(); model.updateDraft("Pending draft")
        assertNull(model.setChatPinned(other.id, true))
        val chats = persisted()
        assertEquals(active.id, model.activeChatId.value)
        assertEquals("Pending draft", chats.first { it.id == active.id }.draft)
        assertEquals(other.copy(pinned = true), chats.first { it.id == other.id })
        assertNull(model.setChatPinned(other.id, true)); assertEquals(chats, persisted())
    }
    @Test fun oldBackendMissingChatAndBusyActionLeavePinStateUnchanged() {
        assertNotNull(model.setChatPinned(active.id, true)); enable()
        assertNotNull(model.setChatPinned("missing", true))
        @Suppress("UNCHECKED_CAST") val typing = ChatViewModel::class.java.getDeclaredField("_isTyping").apply { isAccessible = true }.get(model) as MutableStateFlow<Boolean>
        typing.value = true
        assertNotNull(model.setChatPinned(active.id, true)); assertEquals(listOf(active, other), persisted())
    }
    @Test fun pinnedDraftChatIsRetainedWhenItsDraftIsCleared() {
        enable(); model.startNewChat(); model.updateDraft("Remember later"); model.flushHistory()
        val id = model.activeChatId.value
        assertNull(model.setChatPinned(id, true)); model.updateDraft("")
        assertTrue(persisted().first { it.id == id }.pinned)
        assertTrue(persisted().first { it.id == id }.messages.isEmpty())
    }
}

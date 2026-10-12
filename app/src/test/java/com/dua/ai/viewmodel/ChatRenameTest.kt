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
class ChatRenameTest {
    private lateinit var app: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val original = SavedChat("active", "Dua", "Draft", listOf(ChatMessage(id = "u", text = "Original question", isUser = true)), 1)
    private val other = original.copy(id = "other", draft = "Other draft")

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            app.getSharedPreferences(name, 0).edit().clear().commit()
        app.getSharedPreferences("dua_chat_history", 0).edit()
            .putString("history", ChatHistoryCodec.encode(listOf(original, other), original.id)).commit()
        model = ChatViewModel(app).also { store.put("chat", it) }
    }
    @After fun teardown() { store.clear() }

    @Suppress("UNCHECKED_CAST")
    private fun enableRename() {
        val flow = ChatViewModel::class.java.getDeclaredField("_integrations").apply { isAccessible = true }.get(model) as MutableStateFlow<IntegrationState>
        flow.value = flow.value.copy(chatRenameEnabled = true)
    }
    private fun persisted(): List<SavedChat> {
        model.flushHistory()
        return ChatHistoryCodec.decode(app.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second
    }

    @Test fun activeRenameSurvivesDraftSaveSwitchAndRestartWithoutRewritingMessages() {
        enableRename()
        assertNull(model.renameChat(original.id, "  Code review  "))
        model.updateDraft("Next question")
        model.openChat(other.id)
        model.openChat(original.id)
        val named = persisted().first { it.id == original.id }
        assertEquals("Code review", named.title)
        assertEquals(original.messages, named.messages)
        assertEquals("Next question", named.draft)
        assertTrue(named.updatedAt > original.updatedAt)
        store.clear()
        model = ChatViewModel(app).also { store.put("chat", it) }
        model.updateDraft("After restart")
        assertEquals("Code review", persisted().first { it.id == original.id }.title)
    }
    @Test fun renamingAnotherChatFlushesActiveDraftAndRetainsSelection() {
        enableRename()
        model.updateDraft("Unsaved typing")
        assertNull(model.renameChat(other.id, "Other name"))
        assertEquals(original.id, model.activeChatId.value)
        val saved = persisted()
        assertEquals("Unsaved typing", saved.first { it.id == original.id }.draft)
        assertEquals("Other name", saved.first { it.id == other.id }.title)
    }
    @Test fun oldBackendInvalidNamesAndMissingChatsLeaveRecordsUntouched() {
        assertNotNull(model.renameChat(original.id, "New name"))
        enableRename()
        for (name in listOf("", "a\nb", "x".repeat(121))) assertNotNull(model.renameChat(original.id, name))
        assertNotNull(model.renameChat("missing", "Name"))
        assertEquals(listOf(original, other), persisted())
    }
    @Test fun activeGenerationBlocksRename() {
        enableRename()
        @Suppress("UNCHECKED_CAST")
        val typing = ChatViewModel::class.java.getDeclaredField("_isTyping").apply { isAccessible = true }.get(model) as MutableStateFlow<Boolean>
        typing.value = true
        assertNotNull(model.renameChat(original.id, "New name"))
        assertEquals(listOf(original, other), persisted())
    }
    @Test fun savingSameNameDoesNotCreateAnotherCloudEdit() {
        enableRename()
        assertNull(model.renameChat(original.id, "Name"))
        val before = persisted().first { it.id == original.id }
        assertNull(model.renameChat(original.id, " Name "))
        assertEquals(before, persisted().first { it.id == original.id })
    }
    @Test fun namedDraftConversationSurvivesClearingItsDraft() {
        enableRename()
        model.startNewChat()
        model.updateDraft("Temporary text")
        model.flushHistory()
        val id = model.activeChatId.value
        assertNull(model.renameChat(id, "Saved notes"))
        model.updateDraft("")
        assertEquals("Saved notes", persisted().first { it.id == id }.title)
        assertTrue(persisted().first { it.id == id }.messages.isEmpty())
    }
}

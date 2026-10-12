package com.dua.ai.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.*
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
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
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ServerChatDeletionCacheTest {
    private lateinit var app: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val active = SavedChat("target", "Dua", "Target draft", listOf(ChatMessage(id = "u", text = "Target question", isUser = true)), 1)
    private val other = SavedChat("other", "Dua", "Other draft", listOf(ChatMessage(id = "o", text = "Other question", isUser = true)), 2)
    private val session = AccountSession(AccountUser(owner, "owner@example.com", "Owner"), "fake-access", "fake-refresh", Long.MAX_VALUE / 1000)
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            app.getSharedPreferences(name, 0).edit().clear().commit()
        app.getSharedPreferences("dua_chat_history", 0).edit().putString("history", ChatHistoryCodec.encode(listOf(active, other), active.id)).commit()
        model = ChatViewModel(app).also { store.put("chat", it) }
        val repository = AccountRepository(AccountApi(), object : SessionPersistence {
            override fun read() = session
            override fun write(value: AccountSession?) = Unit
        })
        ChatViewModel::class.java.getDeclaredField("account").apply { isAccessible = true }.set(model, repository)
    }
    @After fun teardown() { store.clear() }
    private fun apply(result: CloudChatDeletion) = kotlinx.coroutines.runBlocking {
        kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
            try {
                val returned = ChatViewModel::class.java.getDeclaredMethod("applyConfirmedChatDeletion", CloudChatDeletion::class.java, kotlin.coroutines.Continuation::class.java)
                    .apply { isAccessible = true }.invoke(model, result, continuation)
                if (returned !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) continuation.resumeWith(Result.success(Unit))
            } catch (wrapped: InvocationTargetException) { continuation.resumeWith(Result.failure(wrapped.targetException)) }
        }
    }
    private fun cached(): List<SavedChat> {
        model.flushHistory()
        return ChatHistoryCodec.decode(app.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second
    }
    @Test fun confirmedActiveDeletionClearsBuffersAndCannotReturnAfterTypingOrRestart() {
        apply(CloudChatDeletion(owner, active.id, 4, listOf(active.id)))
        assertTrue(model.messages.value.isEmpty()); assertEquals("", model.draft.value)
        assertNotEquals(active.id, model.activeChatId.value)
        model.updateDraft("New conversation")
        assertFalse(cached().any { it.id == active.id })
        assertEquals(other, cached().first { it.id == other.id })
        store.clear(); model = ChatViewModel(app).also { store.put("chat", it) }
        assertFalse(cached().any { it.id == active.id })
        assertEquals(listOf(active.id), CloudDeletionIds.decode(org.json.JSONArray(app.getSharedPreferences("dua_chat_history", 0).getString("deleted_chat_ids", null))))
    }
    @Test fun deletingAnotherChatPreservesTheLatestActiveDraftAndSelection() {
        model.updateDraft("Unsaved edit in target")
        apply(CloudChatDeletion(owner, other.id, 4, listOf(other.id)))
        assertEquals(active.id, model.activeChatId.value)
        assertEquals("Unsaved edit in target", cached().single().draft)
        assertEquals(active.messages, cached().single().messages)
    }
    @Test fun mismatchedOwnerOrMissingConfirmationCannotAlterTheCache() {
        for (result in listOf(CloudChatDeletion("wrong-owner", active.id, 4, listOf(active.id)), CloudChatDeletion(owner, active.id, 4, emptyList()))) {
            assertTrue(runCatching { apply(result) }.isFailure)
        }
        assertEquals(listOf(active, other), cached())
    }
    @Test fun oldBackendOrSignedOutStateReturnsAnErrorWithoutStartingDeletion() {
        model.deleteServerChat(active.id)
        assertFalse(model.chatDeletion.value.busy); assertNotNull(model.chatDeletion.value.error)
        assertEquals(listOf(active, other), cached())
    }
    @Test fun aFailedCacheCommitKeepsLocalRecordsAndAnIdempotentRetryFinishes() {
        val preferences = app.getSharedPreferences("dua_chat_history", 0)
        val guarded = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, arguments ->
            if (method.name == "edit") {
                val editor = preferences.edit()
                Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, call, values ->
                    if (call.name == "commit") false else {
                        val returned = call.invoke(editor, *(values ?: emptyArray()))
                        if (returned is SharedPreferences.Editor) proxy else returned
                    }
                }
            } else method.invoke(preferences, *(arguments ?: emptyArray()))
        } as SharedPreferences
        val field = ChatViewModel::class.java.getDeclaredField("preferences").apply { isAccessible = true }
        field.set(model, guarded)
        val confirmed = CloudChatDeletion(owner, active.id, 4, listOf(active.id))
        assertTrue(runCatching { apply(confirmed) }.isFailure)
        assertEquals(listOf(active, other), model.savedChats.value)
        assertEquals(active.id, model.activeChatId.value)
        assertNull(preferences.getString("deleted_chat_ids", null))
        field.set(model, preferences)
        apply(confirmed)
        assertEquals(listOf(other), cached())
    }
    @Test fun repeatedDeleteAndCacheRemovalCannotReplaceAnInFlightDeletion() {
        @Suppress("UNCHECKED_CAST")
        val flow = ChatViewModel::class.java.getDeclaredField("_chatDeletion").apply { isAccessible = true }.get(model)
            as kotlinx.coroutines.flow.MutableStateFlow<ChatViewModel.ChatDeletionState>
        val inFlight = ChatViewModel.ChatDeletionState(id = active.id, busy = true)
        flow.value = inFlight
        @Suppress("UNCHECKED_CAST")
        val busy = ChatViewModel::class.java.getDeclaredField("_dataBusy").apply { isAccessible = true }.get(model)
            as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        busy.value = true
        model.deleteServerChat(active.id); model.deleteServerChat(other.id); model.dismissChatDeletion()
        model.deleteChat(other.id)
        assertEquals(inFlight, flow.value)
        assertEquals(listOf(active, other), model.savedChats.value)
    }
    @Test fun cachedDeletionIdsCannotHideARecreatedOwnersServerHistory() {
        apply(CloudChatDeletion(owner, active.id, 4, listOf(active.id)))
        val replacement = session.copy(user = session.user.copy(id = "22222222-2222-4222-8222-222222222222"))
        val repository = AccountRepository(AccountApi(), object : SessionPersistence {
            override fun read() = replacement
            override fun write(value: AccountSession?) = Unit
        })
        ChatViewModel::class.java.getDeclaredField("account").apply { isAccessible = true }.set(model, repository)
        val sync = ChatViewModel::class.java.getDeclaredField("automaticSync").apply { isAccessible = true }.get(model)
        val port = com.dua.ai.setup.AutomaticCloudSync::class.java.getDeclaredField("local").apply { isAccessible = true }.get(sync)
            as com.dua.ai.setup.AutomaticCloudSync.LocalPort
        assertTrue(port.read().deletedChatIds.isEmpty())
        port.commit(CloudSnapshot(listOf(active), emptyList()))
        assertEquals(listOf(active), cached())
        assertEquals(replacement.user.id, app.getSharedPreferences("dua_chat_history", 0).getString("deleted_chat_owner", null))
    }
}

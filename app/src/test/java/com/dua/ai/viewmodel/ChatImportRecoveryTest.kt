package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatImportRecoveryTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private fun chat() = CloudSnapshotCodec.decode(JSONObject(javaClass.getResourceAsStream("/cloud-snapshot-v1.json")!!
        .bufferedReader().use { it.readText() })).chats.single()

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) {
            application.getSharedPreferences(name, 0).edit().clear().commit()
        }
    }
    @After fun teardown() { store.clear() }

    private fun seed(chats: List<SavedChat>) {
        application.getSharedPreferences("dua_chat_history", 0).edit()
            .putString("history", ChatHistoryCodec.encode(chats, chats.first().id)).commit()
        model = ChatViewModel(application).also { store.put("chat", it) }
    }
    private fun preview(chats: List<SavedChat>) {
        @Suppress("UNCHECKED_CAST")
        val flow = ChatViewModel::class.java.getDeclaredField("_importPreview").apply { isAccessible = true }
            .get(model) as MutableStateFlow<List<SavedChat>?>
        flow.value = chats
    }
    private fun waitForImport() {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (model.dataBusy.value && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertFalse("Import did not finish", model.dataBusy.value)
    }

    @Test fun importedCopyRetainsMetadataAndActiveDraftSurvivesRestartPersistence() {
        val original = chat()
        seed(listOf(original))
        model.updateDraft("Keep my active draft")
        preview(ChatHistoryCodec.parseImport(ChatHistoryCodec.exportBackup(listOf(original))))
        model.confirmImport()
        waitForImport()
        model.flushHistory()
        val records = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0)
            .getString("history", null)!!).second
        val active = records.single { it.id == original.id }
        val imported = records.single { it.id != original.id }
        assertEquals("Keep my active draft", active.draft)
        assertEquals(original.messages, active.messages)
        assertEquals(original.summary, active.summary)
        assertEquals(original.buildRepository, model.buildRepo.value)
        assertEquals(original.buildRepository, imported.buildRepository)
        assertEquals(imported.messages.first().id, imported.summary!!.throughId)
        imported.messages.zip(original.messages).forEach { (actual, expected) -> assertEquals(expected.copy(id = actual.id), actual) }
        assertNull(model.importPreview.value)
    }

    @Test fun oversizedCombinedImportLeavesHistoryAndBuffersAndAllowsRetry() {
        val original = chat()
        val existing = (0 until 100).map { original.copy(id = "chat-$it") }
        seed(existing)
        preview(listOf(original))
        val before = application.getSharedPreferences("dua_chat_history", 0).getString("history", null)
        model.confirmImport()
        waitForImport()
        assertEquals(before, application.getSharedPreferences("dua_chat_history", 0).getString("history", null))
        assertEquals(existing, model.savedChats.value)
        assertEquals(existing.first().messages, model.messages.value)
        assertEquals(existing.first().draft, model.draft.value)
        assertNotNull(model.importPreview.value)
        assertFalse(model.dataBusy.value)
    }

    @Test fun draftChangedWhilePreparingImportIsKeptAndStopsTheStaleImport() {
        val original = chat()
        seed(listOf(original))
        preview(listOf(original))
        model.confirmImport()
        model.updateDraft("Typing during preparation")
        waitForImport()
        assertNotNull(model.importPreview.value)
        assertTrue(model.dataStatus.value.contains("phone cache changed"))
        assertEquals("Typing during preparation", model.draft.value)
        model.flushHistory()
        assertEquals(1, model.savedChats.value.size)
        assertEquals(original.messages, model.savedChats.value.single().messages)
        val restored = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0)
            .getString("history", null)!!).second.single()
        assertEquals("Typing during preparation", restored.draft)
    }
}

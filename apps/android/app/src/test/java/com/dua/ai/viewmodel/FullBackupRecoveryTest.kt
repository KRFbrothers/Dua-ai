package com.dua.ai.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class FullBackupRecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var app: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val password get() = "backup password long enough".toCharArray()
    private val original = SavedChat("active", "Dua", "Original draft", listOf(ChatMessage(id = "message", text = "Hello", isUser = true, model = "Dua")), 123)

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_preferences", "dua_auto_sync", "dua_files", "dua_account_session")) app.getSharedPreferences(name, 0).edit().clear().commit()
        app.getSharedPreferences("dua_chat_history", 0).edit().putString("history", ChatHistoryCodec.encode(listOf(original), original.id)).commit()
        DevicePreferences(app).update(LocalPreferences(memoryEnabled = false, memory = listOf("Prefer Hinglish")))
        model = ChatViewModel(app).also { store.put("chat", it) }
    }
    @After fun close() { store.clear() }

    private fun await() {
        val deadline = System.nanoTime() + 15_000_000_000L
        while ((model.dataBusy.value || model.fullBackup.state.value.busy) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
        assertFalse("Operation did not finish: ${model.fullBackup.state.value}", model.fullBackup.state.value.busy)
        assertFalse(model.dataBusy.value)
    }
    private fun backup(): File = temp.newFile("snapshot.duabackup").also {
        val key = password
        model.fullBackup.save(Uri.fromFile(it), key)
        await()
        assertTrue(model.fullBackup.state.value.status, model.fullBackup.state.value.status.startsWith("Full backup saved"))
        assertTrue(key.all { char -> char == '\u0000' })
        assertTrue(it.length() > 0)
    }

    @Test fun encryptedBackupReviewsThenMergesNotesAndImportsCopiesWithoutLosingTheActiveDraft() {
        val file = backup()
        model.updateSettings(model.localSettings.value.copy(memory = listOf("Keep existing note")))
        model.updateDraft("Keep current draft")
        model.fullBackup.review(Uri.fromFile(file), password)
        await()
        assertEquals(1, model.fullBackup.state.value.preview?.notes)
        assertEquals(listOf("Keep existing note"), model.localSettings.value.memory)
        assertEquals(1, model.savedChats.value.size)
        model.fullBackup.confirmRestore()
        await()
        assertTrue(model.fullBackup.state.value.status, model.fullBackup.state.value.status.startsWith("Full backup restored"))
        assertEquals(2, model.savedChats.value.size)
        assertEquals("Keep current draft", model.draft.value)
        assertEquals(listOf("Keep existing note", "Prefer Hinglish"), DevicePreferences(app).state.value.memory)
        assertFalse(model.localSettings.value.memoryEnabled)
        model.flushHistory()
        val cached = ChatHistoryCodec.decode(app.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second
        assertEquals(2, cached.size)
        assertEquals("Keep current draft", cached.single { it.id == original.id }.draft)
        assertNull(model.fullBackup.state.value.preview)
    }

    @Test fun wrongPasswordAndCancelledReviewDoNotChangeAnyChatsOrNotes() {
        val file = backup()
        val before = model.savedChats.value.toList()
        model.fullBackup.review(Uri.fromFile(file), "wrong backup password".toCharArray())
        await()
        assertNull(model.fullBackup.state.value.preview)
        assertTrue(model.fullBackup.state.value.status.contains("password"))
        assertEquals(before, model.savedChats.value)
        model.fullBackup.review(Uri.fromFile(file), password)
        await()
        assertNotNull(model.fullBackup.state.value.preview)
        model.fullBackup.cancelReview()
        assertNull(model.fullBackup.state.value.preview)
        assertEquals(before, model.savedChats.value)
        assertEquals(listOf("Prefer Hinglish"), model.localSettings.value.memory)
    }

    @Test fun memoryCapacityIsCheckedBeforeOfferingRestoreAndExistingDataIsKept() {
        val file = backup()
        val current = (1..10).map { "Existing $it" }
        model.updateSettings(model.localSettings.value.copy(memory = current))
        model.fullBackup.review(Uri.fromFile(file), password)
        await()
        assertNull(model.fullBackup.state.value.preview)
        assertEquals(current, model.localSettings.value.memory)
        assertEquals(1, model.savedChats.value.size)
        assertTrue(model.fullBackup.state.value.status.contains("10"))
    }
}

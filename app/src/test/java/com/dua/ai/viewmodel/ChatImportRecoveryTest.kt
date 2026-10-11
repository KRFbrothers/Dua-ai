package com.dua.ai.viewmodel

import android.app.Application
import android.content.SharedPreferences
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
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    @Test fun importedProjectInstructionsSurviveRestartWithoutChangingExistingProjects() {
        val original = chat()
        seed(listOf(original))
        val projects = com.dua.ai.projects.ProjectCodec.defaults + com.dua.ai.projects.ChatProject("client", "Client", "Cite invoice numbers")
        ChatViewModel::class.java.getDeclaredField("importProjects").apply { isAccessible = true }.set(model, projects)
        preview(listOf(original.copy(projectId = "client")))
        model.confirmImport()
        waitForImport()
        model.flushHistory()
        val restored = ChatHistoryCodec.decode(application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second
        val imported = restored.single { it.id != original.id }
        val settings = com.dua.ai.settings.DevicePreferences(application).state.value.projects
        assertEquals(com.dua.ai.projects.ProjectCodec.defaults, settings.take(3))
        assertEquals("Cite invoice numbers", settings.single { it.id == imported.projectId }.instructions)
        assertEquals("personal", restored.single { it.id == original.id }.projectId)
        assertNull(model.importPreview.value)
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

    @Test fun typingDuringTheIoCommitRestoresTheLatestCacheAndRejectsTheStaleImport() {
        val original = chat()
        seed(listOf(original))
        val preferences = application.getSharedPreferences("dua_chat_history", 0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val committedOnMain = AtomicBoolean(false)
        val mainThread = Looper.getMainLooper().thread
        val guarded = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, arguments ->
            if (method.name == "edit") {
                val editor = preferences.edit()
                Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)) { proxy, call, values ->
                    if (call.name == "commit") {
                        committedOnMain.set(Thread.currentThread() == mainThread)
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS)) { "Test did not release import commit" }
                    }
                    val result = call.invoke(editor, *(values ?: emptyArray()))
                    if (result is SharedPreferences.Editor) proxy else result
                }
            } else method.invoke(preferences, *(arguments ?: emptyArray()))
        } as SharedPreferences
        ChatViewModel::class.java.getDeclaredField("preferences").apply { isAccessible = true }.set(model, guarded)
        preview(listOf(original))
        try {
            model.confirmImport()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (entered.count > 0 && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertEquals("Import never reached the IO commit", 0L, entered.count)
            assertFalse("Disk commit blocked Main", committedOnMain.get())
            model.updateDraft("Typing during disk commit")
            release.countDown()
            waitForImport()
            assertNotNull(model.importPreview.value)
            assertTrue(model.dataStatus.value.contains("phone cache changed"))
            assertEquals("Typing during disk commit", model.draft.value)
            val records = ChatHistoryCodec.decode(preferences.getString("history", null)!!).second
            assertEquals(1, records.size)
            assertEquals("Typing during disk commit", records.single().draft)
            assertEquals(original.messages, records.single().messages)
        } finally { release.countDown() }
    }
}

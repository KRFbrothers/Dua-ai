package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.setup.IntegrationState
import kotlinx.coroutines.flow.MutableStateFlow
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.AutomaticCloudSync
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
class ProjectSwitchingTest {
    private lateinit var app: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val personal = SavedChat("personal-chat", "Dua", "Personal draft", emptyList(), 1)
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) app.getSharedPreferences(name, 0).edit().clear().commit()
        app.getSharedPreferences("dua_chat_history", 0).edit().putString("history", ChatHistoryCodec.encode(listOf(personal), personal.id)).commit()
        DevicePreferences(app).update(LocalPreferences(memoryEnabled = true, memory = listOf("Private note")))
        model = ChatViewModel(app).also { store.put("chat", it) }
    }
    @After fun teardown() { store.clear() }
    private fun persisted() = ChatHistoryCodec.decode(app.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!).second
    @Suppress("UNCHECKED_CAST")
    private fun enableTasks(dueDates: Boolean = false) {
        val flow = ChatViewModel::class.java.getDeclaredField("_integrations").apply { isAccessible = true }.get(model) as MutableStateFlow<IntegrationState>
        flow.value = flow.value.copy(projectsEnabled = true, projectTasksEnabled = true, taskDueDatesEnabled = dueDates)
    }
    @Test fun dueDatesRequireBackendSupportAndSurviveCompletionAndSettingsReset() {
        enableTasks()
        val at = 1893456000000L
        assertFalse(model.saveTask("coding", null, "Dated", at))
        enableTasks(dueDates = true)
        assertTrue(model.saveTask("coding", null, "Dated", at))
        val task = model.localSettings.value.projects[1].tasks.single()
        assertTrue(model.toggleTask("coding", task.id))
        model.resetSettings()
        assertEquals(at, DevicePreferences(app).state.value.projects[1].tasks.single().dueAt)
    }
    @Test fun tasksStayWithProjectAndSurviveSettingsEditsResetAndViewModelRestart() {
        enableTasks()
        assertTrue(model.saveTask("coding", null, "  Review diff  "))
        var task = model.localSettings.value.projects.single { it.id == "coding" }.tasks.single()
        assertEquals("Review diff", task.title)
        assertTrue(model.toggleTask("coding", task.id))
        assertTrue(model.saveTask("coding", task.id, "Review final diff"))
        task = model.localSettings.value.projects.single { it.id == "coding" }.tasks.single()
        assertTrue(task.completed)
        assertTrue(model.saveProject("coding", "Code work", "Use Kotlin", false))
        model.resetSettings()
        val reopened = ChatViewModel(app).also { store.put("reopened", it) }
        assertEquals(listOf(task), reopened.localSettings.value.projects.single { it.id == "coding" }.tasks)
        assertTrue(reopened.localSettings.value.projects.single { it.id == "business" }.tasks.isEmpty())
        assertTrue(model.deleteTask("coding", task.id))
        assertTrue(model.localSettings.value.projects.single { it.id == "coding" }.tasks.isEmpty())
        assertFalse(model.toggleTask("coding", task.id))
    }
    @Test fun oldBackendArchivedProjectsAndInvalidTasksRejectChangesWithoutDroppingExistingData() {
        assertFalse(model.saveTask("coding", null, "Keep safe"))
        enableTasks()
        assertTrue(model.saveTask("coding", null, "Keep safe"))
        assertTrue(model.saveProject("coding", "Coding", "", true))
        val original = model.localSettings.value.projects
        assertFalse(model.saveTask("coding", null, "Blocked"))
        assertEquals(original, model.localSettings.value.projects)
        assertTrue(model.saveProject("coding", "Coding", "", false))
        assertFalse(model.saveTask("coding", null, " "))
        assertFalse(model.saveTask("coding", "missing", "No accidental replacement"))
        assertEquals(listOf("Keep safe"), model.localSettings.value.projects.single { it.id == "coding" }.tasks.map { it.title })
    }
    @Test fun switchingSavesOldDraftAndPersistsNewProjectWithoutMovingOldChats() {
        model.updateDraft("Edited personal draft")
        model.selectProject("coding")
        assertEquals("coding", model.projectId.value)
        assertEquals("", model.draft.value)
        model.updateDraft("Coding draft"); model.flushHistory()
        val coding = persisted().single { it.projectId == "coding" }
        assertEquals("Coding draft", coding.draft)
        assertEquals("Edited personal draft", persisted().single { it.id == personal.id }.draft)
        model.openChat(personal.id)
        assertEquals("personal", model.projectId.value)
        model.openChat(coding.id)
        assertEquals("coding", model.projectId.value)
        assertEquals("Coding draft", model.draft.value)
    }
    @Test fun archivedProjectBlocksRepliesAndNewChatChoosesAnActiveProject() {
        model.selectProject("coding")
        assertTrue(model.saveProject("coding", "Coding", "Use Kotlin", true))
        model.sendMessage("Hello")
        assertTrue(model.error.value!!.contains("archived"))
        assertTrue(model.messages.value.isEmpty())
        model.startNewChat()
        assertEquals("personal", model.projectId.value)
        assertTrue(model.saveProject("coding", "Coding", "Use Kotlin", false))
        model.selectProject("coding")
        assertEquals("coding", model.projectId.value)
    }
    @Test fun automaticDownloadRestoresProjectSettingsAndActiveChatBeforeDraftEdits() {
        val coding = personal.copy(projectId = "coding", draft = "Server draft")
        val projects = ProjectCodec.defaults.map { if (it.id == "coding") it.copy(instructions = "Use Kotlin") else it }
        val sync = ChatViewModel::class.java.getDeclaredField("automaticSync").apply { isAccessible = true }.get(model)
        val port = AutomaticCloudSync::class.java.getDeclaredField("local").apply { isAccessible = true }.get(sync) as AutomaticCloudSync.LocalPort
        port.commit(CloudSnapshot(listOf(coding), emptyList(), projects))
        assertEquals("coding", model.projectId.value)
        assertEquals(projects, model.localSettings.value.projects)
        model.updateDraft("Edit after sync"); model.flushHistory()
        assertEquals("coding", persisted().single().projectId)
        assertEquals("Use Kotlin", DevicePreferences(app).state.value.projects.find { it.id == "coding" }!!.instructions)
    }
    @Test fun personalMemoryIsNotProposedOrSentWithOtherProjectChats() {
        model.selectProject("business")
        val memory = ChatViewModel::class.java.getDeclaredMethod("projectMemory").apply { isAccessible = true }
        assertEquals("", memory.invoke(model))
        model.proposeMemoryNotes(); assertTrue(model.memoryCandidates.value.isEmpty())
        model.selectProject("personal")
        assertEquals("- Private note", memory.invoke(model))
    }
    @Suppress("UNCHECKED_CAST")
    private fun enableProjectMemory() {
        val flow = ChatViewModel::class.java.getDeclaredField("_integrations").apply { isAccessible = true }.get(model) as MutableStateFlow<IntegrationState>
        flow.value = flow.value.copy(projectsEnabled = true, projectMemoryEnabled = true)
    }
    @Test fun projectMemorySurvivesTaskSettingsResetRestartAndServerDownload() {
        enableProjectMemory(); enableTasks()
        assertTrue(model.saveProjectMemory("coding", true, listOf("  Prefer Compose  ")))
        assertTrue(model.saveTask("coding", null, "Review"))
        assertTrue(model.saveProject("coding", "Coding", "Use Kotlin", false))
        assertEquals(listOf("Private note"), model.localSettings.value.memory)
        model.resetSettings()
        val reopened = ChatViewModel(app).also { store.put("reopened", it) }
        val coding = reopened.localSettings.value.projects[1]
        assertEquals(com.dua.ai.projects.ProjectMemory(true, listOf("Prefer Compose")), coding.memory)
        assertEquals("Use Kotlin", coding.instructions); assertEquals("Review", coding.tasks.single().title)
        assertEquals(com.dua.ai.projects.ProjectMemory(), reopened.localSettings.value.projects[2].memory)
        assertTrue(reopened.localSettings.value.memory.isEmpty())
        val remote = reopened.localSettings.value.projects.map { if (it.id == "coding") it.copy(memory = com.dua.ai.projects.ProjectMemory(false, listOf("Server preference"))) else it }
        val sync = ChatViewModel::class.java.getDeclaredField("automaticSync").apply { isAccessible = true }.get(model)
        val port = AutomaticCloudSync::class.java.getDeclaredField("local").apply { isAccessible = true }.get(sync) as AutomaticCloudSync.LocalPort
        port.commit(CloudSnapshot(listOf(personal), listOf("Private note"), remote))
        assertEquals(remote[1].memory, DevicePreferences(app).state.value.projects[1].memory)
    }
    @Test fun unavailableArchivedPersonalAndInvalidMemoryDoNotOverwriteCache() {
        assertFalse(model.saveProjectMemory("coding", true, listOf("Blocked")))
        enableProjectMemory()
        assertTrue(model.saveProjectMemory("coding", false, listOf("Keep me")))
        assertFalse(model.saveProjectMemory("personal", true, listOf("Duplicate")))
        assertFalse(model.saveProjectMemory("coding", true, List(11) { "Too many" }))
        assertFalse(model.saveProjectMemory("coding", true, listOf(" ")))
        assertTrue(model.saveProject("coding", "Coding", "", true))
        assertFalse(model.saveProjectMemory("coding", true, listOf("Blocked")))
        assertEquals(com.dua.ai.projects.ProjectMemory(false, listOf("Keep me")), model.localSettings.value.projects[1].memory)
        assertEquals(listOf("Private note"), model.localSettings.value.memory)
    }
    @Test fun oldBackendCannotSilentlyIgnoreCodingInstructions() {
        model.selectProject("coding")
        model.sendMessage("Review this code")
        assertTrue(model.messages.value.isEmpty())
        assertTrue(model.error.value!!.contains("Projects backend"))
    }
    @Test fun resettingAppearanceAndMemoryKeepsProjectsReferencedBySavedChats() {
        assertTrue(model.saveProject("coding", "Coding", "Use Kotlin", false))
        model.selectProject("coding"); model.updateDraft("Keep draft"); model.flushHistory()
        val projects = model.localSettings.value.projects
        model.resetSettings()
        assertEquals(projects, model.localSettings.value.projects)
        assertTrue(model.localSettings.value.memory.isEmpty())
        assertEquals("coding", persisted().first().projectId)
    }
}

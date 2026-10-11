package com.dua.ai.projects

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.ChatBackup
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.setup.AutomationSyncPolicy
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProjectsTest {
    private val projects = ProjectCodec.defaults + ChatProject("client", "Client work", "Cite sources")
    private val chat = SavedChat("chat-id", "Dua", "Draft", emptyList(), 1, projectId = "client")
    private fun snapshot(p: List<ChatProject>) = CloudSnapshot(listOf(chat), emptyList(), p)
    @Test fun cloudCachePreferencesAndExportKeepProjectSettingsAndChatReferences() {
        val cloud = snapshot(projects)
        assertEquals(cloud, CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(cloud)))
        assertEquals(chat, ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), chat.id)).second.single())
        val settings = LocalPreferences(projects = projects)
        assertEquals(settings, PreferencesCodec.decode(PreferencesCodec.encode(settings)))
        val raw = ChatHistoryCodec.exportBackup(listOf(chat), projects = projects)
        val restored = ChatHistoryCodec.readBackup(raw.byteInputStream())
        assertEquals(projects, restored.projects)
        assertEquals(chat, restored.chats.single())
    }
    @Test fun legacyExportsAndSettingsUseDefaultProjectsAndPersonalHistory() {
        val old = JSONObject(ChatHistoryCodec.exportBackup(listOf(chat.copy(projectId = "personal"))))
        old.getJSONArray("chats").getJSONObject(0).remove("projectId")
        assertEquals("personal", ChatHistoryCodec.parseImport(old.toString()).single().projectId)
        assertEquals(ProjectCodec.defaults, ChatHistoryCodec.readBackup(old.toString().byteInputStream()).projects)
        val settings = JSONObject(PreferencesCodec.encode(LocalPreferences())).apply { remove("projects") }
        assertEquals(ProjectCodec.defaults, PreferencesCodec.decode(settings.toString()).projects)
    }
    @Test fun danglingIdsMalformedFlagsAndProjectLimitsCannotOverwriteValidState() {
        val raw = CloudSnapshotCodec.encode(snapshot(projects))
        raw.getJSONArray("chats").getJSONObject(0).put("projectId", "missing")
        rejects { CloudSnapshotCodec.decode(raw) }
        val array = ProjectCodec.toJson(projects); array.getJSONObject(0).put("archived", "false")
        rejects { ProjectCodec.fromJson(array) }
        rejects { ProjectCodec.validate(projects.map { it.copy(archived = true) }) }
        rejects { ProjectCodec.validate(projects + projects.first()) }
        rejects { ProjectCodec.validate(projects.map { it.copy(instructions = "x".repeat(4001)) }) }
    }
    @Test fun automaticSyncUploadsOnlyLocalEditsAndPausesForDivergentInstructions() {
        val original = snapshot(projects)
        val baseline = AutomationSyncPolicy.baseline(original)
        val local = snapshot(projects.map { if (it.id == "client") it.copy(instructions = "Show calculations") else it })
        assertEquals(local, AutomationSyncPolicy.merge(local, original, baseline).snapshot)
        val remote = snapshot(projects.map { if (it.id == "client") it.copy(instructions = "Cite document pages") else it })
        assertEquals(remote, AutomationSyncPolicy.merge(original, remote, baseline).snapshot)
        rejects { AutomationSyncPolicy.merge(local, remote, baseline) }
        assertFalse(AutomationSyncPolicy.same(local, original))
        assertFalse(baseline.toString().contains("Cite sources"))
    }
    @Test fun defaultInstructionsEditedBeforeFirstSyncAreUploadedButRemoteEditsWinUnchangedCache() {
        val original = CloudSnapshot(emptyList(), emptyList())
        val edited = original.copy(projects = original.projects.map { if (it.id == "coding") it.copy(instructions = "Use Kotlin") else it })
        assertEquals(edited, AutomationSyncPolicy.merge(edited, original, null).snapshot)
        assertEquals(edited, AutomationSyncPolicy.merge(original, edited, null).snapshot)
    }
    @Test fun reviewedDownloadKeepsPhoneOnlyProjectDefinitionsAndUsesServerInstructions() {
        val remote = CloudSnapshot(emptyList(), emptyList(), ProjectCodec.defaults.map { if (it.id == "coding") it.copy(instructions = "Use Kotlin") else it })
        val merged = CloudSnapshotCodec.mergeDownload(snapshot(projects), remote).snapshot
        assertEquals(chat, merged.chats.single())
        assertEquals(projects.last(), merged.projects.last())
        assertEquals("Use Kotlin", merged.projects.find { it.id == "coding" }!!.instructions)
    }
    @Test fun importingDifferentInstructionsCreatesProjectCopiesInsteadOfChangingExistingChats() {
        val incoming = projects.map { if (it.id == "client") it.copy(instructions = "Different context") else it }
        val imported = ProjectImports.prepare(projects, ChatBackup(listOf(chat), incoming))
        assertEquals(projects, imported.projects.take(projects.size))
        val copy = imported.projects.last()
        assertNotEquals("client", copy.id)
        assertEquals("Different context", copy.instructions)
        assertEquals(copy.id, imported.chats.single().projectId)
        assertEquals(projects, ProjectImports.prepare(projects, ChatBackup(listOf(chat), projects)).projects)
    }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Invalid project state accepted") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
}

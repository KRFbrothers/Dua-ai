package com.dua.ai.projects

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.ChatBackup
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import com.dua.ai.setup.AutomationSyncPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProjectMemoryTest {
    private fun snapshot(memory: ProjectMemory) = CloudSnapshot(emptyList(), listOf("Personal only"), ProjectCodec.defaults.map {
        if (it.id == "coding") it.copy(instructions = "Use Kotlin", tasks = listOf(ProjectTask("t", "Review", dueAt = 1893456000000L)), memory = memory) else it
    })
    @Test fun cloudPreferencesAndChatlessExportKeepNotesSwitchAndOtherProjectFields() {
        for (enabled in listOf(false, true)) {
            val original = snapshot(ProjectMemory(enabled, listOf("Use Compose", "🙂".repeat(100), "Line one\nLine two")))
            assertEquals(original, CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(original)))
            val settings = LocalPreferences(projects = original.projects)
            assertEquals(settings, PreferencesCodec.decode(PreferencesCodec.encode(settings)))
            assertEquals(original.projects, ChatHistoryCodec.readBackup(ChatHistoryCodec.exportBackup(emptyList(), projects = original.projects).byteInputStream()).projects)
        }
    }
    @Test fun defaultsKeepOldWireSignaturesAndAcceptExplicitEmptyMemory() {
        ProjectCodec.defaults.forEach { p ->
            val legacy = JSONObject().put("id", p.id).put("name", p.name).put("instructions", p.instructions).put("archived", p.archived)
            assertEquals(AutomationSyncPolicy.digest(legacy.toString()), AutomationSyncPolicy.projectSignature(p))
            assertFalse(ProjectCodec.encodedProject(p).has("memory"))
        }
        val json = ProjectCodec.toJson(ProjectCodec.defaults)
        json.getJSONObject(1).put("memory", JSONObject().put("enabled", false).put("notes", JSONArray()))
        assertEquals(ProjectCodec.defaults, ProjectCodec.fromJson(json))
    }
    @Test fun invalidMemoryFailsWithoutAcceptingLossyTypesOrOversizedNotes() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("memory", JSONObject.NULL) }, { it.put("memory", JSONArray()) },
            { it.getJSONObject("memory").put("enabled", "true") },
            { it.getJSONObject("memory").remove("notes") },
            { it.getJSONObject("memory").put("notes", JSONArray().put(" ")) },
            { it.getJSONObject("memory").put("notes", JSONArray().put(7)) },
            { it.getJSONObject("memory").put("notes", JSONArray().put("x".repeat(201))) },
            { it.getJSONObject("memory").put("notes", JSONArray(List(11) { "Note" })) },
            { it.getJSONObject("memory").put("extra", true) }
        )
        mutations.forEach { mutation ->
            val json = CloudSnapshotCodec.encode(snapshot(ProjectMemory(true, listOf("Private code preference"))))
            mutation(json.getJSONArray("projects").getJSONObject(1))
            assertTrue(runCatching { CloudSnapshotCodec.decode(json) }.isFailure)
        }
        assertTrue(runCatching { ProjectCodec.validate(ProjectCodec.defaults.map { if (it.id == "personal") it.copy(memory = ProjectMemory(true, listOf("Duplicate Personal"))) else it }) }.isFailure)
    }
    @Test fun memoryChangesSyncAndConflictingEditsPauseBeforeOverwriting() {
        val original = snapshot(ProjectMemory(true, listOf("Old preference")))
        val base = AutomationSyncPolicy.baseline(original)
        val edited = snapshot(ProjectMemory(false, listOf("New preference")))
        assertEquals(edited, AutomationSyncPolicy.merge(edited, original, base).snapshot)
        assertEquals(edited, AutomationSyncPolicy.merge(original, edited, base).snapshot)
        val elsewhere = snapshot(ProjectMemory(true, listOf("Different server edit")))
        assertTrue(runCatching { AutomationSyncPolicy.merge(edited, elsewhere, base) }.isFailure)
        assertEquals(elsewhere, CloudSnapshotCodec.mergeDownload(edited, elsewhere).snapshot)
        val deleted = snapshot(ProjectMemory())
        assertEquals(deleted, AutomationSyncPolicy.merge(deleted, original, base).snapshot)
    }
    @Test fun importsWithDifferentNotesPreserveCurrentMemoryAndCreateSeparateProjects() {
        val current = snapshot(ProjectMemory(true, listOf("Current note")))
        val imported = snapshot(ProjectMemory(false, listOf("Imported note")))
        val result = ProjectImports.prepare(current.projects, ChatBackup(emptyList(), imported.projects))
        assertEquals(current.projects[1], result.projects.find { it.id == "coding" })
        val copy = result.projects.single { it.name.endsWith("(imported)") }
        assertNotEquals("coding", copy.id)
        assertEquals(imported.projects[1].memory, copy.memory)
        assertEquals(current.projects.size + 1, result.projects.size)
    }
}

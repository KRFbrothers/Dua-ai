package com.dua.ai.projects

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.ChatBackup
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import com.dua.ai.setup.AutomationSyncPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProjectTasksTest {
    private val task = ProjectTask("task-1", "Review Kotlin changes")
    private fun snapshot(tasks: List<ProjectTask>) = CloudSnapshot(emptyList(), emptyList(), com.dua.ai.projects.ProjectFixtures.owned.map {
        if (it.id == "coding") it.copy(tasks = tasks) else it
    })
    @Test fun cloudPreferencesAndChatlessJsonBackupPreserveTasks() {
        val original = snapshot(listOf(task.copy(dueAt = 1893456000000L), task.copy(id = "task-2", completed = true)))
        assertEquals(original, CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(original)))
        val settings = LocalPreferences(projects = original.projects)
        assertEquals(settings, PreferencesCodec.decode(PreferencesCodec.encode(settings)))
        val raw = ChatHistoryCodec.exportBackup(emptyList(), projects = original.projects)
        assertEquals(original.projects, ChatHistoryCodec.readBackup(raw.byteInputStream()).projects)
    }
    @Test fun emptyTasksKeepLegacyWireFormatAndSyncSignaturesIncludingArchivedProjects() {
        for (project in com.dua.ai.projects.ProjectFixtures.owned + ChatProject("archived", "Archived", archived = true)) {
            val legacy = JSONObject().put("id", project.id).put("name", project.name).put("instructions", project.instructions).put("archived", project.archived)
            assertEquals(AutomationSyncPolicy.digest(legacy.toString()), AutomationSyncPolicy.projectSignature(project))
            assertFalse(ProjectCodec.encodedProject(project).has("tasks"))
        }
        val json = CloudSnapshotCodec.encode(snapshot(emptyList()))
        json.getJSONArray("projects").getJSONObject(1).put("tasks", JSONArray())
        assertEquals(snapshot(emptyList()), CloudSnapshotCodec.decode(json))
    }
    @Test fun completionEditsAndDeletionSyncWhileDivergentTaskEditsPauseForReview() {
        val original = snapshot(listOf(task))
        val base = AutomationSyncPolicy.baseline(original)
        val completed = snapshot(listOf(task.copy(completed = true)))
        assertEquals(completed, AutomationSyncPolicy.merge(completed, original, base).snapshot)
        assertEquals(completed, AutomationSyncPolicy.merge(original, completed, base).snapshot)
        assertEquals(snapshot(emptyList()), AutomationSyncPolicy.merge(snapshot(emptyList()), original, base).snapshot)
        val divergent = snapshot(listOf(task.copy(title = "Different edit")))
        assertTrue(runCatching { AutomationSyncPolicy.merge(completed, divergent, base) }.isFailure)
        assertFalse(base.toString().contains(task.title))
        assertEquals(completed.projects, CloudSnapshotCodec.mergeDownload(divergent, completed).snapshot.projects)
    }
    @Test fun importingTaskChangesKeepsExistingTasksAndCreatesASeparateProject() {
        val original = snapshot(listOf(task))
        val incoming = snapshot(listOf(task.copy(completed = true)))
        val imported = ProjectImports.prepare(original.projects, ChatBackup(emptyList(), incoming.projects))
        assertEquals(original.projects, imported.projects.take(original.projects.size))
        assertEquals(incoming.projects[1].tasks, imported.projects.last().tasks)
        assertNotEquals("coding", imported.projects.last().id)
    }
    @Test fun malformedTasksAndLimitsFailBeforePersistence() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("tasks", JSONObject.NULL) }, { it.put("tasks", "bad") },
            { it.getJSONArray("tasks").getJSONObject(0).put("completed", "true") },
            { it.getJSONArray("tasks").getJSONObject(0).put("title", " ") },
            { it.getJSONArray("tasks").getJSONObject(0).put("title", "x".repeat(201)) },
            { it.getJSONArray("tasks").getJSONObject(0).put("id", "../bad") },
            { it.getJSONArray("tasks").getJSONObject(0).put("extra", true) },
            { it.getJSONArray("tasks").getJSONObject(0).put("dueAt", JSONObject.NULL) },
            { it.getJSONArray("tasks").getJSONObject(0).put("dueAt", "123") },
            { it.getJSONArray("tasks").getJSONObject(0).put("dueAt", 1.5) },
            { it.getJSONArray("tasks").getJSONObject(0).put("dueAt", 0) },
            { it.getJSONArray("tasks").getJSONObject(0).put("dueAt", ProjectCodec.MAX_DUE_AT + 1) },
            { it.getJSONArray("tasks").put(it.getJSONArray("tasks").getJSONObject(0)) }
        )
        for (mutate in mutations) {
            val json = CloudSnapshotCodec.encode(snapshot(listOf(task)))
            mutate(json.getJSONArray("projects").getJSONObject(1))
            assertTrue(runCatching { CloudSnapshotCodec.decode(json) }.isFailure)
        }
        val maximum = List(100) { task.copy(id = "t$it", title = "🙂".repeat(100)) }
        assertEquals(snapshot(maximum), CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(snapshot(maximum))))
        assertTrue(runCatching { CloudSnapshotCodec.encode(snapshot(maximum + task)) }.isFailure)
    }
}

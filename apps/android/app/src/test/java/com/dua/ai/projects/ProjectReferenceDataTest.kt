package com.dua.ai.projects

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.files.FileRef
import com.dua.ai.history.SavedChat
import org.junit.Assert.*
import org.junit.Test

class ProjectReferenceDataTest {
    private val userProject = ChatProject("user-project", "My notes", details = ProjectDetails(100, 200, "book", "blue", pinned = true))
    private val source = FileRef("11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222.txt", "Notes.txt", "text/plain", 4)
    @Test fun freshInstallHasOnlyTheHiddenHomeWorkspaceAndNoProjectOrChatExamples() {
        assertEquals(listOf("personal"), ProjectCodec.defaults.map { it.id })
        assertTrue(ProjectCodec.userProjects(ProjectCodec.defaults, emptyList()).isEmpty())
        assertTrue(CloudSnapshot(emptyList(), emptyList()).chats.isEmpty())
    }
    @Test fun listHidesUntouchedLegacyStarterDefinitionsWithoutDeletingUserContent() {
        val legacy = ProjectCodec.defaults + ChatProject("coding", "Coding") + ChatProject("business", "Business") + userProject
        assertEquals(listOf(userProject), ProjectCodec.userProjects(legacy, emptyList()))
        val actualChat = SavedChat("actual", "Dua", "Draft by user", emptyList(), 4, projectId = "coding")
        assertEquals(listOf("coding", "user-project"), ProjectCodec.userProjects(legacy, listOf(actualChat)).map { it.id })
        assertEquals(4, legacy.size)
    }
    @Test fun metadataAndSourcesSurviveCloudAndBackupEncoding() {
        val project = userProject.copy(sources = listOf(source))
        val snapshot = CloudSnapshot(emptyList(), emptyList(), ProjectCodec.defaults + project)
        val encoded = CloudSnapshotCodec.encode(snapshot)
        assertEquals(snapshot, CloudSnapshotCodec.decode(encoded))
        assertEquals(project, ProjectCodec.fromJson(ProjectCodec.toJson(snapshot.projects)).last())
        assertFalse(ProjectCodec.encodedProject(ProjectCodec.defaults.single()).has("details"))
        assertFalse(ProjectCodec.encodedProject(ProjectCodec.defaults.single()).has("sources"))
    }
    @Test fun deletedProjectRetainsItsDefinitionButDoesNotRender() {
        val removed = userProject.copy(archived = true, details = userProject.details!!.copy(deleted = true))
        val all = ProjectCodec.defaults + removed
        ProjectCodec.validate(all)
        assertTrue(ProjectCodec.userProjects(all, emptyList()).isEmpty())
        assertEquals(removed, ProjectCodec.fromJson(ProjectCodec.toJson(all)).last())
    }
    @Test fun malformedMetadataAndUnboundedSourcesAreRejected() {
        for (project in listOf(userProject.copy(details = userProject.details!!.copy(updatedAt = 10)),
            userProject.copy(details = userProject.details!!.copy(deleted = true)),
            userProject.copy(details = userProject.details!!.copy(color = "invented")),
            userProject.copy(sources = listOf(source, source)), userProject.copy(sources = listOf(source.copy(size = 0))))) {
            assertThrows(IllegalArgumentException::class.java) { ProjectCodec.validate(ProjectCodec.defaults + project) }
        }
        val data = ProjectCodec.toJson(ProjectCodec.defaults + userProject)
        data.getJSONObject(1).getJSONObject("details").put("pinned", "true")
        assertThrows(IllegalStateException::class.java) { ProjectCodec.fromJson(data) }
    }
}

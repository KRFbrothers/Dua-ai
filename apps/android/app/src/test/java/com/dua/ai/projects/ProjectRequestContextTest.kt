package com.dua.ai.projects

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.files.FileRef
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ProjectRequestContextTest {
    private val source=FileRef("11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222.txt","Notes.txt","text/plain",4)
    private val project=ChatProject("owner-project","Owner project","Use Kotlin",memory=ProjectMemory(true,listOf("Prefer Compose")),sources=listOf(source))
    private fun snapshot(p:ChatProject=project)=CloudSnapshot(emptyList(),listOf("Personal only note"),ProjectCodec.defaults+p)
    @Test fun onlyServerProjectNotesInstructionsAndSourcesEnterTheRequest() {
        val context=ProjectRequestContextPolicy.resolve(project.id,project,snapshot(),personalMemoryEnabled=true)
        assertTrue(context.memory.contains("Prefer Compose"));assertTrue(context.memory.contains("Use Kotlin"))
        assertFalse(context.memory.contains("Personal only note"));assertEquals(listOf(source),context.sources)
    }
    @Test fun unsyncedOrStaleClientContextIsRejectedInsteadOfSilentlySent() {
        for(local in listOf(project.copy(instructions="Local-only instruction"),project.copy(sources=emptyList()),project.copy(memory=ProjectMemory(true,listOf("Local note"))))) {
            assertThrows(IllegalStateException::class.java){ProjectRequestContextPolicy.resolve(project.id,local,snapshot(),false)}
        }
    }
    @Test fun missingArchivedDeletedAndCrossProjectSourcesFailClosed() {
        assertThrows(IllegalStateException::class.java){ProjectRequestContextPolicy.resolve(project.id,project,CloudSnapshot(emptyList(),emptyList()),false)}
        assertThrows(IllegalStateException::class.java){ProjectRequestContextPolicy.resolve(project.id,project.copy(archived=true),snapshot(project.copy(archived=true)),false)}
        val foreign=snapshot().copy(chats=listOf(SavedChat("foreign","Dua","",listOf(ChatMessage("u","Foreign source",true,files=listOf(source))),1,projectId="personal")))
        assertThrows(IllegalStateException::class.java){ProjectRequestContextPolicy.resolve(project.id,project,foreign,false)}
    }
    @Test fun metadataOnlyChangesDoNotBlockContextAndPersonalMemoryUsesServerText() {
        val withDetails=project.copy(details=ProjectDetails(1,2,pinned=true))
        assertEquals(listOf(source),ProjectRequestContextPolicy.resolve(project.id,withDetails,snapshot(),false).sources)
        val home=ProjectCodec.defaults.single()
        val remote=CloudSnapshot(emptyList(),listOf("Server note"))
        assertEquals("- Server note",ProjectRequestContextPolicy.resolve(home.id,home,remote,true).memory)
        assertEquals("",ProjectRequestContextPolicy.resolve(home.id,home,remote,false).memory)
    }
}

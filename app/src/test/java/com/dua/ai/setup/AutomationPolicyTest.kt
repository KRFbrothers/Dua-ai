package com.dua.ai.setup

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class AutomationPolicyTest {
    private val chat = SavedChat("saved", "Dua", "", listOf(ChatMessage(text = "Hi", isUser = true)), 1)

    @Test fun `deletions require review and additions do not`() {
        val initial = CloudSnapshot(listOf(chat), listOf("Prefer Hindi"))
        val baseline = AutomationSyncPolicy.baseline(initial)
        assertTrue(AutomationSyncPolicy.requiresDeletionReview(baseline, initial.copy(chats = emptyList())))
        assertFalse(AutomationSyncPolicy.requiresDeletionReview(baseline, initial.copy(memory = emptyList())))
        assertTrue(AutomationSyncPolicy.requiresDeletionReview(baseline, initial.copy(chats = listOf(chat.copy(messages = emptyList())))))
        assertFalse(AutomationSyncPolicy.requiresDeletionReview(baseline, initial.copy(memory = initial.memory + "Prefer short replies")))
    }

    @Test fun `local memory candidates require explicit statements and reject credentials`() {
        assertEquals(emptyList<String>(), AutomationMemoryCandidates.propose("I prefer Hindi"))
        assertEquals(listOf("Prefer Hindi", "I like tea"), AutomationMemoryCandidates.propose("Remember Prefer Hindi\nYaad rakho I like tea\nRemember my password is private"))
        assertEquals(emptyList<String>(), AutomationMemoryCandidates.propose("Remember Prefer Hindi", listOf("prefer hindi")))
        assertEquals(emptyList<String>(), AutomationMemoryCandidates.propose("Remember " + "x".repeat(201)))
    }

    @Test fun `confirmation preserves notes and retries have a finite budget`() {
        assertEquals(listOf("Prefer Hindi"), AutomationMemoryCandidates.confirm("Prefer Hindi", emptyList()))
        assertEquals(2_000L, AutomationSyncPolicy.retryDelayMillis(1))
        assertEquals(16_000L, AutomationSyncPolicy.retryDelayMillis(4))
        assertNull(AutomationSyncPolicy.retryDelayMillis(5))
    }

    @Test fun `signatures survive imported message ids and contain no plaintext`() {
        val snapshot = CloudSnapshot(listOf(chat), listOf("Personal memory note"))
        val imported = snapshot.copy(chats = listOf(chat.copy(messages = chat.messages.map { it.copy(id = "different") })))
        assertEquals(AutomationSyncPolicy.baseline(snapshot), AutomationSyncPolicy.baseline(imported))
        val baseline = AutomationSyncPolicy.baseline(snapshot)
        assertTrue(baseline.chatSignatures.values.all { it.matches(Regex("[0-9a-f]{64}")) })
        assertFalse(baseline.toString().contains("Personal memory note"))
        assertNotEquals(AutomationSyncPolicy.signature(chat), AutomationSyncPolicy.signature(chat.copy(draft = "Changed draft")))
    }

    @Test fun `signatures include server-backed metadata`() {
        val base = AutomationSyncPolicy.signature(chat)
        val withSummary = chat.copy(summary = com.dua.ai.model.ChatSummary("Earlier", chat.messages.first().id))
        val withBuild = chat.copy(buildRepository = "owner/repo")
        assertNotEquals(base, AutomationSyncPolicy.signature(withSummary))
        assertNotEquals(base, AutomationSyncPolicy.signature(withBuild))
    }

    @Test fun `server wins over the phone cache for the same chat and for memory`() {
        val local = CloudSnapshot(listOf(chat), listOf("Local note"))
        val remote = CloudSnapshot(listOf(chat.copy(messages = listOf(ChatMessage(text = "Other prompt", isUser = true)))), listOf("Server note"))
        val plan = AutomationSyncPolicy.merge(local, remote, null)
        assertFalse(plan.requiresContentReview)
        assertTrue(AutomationSyncPolicy.same(remote, plan.snapshot))
        assertTrue(AutomationSyncPolicy.same(plan.snapshot, AutomationSyncPolicy.merge(plan.snapshot, remote, null).snapshot))
    }

    @Test fun `phone-only chats and memory are kept only while the server is empty`() {
        val local = CloudSnapshot(listOf(chat), listOf("Local note"))
        val empty = AutomationSyncPolicy.merge(local, CloudSnapshot(emptyList(), emptyList()), null)
        assertEquals(listOf("Local note"), empty.snapshot.memory)
        val synced = AutomationSyncPolicy.merge(local, CloudSnapshot(emptyList(), listOf("Server note")), null)
        assertEquals(listOf("Server note"), synced.snapshot.memory)
        assertEquals(1, synced.snapshot.chats.size)
    }

    @Test fun `offline edit is kept as an unsynced copy while the server copy wins`() {
        val base = AutomationSyncPolicy.baseline(CloudSnapshot(listOf(chat), emptyList()))
        val edited = chat.copy(messages = chat.messages + ChatMessage(text = "Offline", isUser = true))
        val remote = CloudSnapshot(listOf(chat.copy(messages = listOf(ChatMessage(text = "Server", isUser = true)))), emptyList())
        val plan = AutomationSyncPolicy.merge(CloudSnapshot(listOf(edited), emptyList()), remote, base)
        assertEquals(2, plan.snapshot.chats.size)
        assertEquals("Server", plan.snapshot.chats.first { it.id == "saved" }.messages.single().text)
        assertTrue(plan.snapshot.chats.any { it.id.startsWith("unsynced-") && it.messages.size == 2 })
        assertEquals(plan.snapshot.chats.map { it.id }, AutomationSyncPolicy.merge(plan.snapshot, remote, base).snapshot.chats.map { it.id }.take(2))
    }

    @Test fun `memory deleted on the phone is deleted from the server copy and new notes are kept`() {
        val base = AutomationSyncPolicy.baseline(CloudSnapshot(emptyList(), listOf("A", "B")))
        val plan = AutomationSyncPolicy.merge(CloudSnapshot(emptyList(), listOf("B", "New")), CloudSnapshot(emptyList(), listOf("A", "B", "C")), base)
        assertEquals(listOf("B", "C", "New"), plan.snapshot.memory)
    }
}

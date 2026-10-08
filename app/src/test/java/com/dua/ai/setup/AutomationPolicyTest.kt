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
        assertTrue(AutomationSyncPolicy.requiresDeletionReview(baseline, initial.copy(memory = emptyList())))
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

    @Test fun `stable conflict copies require review and repeated merge is idempotent`() {
        val local = CloudSnapshot(listOf(chat), emptyList())
        val remote = local.copy(chats = listOf(chat.copy(messages = listOf(ChatMessage(text = "Other prompt", isUser = true)))))
        val first = AutomationSyncPolicy.merge(local, remote, null)
        assertTrue(first.requiresContentReview)
        assertEquals(2, first.snapshot.chats.size)
        assertTrue(first.snapshot.chats.last().id.startsWith("auto-"))
        val repeated = AutomationSyncPolicy.merge(first.snapshot, remote, null)
        assertFalse(repeated.requiresContentReview)
        assertTrue(AutomationSyncPolicy.same(first.snapshot, repeated.snapshot))
    }
}

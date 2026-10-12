package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSavePolicyTest {
    private val saved = SavedChat("one", "Dua", "Draft", listOf(ChatMessage(text = "Hello", isUser = true)), 1)

    @Test fun openingOrReconstructingAnUnchangedChatKeepsItsCloudModificationTime() {
        val reopened = saved.copy(messages = saved.messages.map { it.copy(id = "reconstructed") }, updatedAt = 99)
        assertEquals(1L, ChatSavePolicy.preserveUnchangedTime(saved, reopened).updatedAt)
        assertEquals(99L, ChatSavePolicy.preserveUnchangedTime(null, reopened).updatedAt)
        assertEquals(99L, ChatSavePolicy.preserveUnchangedTime(saved, reopened.copy(id = "copy")).updatedAt)
    }

    @Test fun draftModelAndMessageEditsReceiveANewModificationTime() {
        listOf(saved.copy(draft = "Edited"), saved.copy(model = "Gemini"),
            saved.copy(messages = saved.messages.map { it.copy(text = "Changed") }),
            saved.copy(messages = saved.messages.map { it.copy(isUser = false) }),
            saved.copy(messages = saved.messages.map { it.copy(state = "stopped") }),
            saved.copy(messages = emptyList()), saved.copy(customTitle = "Renamed")).forEach { changed ->
            assertEquals(99L, ChatSavePolicy.preserveUnchangedTime(saved, changed.copy(updatedAt = 99)).updatedAt)
        }
    }
}

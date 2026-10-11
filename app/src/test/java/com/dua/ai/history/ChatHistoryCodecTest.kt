package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ChatHistoryCodecTest {
    private val first = SavedChat("one", "Gemini", "अधूरा", listOf(
        ChatMessage(id = "message", text = "नमस्ते", isUser = true, model = "Gemini")
    ), 10)
    @Test fun multipleChatsAndActiveSelectionRoundTrip() {
        val records = listOf(first, SavedChat("two", "Dua", "Second draft", emptyList(), 20))
        val (active, restored) = ChatHistoryCodec.decode(ChatHistoryCodec.encode(records, "two"))
        assertEquals("two", active)
        assertEquals(records, restored)
        assertEquals("नमस्ते", restored[0].title)
        assertEquals("Draft: अधूरा", restored[0].preview)
    }
    @Test fun newEmptyChatDoesNotDestroyOlderRecords() {
        val (active, restored) = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(first), "new"))
        assertEquals("new", active)
        assertEquals(listOf(first), restored)
    }
    @Test fun stoppedBeforeFirstTokenSurvivesReloadAndKeepsPromptPreview() {
        val prompt = ChatMessage(text = "Explain this", isUser = true)
        val stopped = ChatMessage(text = "", isUser = false, state = "stopped")
        val chat = SavedChat("stopped", "Dua", "", listOf(prompt, stopped), 20)
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), chat.id)).second.single()
        assertEquals(listOf(prompt, stopped), restored.messages)
        assertEquals(prompt.text, restored.preview)
        assertEquals("Draft: Try another question", restored.copy(draft = "Try another question").preview)
    }
    @Test fun deletedChatStaysDeletedAfterReload() {
        val records = listOf(first, first.copy(id = "two"))
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(records.filterNot { it.id == "one" }, "two")).second
        assertEquals(listOf("two"), restored.map { it.id })
    }
    @Test fun corruptDataIsRejected() {
        assertTrue(runCatching { ChatHistoryCodec.decode("broken") }.isFailure)
        assertTrue(runCatching { ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(first.copy(model = "unsupported")), "one")) }.isFailure)
    }
}

class ChatHistoryThinkingTest {
    private val reply = ChatMessage(id = "r", text = "Answer", isUser = false, model = "Gemini", thinking = "First I checked…", thinkingSeconds = 12)
    private val user = ChatMessage(id = "u", text = "Why?\nFiles: a.csv", isUser = true, model = "Gemini", attachmentText = "Attached text file (a.csv):\n1,2")
    private val chat = SavedChat("one", "Gemini", "", listOf(user, reply), 10)

    @Test fun thinkingAndLocalFileTextRoundTripLocally() {
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), "one")).second.single()
        assertEquals(listOf(user, reply), restored.messages)
    }
    @Test fun exportAndCloudKeepThinkingAndExtractedFileText() {
        val export = ChatHistoryCodec.export(listOf(chat), 1)
        assertTrue(export.contains("attachmentText")); assertTrue(export.contains("1,2"))
        val imported = ChatHistoryCodec.parseImport(export).single().messages
        assertEquals("First I checked…", imported[1].thinking); assertEquals(12, imported[1].thinkingSeconds)
        assertEquals(user.attachmentText, imported[0].attachmentText)
        val cloud = com.dua.ai.account.CloudSnapshotCodec.decode(com.dua.ai.account.CloudSnapshotCodec.encode(com.dua.ai.account.CloudSnapshot(listOf(chat), emptyList())))
        assertEquals(12, cloud.chats.single().messages[1].thinkingSeconds)
        assertEquals(1, org.json.JSONObject(export).getInt("version"))
    }
    @Test fun olderSavesWithoutThinkingStillLoad() {
        val old = """{"activeId":"one","chats":[{"id":"one","model":"Dua","draft":"","updatedAt":1,"messages":[{"id":"m","text":"Hi","isUser":false,"model":"Dua","state":"complete"}]}]}"""
        val message = ChatHistoryCodec.decode(old).second.single().messages.single()
        assertNull(message.thinking); assertNull(message.thinkingSeconds); assertNull(message.attachmentText)
        val oldExport = """{"format":"dua-chat-export","version":1,"exportedAt":0,"chats":[{"id":"c","model":"Grok","draft":"","updatedAt":1,"messages":[{"role":"model","text":"Hi","state":"complete"}]}]}"""
        assertNull(ChatHistoryCodec.parseImport(oldExport).single().messages.single().thinking)
    }
    @Test fun streamingRepliesSaveAsStoppedAndEmptyPlaceholdersAreDropped() {
        val streaming = SavedChat("s", "Grok", "", listOf(user, reply.copy(state = ChatMessage.STREAMING, text = "Half")), 1)
        assertEquals("stopped", ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(streaming), "s")).second.single().messages[1].state)
        val placeholder = streaming.copy(messages = listOf(user, ChatMessage(text = "", isUser = false, state = ChatMessage.STREAMING)))
        assertEquals(1, ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(placeholder), "s")).second.single().messages.size)
        assertEquals(1, ChatHistoryCodec.parseImport(ChatHistoryCodec.export(listOf(placeholder))).single().messages.size)
    }
    @Test fun invalidThinkingFieldsInImportsAreRejected() {
        val bad = """{"format":"dua-chat-export","version":1,"exportedAt":0,"chats":[{"id":"c","model":"Grok","draft":"","updatedAt":1,"messages":[{"role":"model","text":"Hi","state":"complete","thinkingSeconds":-1}]}]}"""
        assertTrue(runCatching { ChatHistoryCodec.parseImport(bad) }.isFailure)
    }
}

package com.dua.ai.model

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.ReplyEvent
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Phase 2: long-context window and summaries, structured sources, X search request fields. */
class LongContextSearchTest {
    private fun turns(count: Int, size: Int = 10) = (0 until count).map { ChatMessage(id = "m$it", text = (if (it % 2 == 0) "q$it " else "a$it ") + "x".repeat(size), isUser = it % 2 == 0) }
    private val bytes: (ChatMessage) -> Int = DuaApiClient::turnJsonBytes

    @Test fun windowSendsEveryTurnAfterTheSummaryBoundary() {
        val chat = turns(60) + ChatMessage(id = "new", text = "latest", isUser = true)
        val full = ContextWindow.select(chat, null, 2 * 1024 * 1024, bytes)
        assertEquals("no 39-turn cut", 61, full.turns.size)
        val summary = ChatSummary("Earlier: x", "m19")
        val after = ContextWindow.select(chat, summary, 2 * 1024 * 1024, bytes)
        assertEquals("m20", after.turns.first().id)
        assertEquals(41, after.turns.size)
        assertEquals(summary, after.summary)
        // A summary whose boundary message is gone is ignored.
        assertNull(ContextWindow.select(chat, ChatSummary("x", "deleted"), 1_000_000, bytes).summary)
    }

    @Test fun windowSkipsUnfinishedRepliesDropsOldestOverBudgetAndStartsWithUser() {
        val chat = listOf(ChatMessage(id = "u0", text = "hi", isUser = true), ChatMessage(id = "p", text = "partial", isUser = false, state = "stopped")) +
            turns(20, 1000).drop(1) + ChatMessage(id = "last", text = "now?", isUser = true)
        val small = ContextWindow.select(chat, null, 5_000, bytes)
        assertTrue(small.turns.none { it.id == "p" })
        assertTrue(small.turns.first().isUser)
        assertEquals("last", small.turns.last().id)
        assertTrue(small.dropped > 0)
        assertTrue(small.turns.sumOf { bytes(it) + 1 } <= 5_000)
        val capped = ContextWindow.select(turns(450) + ChatMessage(id = "z", text = "z", isUser = true), null, 10_000_000, bytes)
        assertTrue(capped.turns.size <= ContextWindow.MAX_TURNS)
    }

    @Test fun summaryAdvancesToTheLastCoveredSentTurn() {
        val sent = turns(10) + ChatMessage(id = "new", text = "q", isUser = true)
        assertEquals(ChatSummary("S", "m5"), ContextWindow.advance(sent, 6, "S"))
        assertNull("latest turn is never covered", ContextWindow.advance(sent, 11, "S"))
        assertNull(ContextWindow.advance(sent, 0, "S"))
        assertNull(ContextWindow.advance(sent, 3, " "))
    }

    @Test fun summarySurvivesExportAndReferencesTheCopiedMessageOnImport() {
        val chat = SavedChat("c1", "Grok", "", turns(4), 1L, ChatSummary("Earlier bits", "m1"))
        val (_, decoded) = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), "c1"))
        assertEquals(ChatSummary("Earlier bits", "m1"), decoded.single().summary)
        assertEquals(chat.summary, ChatHistoryCodec.parseImport(ChatHistoryCodec.export(listOf(chat))).single().summary)
        val imported = ChatHistoryCodec.importCopies(emptyList(), listOf(chat)).single()
        assertEquals(chat.summary!!.text, imported.summary!!.text)
        assertEquals(imported.messages[1].id, imported.summary!!.throughId)
        val legacy = JSONObject(ChatHistoryCodec.encode(listOf(chat.copy(summary = null)), "c1"))
        assertNull(ChatHistoryCodec.decode(legacy.toString()).second.single().summary)
    }

    @Test fun requestCarriesContextAndXSearchOnlyWhenAsked() {
        val api = DuaApiClient("https://dua.example/")
        val history = turns(60) + ChatMessage(text = "now", isUser = true)
        fun body(request: okhttp3.Request) = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        val long = body(api.buildRequest("Grok", history, emptyList(), "", null, "search", false, stream = true,
            historyBytes = DuaApiClient.LONG_HISTORY_BYTES, context = ConversationContext("chat-1234-5678", "S"), xSearch = true))
        assertEquals(61, long.getJSONArray("messages").length())
        assertEquals("chat-1234-5678", long.getJSONObject("context").getString("id"))
        assertEquals("S", long.getJSONObject("context").getString("summary"))
        assertTrue(long.getBoolean("xSearch"))
        val legacy = body(api.buildRequest("Gemini", history, emptyList(), "", null, null, false, stream = true, xSearch = true))
        assertEquals("older servers keep the 39-turn window", 39, legacy.getJSONArray("messages").length())
        assertFalse(legacy.has("context"))
        assertFalse("X search is Grok only", legacy.has("xSearch"))
        val noSummary = body(api.buildRequest("Dua", history, emptyList(), "", null, null, false, stream = true, context = ConversationContext("chat-1234-5678", null)))
        assertFalse(noSummary.getJSONObject("context").has("summary"))
        assertThrows(IllegalArgumentException::class.java) { ConversationContext("bad id", null) }
    }

    @Test fun streamDecodesStructuredSourcesAndContextEvents() {
        val parser = ChatStreamParser()
        fun one(json: String) = parser.line("data: $json").let { parser.line("") }
        val sources = one("""{"type":"sources","sources":[{"id":1,"title":"uefa.com","url":"https://vertexaisearch.cloud.google.com/r/1","domain":"uefa.com"},{"id":2,"title":"x","url":"http://insecure.example"}]}""") as ReplyEvent.Sources
        assertEquals(1, sources.count)
        assertEquals("uefa.com", sources.sources.single().domain)
        assertEquals(ReplyEvent.Context("Summary", 12), one("""{"type":"context","summary":"Summary","covered":12}"""))
        assertNull(one("""{"type":"context","summary":"","covered":3}"""))
    }

    @Test fun sourceChipsUseTheRealSiteForGoogleRedirectsAndShowPageTitles() {
        val gemini = ReplySource(1, "uefa.com", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc")
        assertEquals("uefa.com", gemini.domain)
        assertNull("domain-only titles are not repeated", gemini.pageTitle)
        val openai = ReplySource(2, "BBC Weather", "https://www.bbc.com/weather")
        assertEquals("bbc.com", openai.domain)
        assertEquals("BBC Weather", openai.pageTitle)
        val parts = ReplySources.split("Answer\n\nSources:\n[1] uefa.com\nhttps://vertexaisearch.cloud.google.com/grounding-api-redirect/abc")
        assertEquals("uefa.com", parts.sources.single().domain)
    }

    @Test fun configAdvertisesNativeSearchXSearchAndLongContext() {
        val json = JSONObject("""{"providers":[],"web":{"searchConfigured":false,"pageReadingAvailable":true,"nativeSearch":["Dua","Grok","Evil"],"xSearch":true},"context":{"summaries":true}}""")
        val state = IntegrationCapabilities.configuration(json, IntegrationState())
        assertEquals(setOf("Dua", "Grok"), state.nativeSearch)
        assertTrue(state.searchAvailable("Grok"))
        assertFalse("Claude needs Brave", state.searchAvailable("Claude"))
        assertTrue(state.xSearchAvailable)
        assertTrue(state.longContext)
        val old = IntegrationCapabilities.configuration(JSONObject("""{"providers":[],"web":{"searchConfigured":true}}"""), IntegrationState())
        assertTrue(old.searchAvailable("Claude"))
        assertFalse(old.longContext || old.xSearchAvailable)
    }
}

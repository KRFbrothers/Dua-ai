package com.dua.ai.model

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.ReplyEvent
import com.dua.ai.network.StreamAccumulator
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import com.dua.ai.ui.chat.buildFilesLabel
import com.dua.ai.ui.chat.highlightDiff
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Build mode (Phase 5B, read-only): `build` stream events, request fields, local history, config and diff colouring. */
class BuildModeTest {
    private fun parse(vararg lines: String): List<ReplyEvent> {
        val parser = ChatStreamParser()
        return lines.mapNotNull { parser.line(it) } + listOfNotNull(parser.finish())
    }

    @Test fun buildEventsDecodeAndUnsafeEntriesAreDropped() {
        val events = parse(
            "event: build", "data: {\"type\":\"build\",\"repository\":\"KRFbrothers/Dua-ai\",\"branch\":\"main\",\"files\":[{\"path\":\"README.md\",\"bytes\":30},{\"path\":\"../etc/passwd\"},{\"path\":\"/abs\"},{\"path\":\"web/a.mjs\",\"bytes\":102400,\"truncated\":true},{\"path\":\"README.md\"}],\"rounds\":2}", "",
            "event: build", "data: {\"type\":\"build\",\"repository\":\"not a repo\",\"files\":[]}", "",
        )
        val info = (events.single() as ReplyEvent.Build).info
        assertEquals("KRFbrothers/Dua-ai", info.repository)
        assertEquals("main", info.branch)
        assertEquals(listOf(BuildFile("README.md", 30), BuildFile("web/a.mjs", 102400, true)), info.files)
        assertEquals("Dua-ai", info.shortName)
    }

    @Test fun accumulatorKeepsTheLatestBuildEvent() {
        val accumulator = StreamAccumulator(0L) { 0L }
        accumulator.apply(ReplyEvent.Build(BuildInfo("o/r", "main")))
        accumulator.apply(ReplyEvent.Build(BuildInfo("o/r", "main", listOf(BuildFile("a.kt", 10)))))
        accumulator.apply(ReplyEvent.Text("```diff\n-a\n+b\n```"))
        assertEquals(listOf(BuildFile("a.kt", 10)), accumulator.snapshot().build!!.files)
    }

    @Test fun requestSendsBuildModeAndRepositoryOnlyOnTheStreamingPath() {
        val api = DuaApiClient("https://dua.example/")
        val history = listOf(ChatMessage(text = "Explain the router", isUser = true))
        fun body(stream: Boolean, build: String?, code: Boolean = false) = JSONObject(Buffer().also {
            api.buildRequest("Dua", history, emptyList(), "", null, null, true, stream, code = code, build = build).body!!.writeTo(it) }.readUtf8())
        val build = body(stream = true, build = "KRFbrothers/Dua-ai", code = true)
        assertEquals("build", build.getString("mode"))
        assertEquals("KRFbrothers/Dua-ai", build.getString("repository"))
        assertFalse("Run code is never combined with Build", build.has("code"))
        assertEquals("reasoning", body(stream = true, build = null).getString("mode"))
        assertFalse(body(stream = true, build = null).has("repository"))
        assertFalse(body(stream = false, build = "KRFbrothers/Dua-ai").has("repository"))
        assertThrows(IllegalArgumentException::class.java) { body(stream = true, build = "../../etc") }
    }

    @Test fun buildRepositoryAndFilesStayInLocalHistoryOnly() {
        val reply = ChatMessage(id = "r", text = "```diff\n-a\n+b\n```", isUser = false, model = "Dua",
            build = BuildInfo("KRFbrothers/Dua-ai", "main", listOf(BuildFile("app/src/main/java/com/dua/ai/Secret.kt", 120))))
        val chat = SavedChat("c", "Dua", "", listOf(ChatMessage(id = "u", text = "Fix it", isUser = true, model = "Dua"), reply), 5, buildRepository = "KRFbrothers/Dua-ai")
        val restored = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), "c")).second.single()
        assertEquals("KRFbrothers/Dua-ai", restored.buildRepository)
        assertEquals(reply.build, restored.messages[1].build)
        val exported = ChatHistoryCodec.export(listOf(chat))
        assertFalse(exported.contains("Secret.kt")); assertFalse(exported.contains("buildRepository"))
        val legacy = JSONObject(ChatHistoryCodec.encode(listOf(chat), "c"))
        legacy.getJSONArray("chats").getJSONObject(0).put("buildRepository", "bad repo")
        assertNull(ChatHistoryCodec.decode(legacy.toString()).second.single().buildRepository)
    }

    @Test fun configNeedsReadOnlyAndExplainsWhyItIsOff() {
        val on = IntegrationCapabilities.configuration(JSONObject("{\"build\":{\"enabled\":true,\"readOnly\":true,\"providers\":[\"Dua\",\"Gemini\"],\"maxFiles\":40}}"), IntegrationState())
        assertTrue(on.build!!.available("Gemini")); assertFalse(on.build!!.available("ChatGPT"))
        val notReadOnly = IntegrationCapabilities.configuration(JSONObject("{\"build\":{\"enabled\":true,\"providers\":[\"Dua\"]}}"), IntegrationState())
        assertFalse("the app only enables Build when the server promises read-only", notReadOnly.build!!.enabled)
        val off = IntegrationCapabilities.configuration(JSONObject("{\"build\":{\"enabled\":false,\"reason\":\"Build mode needs the GitHub connector.\",\"providers\":[\"Dua\"]}}"), IntegrationState())
        assertEquals("Build mode needs the GitHub connector.", off.build!!.reason)
        assertNull(IntegrationCapabilities.configuration(JSONObject("{}"), IntegrationState()).build)
    }

    @Test fun repositoriesCarryTheFullName() {
        val list = IntegrationCapabilities.repositories(JSONObject("{\"repositories\":[{\"name\":\"Dua-ai\",\"fullName\":\"KRFbrothers/Dua-ai\",\"private\":false,\"url\":\"https://github.com/KRFbrothers/Dua-ai\"}," +
            "{\"name\":\"old\",\"url\":\"https://github.com/KRFbrothers/old\"},{\"name\":\"x\",\"url\":\"https://evil.example/a/b\"}]}"))
        assertEquals(listOf("KRFbrothers/Dua-ai", "KRFbrothers/old"), list.map { it.fullName })
    }

    @Test fun fileLinksAndLabels() {
        val info = BuildInfo("KRFbrothers/Dua-ai", "feature/x", listOf(BuildFile("docs/My Notes.md", 5)))
        assertEquals("https://github.com/KRFbrothers/Dua-ai/blob/feature/x/docs/My%20Notes.md", info.fileUrl(info.files[0]))
        assertEquals("https://github.com/o/r/blob/HEAD/a.kt", BuildInfo("o/r").fileUrl(BuildFile("a.kt")))
        assertEquals("Reading Dua-ai…", buildFilesLabel(info.copy(files = emptyList()), live = true))
        assertEquals("Read 1 file · Dua-ai", buildFilesLabel(info, live = false))
        assertEquals("No files read · Dua-ai", buildFilesLabel(info.copy(files = emptyList()), live = false))
    }

    @Test fun diffLinesAreColouredByKind() {
        val diff = "--- a/x\n+++ b/x\n@@ -1 +1 @@\n-old\n+new\n same"
        val styled = highlightDiff(diff, dark = true)
        assertEquals(diff, styled.text)
        fun styleAt(text: String) = styled.spanStyles.filter { diff.indexOf(text) in it.start until it.end }.map { it.item }
        assertNotNull(styleAt("+new").single().background)
        assertNotEquals(styleAt("+new").single().color, styleAt("-old").single().color)
        assertTrue(styleAt(" same").isEmpty())
    }
}

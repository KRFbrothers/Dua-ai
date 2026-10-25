package com.dua.ai.network

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.ui.agent.AgentFilesContent
import com.dua.ai.ui.agent.AgentFilesState
import com.dua.ai.ui.agent.FILES_DISABLED_MESSAGE
import com.dua.ai.ui.theme.DuaTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class AgentDocumentsApiTest {
    private fun api(server: MockWebServer) = AgentDocumentsApi(server.url("/").toString(), appToken = "app-token", allowHttp = true)

    @Test fun listSendsEngineAuthAndParsesOnlyDocuments() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"documents":[{"id":"a1","name":"notes.txt","kind":"document"},{"id":"n1","name":"memo","kind":"note"}]}"""))
            val result = api(server).list("session.token")
            assertEquals(AgentDocumentsResult.Ok(listOf(AgentDocument("a1", "notes.txt", "document"))), result)
            val req = server.takeRequest()
            assertEquals("GET", req.method); assertEquals("/api/agent/documents", req.path)
            assertEquals("Bearer session.token", req.getHeader("Authorization")); assertEquals("app-token", req.getHeader("X-Dua-App-Token"))
        }
    }

    @Test fun addPostsNameAndTextAndDeletePostsId() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":"a1","chunks":1}"""))
            server.enqueue(MockResponse().setBody("""{"deleted":true}"""))
            assertTrue(api(server).add("a.txt", "hello", "t") is AgentDocumentsResult.Ok)
            assertEquals(AgentDocumentsResult.Ok(true), api(server).delete("a1", "t"))
            val add = server.takeRequest(); val body = JSONObject(add.body.readUtf8())
            assertEquals("POST", add.method); assertEquals("a.txt", body.getString("name")); assertEquals("hello", body.getString("text"))
            assertFalse(body.has("query"))
            val del = server.takeRequest()
            assertEquals("DELETE", del.method); assertEquals("a1", JSONObject(del.body.readUtf8()).getString("id"))
        }
    }

    @Test fun notConfiguredIsDisabledButOther503IsAFailure() {
        assertEquals(AgentDocumentsResult.Disabled, AgentDocumentsApi.classify(503, """{"error":"ADK document storage/embeddings are not configured."}""") { })
        assertTrue(AgentDocumentsApi.classify(503, """{"error":"Document storage or embeddings are unavailable."}""") { } is AgentDocumentsResult.Failed)
        assertTrue(AgentDocumentsApi.classify(403, "{}") { } is AgentDocumentsResult.Failed)
        assertTrue(AgentDocumentsApi.classify(200, "not json") { AgentDocumentsApi.parseList(it) } is AgentDocumentsResult.Failed)
    }

    @Test fun plainHttpIsRefusedOutsideTests() {
        assertNull(AgentDocumentsApi("http://engine.example", appToken = "").buildRequest("GET", null, null))
        assertNotNull(AgentDocumentsApi("https://engine.example", appToken = "").buildRequest("GET", null, "bad token!")?.also {
            assertNull(it.header("Authorization")); assertNull(it.header("X-Dua-App-Token")) })
    }

    @Test fun extractTextEnforcesUtf8AndEngineLimit() {
        assertEquals("héllo", AgentDocumentsApi.extractText("\uFEFFhéllo".toByteArray()).getOrThrow())
        assertTrue(AgentDocumentsApi.extractText("%PDF-1.7\n".toByteArray() + byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte())).isFailure)
        assertTrue(AgentDocumentsApi.extractText(byteArrayOf(0x61, 0x00, 0x62)).isFailure)
        assertTrue(AgentDocumentsApi.extractText("   ".toByteArray()).isFailure)
        assertTrue(AgentDocumentsApi.extractText("a".repeat(AgentDocumentsApi.MAX_DOCUMENT_CHARS).toByteArray()).isSuccess)
        assertTrue(AgentDocumentsApi.extractText("a".repeat(AgentDocumentsApi.MAX_DOCUMENT_CHARS + 1).toByteArray()).isFailure)
    }

    @Test fun documentNameIsShortAndSingleLine() {
        assertEquals("Untitled file", AgentDocumentsApi.documentName(null))
        assertEquals("a b", AgentDocumentsApi.documentName("a\nb"))
        assertEquals(200, AgentDocumentsApi.documentName("x".repeat(500)).length)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentFilesContentTest {
    @get:Rule val rule = createComposeRule()

    @Test fun disabledShowsFriendlyMessageAndHidesAdd() {
        rule.setContent { DuaTheme { AgentFilesContent(AgentFilesState(disabled = true), {}, {}, {}) } }
        rule.onNodeWithText(FILES_DISABLED_MESSAGE).assertExists()
        rule.onNodeWithTag("files-add").assertDoesNotExist()
    }

    @Test fun deleteAsksForConfirmation() {
        var deleted: AgentDocument? = null
        val doc = AgentDocument("a1", "notes.txt", "document")
        rule.setContent { DuaTheme { AgentFilesContent(AgentFilesState(documents = listOf(doc)), {}, {}, { deleted = it }) } }
        rule.onNodeWithContentDescription("Delete notes.txt").performClick()
        assertNull(deleted)
        rule.onNodeWithText("Delete").performClick()
        assertEquals(doc, deleted)
    }
}

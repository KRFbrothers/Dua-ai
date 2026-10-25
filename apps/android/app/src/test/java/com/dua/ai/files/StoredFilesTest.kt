package com.dua.ai.files

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.DuaApiClient
import com.dua.ai.network.ReplyEvent
import com.dua.ai.network.StreamAccumulator
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredFilesTest {
    private val uid = "11111111-2222-4333-8444-555555555555"
    private val path = "$uid/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf"
    private val audioPath = "$uid/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.m4a"

    @Test fun storedTypesMatchTheServerList() {
        assertEquals("application/pdf", StoredFileTypes.storedType("a.pdf", "application/pdf"))
        assertEquals("audio/mp4", StoredFileTypes.storedType("memo.m4a", "application/octet-stream"))
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", StoredFileTypes.storedType("q.xlsx", ""))
        assertEquals("text/csv", StoredFileTypes.storedType("a.csv", "text/csv; charset=utf-8"))
        assertNull(StoredFileTypes.storedType("main.kt", "text/x-kotlin"))
        assertNull(StoredFileTypes.storedType("clip.mp4", "video/mp4"))
        assertTrue(StoredFileTypes.needsStorage("memo.mp3", "audio/mpeg"))
        assertTrue(StoredFileTypes.needsStorage("q.xlsx", ""))
        assertFalse(StoredFileTypes.needsStorage("a.csv", "text/csv"))
        assertFalse(StoredFileTypes.needsStorage("a.pdf", "application/pdf"))
        assertEquals("12 MB", StoredFileTypes.sizeLabel(12L * 1024 * 1024))
        assertEquals("4.5 MB", StoredFileTypes.sizeLabel(4_718_592))
    }

    @Test fun historyKeepsFileRefsAndTranscriptsLocally() {
        val message = ChatMessage(text = "Summarise\nFiles: invoice.pdf, note.m4a", isUser = true, model = "Dua",
            files = listOf(FileRef(path, "invoice.pdf", "application/pdf", 1200), FileRef(audioPath, "note.m4a", "audio/mp4", 300, "Call Ammi at six")))
        val (_, chats) = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(SavedChat("c1", "Dua", "", listOf(message), 1L)), "c1"))
        assertEquals(message.files, chats.single().messages.single().files)
        // Export stores references/transcripts, with file bytes remaining in private Storage.
        assertEquals(message.files, ChatHistoryCodec.parseImport(ChatHistoryCodec.export(chats)).single().messages.single().files)
    }

    @Test fun invalidStoredRefsAreDroppedNotFatal() {
        val refs = FileRefCodec.fromArray(JSONArray().put(JSONObject().put("path", "../x").put("name", "a").put("mimeType", "application/pdf"))
            .put(JSONObject().put("path", path).put("name", "a.pdf").put("mimeType", "application/x-msdownload"))
            .put(JSONObject().put("path", path).put("name", "a.pdf").put("mimeType", "application/pdf").put("size", 5)))
        assertEquals(listOf(FileRef(path, "a.pdf", "application/pdf", 5)), refs)
    }

    @Test fun streamParserReadsTranscriptsAndAccumulatorIgnoresThem() {
        val event = ChatStreamParser.decode("""{"type":"files","files":[{"path":"$audioPath","transcript":"hello"},{"path":"bad","transcript":"x"}]}""")
        assertEquals(ReplyEvent.Files(mapOf(audioPath to "hello")), event)
        val accumulator = StreamAccumulator(0L) { 0L }
        accumulator.apply(event!!)
        assertEquals("", accumulator.snapshot().text)
        assertNull(ChatStreamParser.decode("""{"type":"files","files":[]}"""))
    }

    @Test fun requestsSendFileRefsOnlyOnTheStreamingPath() {
        val history = listOf(
            ChatMessage(text = "Read this\nFiles: invoice.pdf", isUser = true, files = listOf(FileRef(path, "invoice.pdf", "application/pdf", 10))),
            ChatMessage(text = "Done", isUser = false),
            ChatMessage(text = "And this?\nFiles: note.m4a", isUser = true, files = listOf(FileRef(audioPath, "note.m4a", "audio/mp4", 20, "hi"))))
        val stream = DuaApiClient.historyJson(history, includeFileText = true, maxBytes = DuaApiClient.FILES_HISTORY_BYTES)
        assertEquals(path, stream.getJSONObject(0).getJSONArray("files").getJSONObject(0).getString("path"))
        assertFalse(stream.getJSONObject(1).has("files"))
        assertEquals("hi", stream.getJSONObject(2).getJSONArray("files").getJSONObject(0).getString("transcript"))
        val legacy = DuaApiClient.historyJson(history, includeFileText = false)
        assertFalse((0 until legacy.length()).any { legacy.getJSONObject(it).has("files") })
    }

    @Test fun configAdvertisesStoredFiles() {
        val state = IntegrationCapabilities.configuration(JSONObject().put("providers", JSONArray()).put("files", JSONObject().put("enabled", true).put("maxBytes", 52428800)), IntegrationState())
        assertTrue(state.filesEnabled)
        assertEquals(52428800L, state.filesMaxBytes)
        // Older servers have no `files` object: stored files stay off and the inline path is used.
        assertFalse(IntegrationCapabilities.configuration(JSONObject().put("providers", JSONArray()), IntegrationState()).filesEnabled)
    }

    @Test fun signedUploadResponseIsValidated() {
        val good = JSONObject().put("path", path).put("uploadUrl", "https://proj.supabase.co/storage/v1/object/upload/sign/dua-files/$path?token=t")
            .put("headers", JSONObject().put("Content-Type", "application/pdf").put("x-upsert", "false").put("apikey", "anon").put("Authorization", "nope"))
            .put("mimeType", "application/pdf").put("maxBytes", 52428800)
        val signed = SignedUpload.parse(good)
        assertEquals(setOf("Content-Type", "x-upsert", "apikey"), signed.headers.keys)
        assertThrows(IllegalArgumentException::class.java) { SignedUpload.parse(JSONObject(good.toString()).put("uploadUrl", "http://proj.supabase.co/x")) }
        assertThrows(IllegalArgumentException::class.java) { SignedUpload.parse(JSONObject(good.toString()).put("path", "../../etc")) }
    }

    @Test fun uploadStreamsBytesWithHeadersAndReportsProgress() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val server = MockWebServer().apply { useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false); start() }
        val tls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val client = OkHttpClient.Builder().sslSocketFactory(tls.sslSocketFactory(), tls.trustManager).build()
        try {
            server.enqueue(MockResponse().setBody("""{"Key":"dua-files/$path"}"""))
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"statusCode":"413","error":"Payload too large","message":"The object exceeded the maximum allowed size"}"""))
            val bytes = ByteArray(300_000) { (it % 251).toByte() }
            val signed = SignedUpload(path, server.url("/storage/v1/object/upload/sign/dua-files/$path?token=t").toString(),
                mapOf("Content-Type" to "application/pdf", "x-upsert" to "false", "apikey" to "anon"), "application/pdf", 52428800)
            val progress = mutableListOf<Long>()
            StoredFileClient(client).upload(signed, bytes.size.toLong(), { bytes.inputStream() }) { progress += it }
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("application/pdf", request.getHeader("Content-Type"))
            assertEquals("false", request.getHeader("x-upsert"))
            assertEquals("anon", request.getHeader("apikey"))
            assertEquals(bytes.size.toLong(), request.bodySize)
            assertEquals(bytes.size.toLong(), progress.last())
            assertTrue(progress.size >= 2)
            val failure = runCatching { StoredFileClient(client).upload(signed, bytes.size.toLong(), { bytes.inputStream() }) {} }.exceptionOrNull()
            assertEquals("File is larger than the storage limit.", failure?.message)
        } finally { client.connectionPool.evictAll(); server.shutdown() }
    }

    @Test fun pendingDeletesPersistPerAccount() {
        var stored: String? = null
        val deletes = PendingFileDeletes({ stored }, { stored = it })
        val other = "99999999-2222-4333-8444-555555555555/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf"
        deletes.add(listOf(path, path, other, "not-a-path"))
        assertEquals(listOf(path, other), deletes.all())
        assertEquals(listOf(path), deletes.forUser(uid))
        deletes.remove(listOf(path))
        assertEquals(listOf(other), PendingFileDeletes({ stored }, { stored = it }).all())
    }
}

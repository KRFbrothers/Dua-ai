package com.dua.ai.backup

import android.app.Application
import android.net.Uri
import com.dua.ai.account.*
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.FileRef
import com.dua.ai.history.ChatBackup
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.projects.ProjectCodec
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FullBackupFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = "11111111-2222-4333-8444-555555555555"
    private val path = "$owner/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf"
    private val raw = byteArrayOf(0, -1, 8, 4, 2, 127)
    private val cloud = ConcurrentHashMap<String, ByteArray>()
    private val uploads = mutableListOf<String>()
    private var refuseUpload = false
    private var failAfterUploads: Int? = null
    private var downloadStarted: CountDownLatch? = null
    private var releaseDownload: CountDownLatch? = null
    private var truncated = false
    private var currentOwner: String? = owner
    private var imported: ChatBackup? = null
    private var preflights = 0
    private lateinit var controller: FullBackupController
    private lateinit var scope: CoroutineScope
    private val password get() = "a unique backup password".toCharArray()

    private fun setup(inline: Boolean = false) {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = if (request.url.host == "api.test") {
                assertEquals("Bearer owner-token", request.header("Authorization"))
                val body = if (request.body != null) JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8()) else JSONObject()
                val json = when (request.url.encodedPath) {
                    "/api/files/list" -> JSONObject().put("truncated", truncated).put("files", JSONArray().apply {
                        cloud.forEach { (key, bytes) -> put(JSONObject().put("path", key).put("name", "Original.pdf").put("mimeType", "application/pdf").put("size", bytes.size)) }
                    })
                    "/api/files/url" -> JSONObject().put("url", "https://storage.test/${body.getString("path")}")
                    "/api/files/restore-sign" -> JSONObject().put("path", body.getString("path")).put("uploadUrl", "https://storage.test/${body.getString("path")}")
                        .put("mimeType", body.getString("mimeType")).put("maxBytes", 50 * 1024 * 1024)
                        .put("headers", JSONObject().put("Content-Type", body.getString("mimeType")).put("x-upsert", "false"))
                    else -> error("Unexpected API request: ${request.url}")
                }
                json.toString().toResponseBody("application/json".toMediaType())
            } else {
                assertEquals("storage.test", request.url.host)
                assertNull("Owner bearer token must never reach the signed URL", request.header("Authorization"))
                val key = request.url.encodedPath.removePrefix("/")
                if (request.method == "PUT") {
                    assertEquals("false", request.header("x-upsert"))
                    if (refuseUpload || failAfterUploads?.let { uploads.size >= it } == true) return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable").body("retry".toResponseBody()).build()
                    assertFalse("Restore must never overwrite", cloud.containsKey(key))
                    cloud[key] = Buffer().also { request.body!!.writeTo(it) }.readByteArray()
                    synchronized(uploads) { uploads += key }
                    "{}".toResponseBody()
                } else {
                    downloadStarted?.countDown()
                    releaseDownload?.let { check(it.await(5, TimeUnit.SECONDS)) }
                    (cloud[key] ?: error("Missing object $key")).toResponseBody()
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", if (request.url.host == "api.test") "application/json" else "application/octet-stream").body(response).build()
        }.build()
        val session = AccountSession(AccountUser(owner, "owner@example.test", "Owner"), "owner-token", "refresh-token", Long.MAX_VALUE)
        val account = AccountRepository(AccountApi("https://api.test", client), object : SessionPersistence {
            override fun read() = session
            override fun write(value: AccountSession?) = Unit
        })
        val message = ChatMessage(id = "message", text = "Read this", isUser = true, model = "Dua",
            files = if (inline) emptyList() else listOf(FileRef(path, "Original.pdf", "application/pdf", raw.size.toLong())),
            inlineAttachments = if (inline) listOf(ChatAttachment("Inline.pdf", "application/pdf", Base64.getEncoder().encodeToString(raw))) else emptyList())
        val snapshot = FullBackupSnapshot(ChatBackup(listOf(SavedChat("chat", "Dua", "draft", listOf(message), 123)), ProjectCodec.defaults), listOf("Personal note"), owner, true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        controller = FullBackupController(scope, RuntimeEnvironment.getApplication().contentResolver, temp.newFolder(), BackupFileTransfer(account, client), object : FullBackupPort {
            override fun begin() = snapshot
            override fun end() = Unit
            override fun owner() = currentOwner
            override fun restoreFilesAvailable() = true
            override fun preflight(data: FullBackupData) { preflights++ }
            override suspend fun importData(backup: ChatBackup, memory: List<String>): Boolean {
                assertEquals(listOf("Personal note"), memory); imported = backup; return true
            }
        })
    }
    @After fun close() { if (::controller.isInitialized) controller.close(); if (::scope.isInitialized) scope.cancel() }
    private fun waitForOperation() = runBlocking { withTimeout(15_000) { controller.state.first { !it.busy } }; Unit }
    private fun save(): File = temp.newFile().also {
        controller.save(Uri.fromFile(it), password); waitForOperation()
        assertTrue(controller.state.value.status, controller.state.value.status.startsWith("Full backup saved"))
    }
    private fun review(file: File) {
        controller.review(Uri.fromFile(file), password); waitForOperation()
        assertNotNull(controller.state.value.status, controller.state.value.preview)
    }

    @Test fun rawCloudBytesAreRecoveredAtOriginalPathsAndRetryAfterUploadFailureKeepsPhoneData() {
        cloud[path] = raw; setup()
        val file = save(); cloud.clear(); review(file)
        refuseUpload = true
        controller.confirmRestore(); waitForOperation()
        assertNull(imported); assertTrue(cloud.isEmpty()); assertNotNull(controller.state.value.preview)
        assertTrue(controller.state.value.status.contains("retry"))
        refuseUpload = false
        controller.confirmRestore(); waitForOperation()
        assertArrayEquals(raw, cloud[path]); assertEquals(listOf(path), uploads)
        assertEquals(path, imported!!.chats.single().messages.single().files.single().path)
        assertNull(controller.state.value.preview)
        review(file); controller.confirmRestore(); waitForOperation()
        assertEquals("Existing identical bytes must be verified and kept without another upload", 1, uploads.size)
    }

    @Test fun sameSizeChangedCloudFileStopsRestoreBeforeImportOrOverwrite() {
        cloud[path] = raw; setup()
        val file = save(); cloud[path] = raw.map { (it + 1).toByte() }.toByteArray(); review(file)
        controller.confirmRestore(); waitForOperation()
        assertNull(imported); assertTrue(uploads.isEmpty())
        assertTrue(controller.state.value.status.contains("differs"))
    }

    @Test fun inMemoryAttachmentsBecomePersistentRawFilesAndChatReferences() {
        setup(inline = true)
        val file = save(); review(file); controller.confirmRestore(); waitForOperation()
        val ref = imported!!.chats.single().messages.single().files.single()
        assertTrue(ref.path.startsWith("$owner/")); assertEquals("Inline.pdf", ref.name)
        assertArrayEquals(raw, cloud[ref.path]); assertEquals(1, uploads.size)
        review(file); controller.confirmRestore(); waitForOperation()
        assertEquals(1, uploads.size)
    }

    @Test fun wrongPasswordDiscardsEarlierReviewAndForeignAccountCannotRestore() {
        cloud[path] = raw; setup()
        val file = save(); review(file)
        controller.review(Uri.fromFile(file), "wrong backup password".toCharArray()); waitForOperation()
        assertNull(controller.state.value.preview)
        controller.confirmRestore(); assertNull(imported)
        currentOwner = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        controller.review(Uri.fromFile(file), password); waitForOperation()
        assertNull(controller.state.value.preview); assertTrue(uploads.isEmpty()); assertNull(imported)
        assertTrue(controller.state.value.status.contains("original backup account"))
    }

    @Test fun incompleteCloudInventoryCannotPublishAFalseFullBackup() {
        cloud[path] = raw; setup(); truncated = true
        val destination = temp.newFile().apply { writeText("keep destination") }
        controller.save(Uri.fromFile(destination), password); waitForOperation()
        assertTrue(controller.state.value.status.contains("inventory is incomplete"))
        assertEquals("keep destination", destination.readText()); assertEquals(0, preflights)
    }

    @Test fun partialRecoveryKeepsRecoveredBytesAndRetryDoesNotUploadThemAgain() {
        val second = "$owner/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf"
        cloud[path] = raw; cloud[second] = raw.reversedArray(); setup()
        val file = save(); cloud.clear(); review(file)
        failAfterUploads = 1
        controller.confirmRestore(); waitForOperation()
        assertEquals(1, cloud.size); assertNull(imported); assertEquals(1, uploads.size)
        failAfterUploads = null
        controller.confirmRestore(); waitForOperation()
        assertArrayEquals(raw, cloud[path]); assertArrayEquals(raw.reversedArray(), cloud[second])
        assertNotNull(imported); assertEquals(2, uploads.size)
    }

    @Test fun cancellingDownloadKeepsDestinationAndClearsTheSuppliedPassword() {
        cloud[path] = raw; setup()
        downloadStarted = CountDownLatch(1); releaseDownload = CountDownLatch(1)
        val destination = temp.newFile().apply { writeText("previous backup") }
        val key = password
        controller.save(Uri.fromFile(destination), key)
        try {
            assertTrue(downloadStarted!!.await(5, TimeUnit.SECONDS))
            controller.cancel()
        } finally { releaseDownload!!.countDown() }
        waitForOperation()
        assertTrue(key.all { it == '\u0000' }); assertEquals("previous backup", destination.readText())
        assertNull(imported); assertNull(controller.state.value.preview)
    }
}

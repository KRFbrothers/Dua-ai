package com.dua.ai.backup.drive

import android.app.Application
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveBackupApiTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = "11111111-2222-4333-8444-555555555555"
    private lateinit var server: MockWebServer
    private lateinit var api: DriveBackupApi
    private var allowed = true
    private var invalidations = 0
    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).readTimeout(3, TimeUnit.SECONDS).build()
        api = DriveBackupApi("google-access-token", owner, { check(allowed) { "Account changed" } }, client, server.url("/").toString(), { invalidations++ })
    }
    @After fun close() { server.shutdown() }
    private fun metadata(id: String, time: Int = 1, bytes: Long = 3, md5: String = "0".repeat(32), state: String = "complete", fileOwner: String = owner) = JSONObject()
        .put("id", id).put("name", "$id.duabackup").put("size", bytes.toString()).put("md5Checksum", md5).put("createdTime", "2026-10-16T10:00:${"%02d".format(time)}Z")
        .put("appProperties", JSONObject().put("duaType", "full-backup-v1").put("duaOwner", fileOwner).put("duaState", state).put("duaSize", bytes.toString()).put("duaMd5", md5))
    private fun response(json: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(json.toString())
    private fun list(vararg entries: JSONObject) = JSONObject().put("files", JSONArray(entries.toList()))
    private fun file(metadata: JSONObject) = DriveBackupFile(metadata.getString("id"), metadata.getString("name"), metadata.getString("size").toLong(),
        java.time.Instant.parse(metadata.getString("createdTime")).toEpochMilli(), metadata.getString("md5Checksum"))

    @Test fun listingIsOwnerScopedAppDataOnlyAndRetentionKeepsThreeNewestCompletedBackups() = runBlocking {
        val rows = (1..5).map { metadata("file-$it", it) }
        server.enqueue(response(list(*rows.reversed().toTypedArray())))
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(204))
        api.retainThree(file(rows.last()))
        val inventory = server.takeRequest()
        assertEquals("Bearer google-access-token", inventory.getHeader("Authorization"))
        assertEquals("appDataFolder", inventory.requestUrl!!.queryParameter("spaces"))
        assertTrue(inventory.requestUrl!!.queryParameter("q")!!.contains(owner))
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/drive/v3/files/file-1", server.takeRequest().path)
        assertEquals(3, server.requestCount)
    }

    @Test fun paginatedInventoryIsCompletedBeforeDeletingAndPendingOrForeignEntriesAreNeverPruned() = runBlocking {
        server.enqueue(response(list(metadata("pending", state = "pending"), metadata("file-4", 4)).put("nextPageToken", "page-2")))
        server.enqueue(response(list(metadata("file-3", 3), metadata("file-2", 2), metadata("file-1", 1))))
        server.enqueue(MockResponse().setResponseCode(204))
        api.retainThree(file(metadata("file-4", 4)))
        server.takeRequest(); assertEquals("page-2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
        assertEquals("/drive/v3/files/file-1", server.takeRequest().path)
        server.enqueue(response(list(metadata("foreign", fileOwner = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"))))
        try { api.backups(); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(4, server.requestCount)
    }

    @Test fun missingNewUploadOrInvalidInventoryStopsAllRetentionDeletes() = runBlocking {
        server.enqueue(response(list(metadata("older", 1))))
        try { api.retainThree(file(metadata("new", 2))); fail() } catch (_: IllegalStateException) { }
        assertEquals(1, server.requestCount)
        server.enqueue(response(list(metadata("bad-size").put("size", "99999999999999"))))
        try { api.backups(); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(2, server.requestCount)
    }

    @Test fun uploadStreamsMultipleChunksChecksRemoteChecksumAndOnlyThenMarksComplete() = runBlocking {
        val raw = ByteArray(8 * 1024 * 1024 + 13) { (it % 251).toByte() }
        val target = temp.newFile().apply { writeBytes(raw) }
        val md5 = DriveBackupApi.digest(target)
        val progress = mutableListOf<Int>()
        server.enqueue(MockResponse().setHeader("Location", server.url("/upload/session")))
        server.enqueue(MockResponse().setResponseCode(308).setHeader("Range", "bytes=0-8388607"))
        server.enqueue(response(metadata("uploaded", bytes = target.length(), md5 = md5, state = "pending")))
        server.enqueue(response(metadata("uploaded", bytes = target.length(), md5 = md5)))
        val result = api.upload(target) { progress += it }
        assertEquals(md5, result.md5)
        val create = server.takeRequest()
        val meta = JSONObject(create.body.readUtf8())
        assertEquals("appDataFolder", meta.getJSONArray("parents").getString(0))
        assertEquals("pending", meta.getJSONObject("appProperties").getString("duaState"))
        assertEquals(owner, meta.getJSONObject("appProperties").getString("duaOwner"))
        assertEquals("resumable", create.requestUrl!!.queryParameter("uploadType"))
        val first = server.takeRequest(); val second = server.takeRequest()
        assertEquals("bytes 0-8388607/${raw.size}", first.getHeader("Content-Range"))
        assertEquals(8L * 1024 * 1024, first.bodySize)
        assertArrayEquals(raw.copyOfRange(8 * 1024 * 1024, raw.size), second.body.readByteArray())
        val mark = server.takeRequest()
        assertEquals("PATCH", mark.method)
        assertEquals("complete", JSONObject(mark.body.readUtf8()).getJSONObject("appProperties").getString("duaState"))
        assertEquals(100, progress.last())
    }

    @Test fun checksumMismatchNeverMarksCompleteOrDeletesAnyOldBackup() = runBlocking {
        val target = temp.newFile().apply { writeText("encrypted bytes") }
        server.enqueue(MockResponse().setHeader("Location", server.url("/upload/session")))
        server.enqueue(response(metadata("bad", bytes = target.length(), md5 = "0".repeat(32), state = "pending")))
        try { api.upload(target) {}; fail() } catch (_: IllegalArgumentException) { }
        assertEquals(2, server.requestCount)
    }

    @Test fun interruptedFinalChunkCanBeRecoveredButIncompleteBytesStayUnlistedAndAreNeverDeleted() = runBlocking {
        val completeBytes = metadata("interrupted", bytes = 123, md5 = "1".repeat(32), state = "pending")
        val incomplete = metadata("partial", state = "pending").apply { remove("md5Checksum"); remove("size") }
        server.enqueue(response(list(completeBytes, incomplete)))
        server.enqueue(response(metadata("interrupted", bytes = 123, md5 = "1".repeat(32))))
        assertEquals(listOf("interrupted"), api.recoverInterruptedUploads().map { it.id })
        assertEquals("GET", server.takeRequest().method)
        val mark = server.takeRequest()
        assertEquals("PATCH", mark.method); assertEquals("/drive/v3/files/interrupted", mark.requestUrl!!.encodedPath)
        assertEquals(2, server.requestCount)
    }

    @Test fun authFailuresAndRedirectsDoNotLeakTokensOrFollowLocations() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.email(); fail() } catch (_: DriveSetupRequired) { }
        assertEquals(1, invalidations)
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/not-followed")))
        try { api.email(); fail() } catch (_: DriveFailure) { }
        assertEquals(2, server.requestCount)
        server.enqueue(MockResponse().setHeader("Location", "https://untrusted.example/upload"))
        try { api.upload(temp.newFile().apply { writeText("encrypted") }) {}; fail() } catch (_: IllegalArgumentException) { }
        assertEquals(3, server.requestCount)
    }

    @Test fun downloadVerifiesEncryptedBytesAndFailureDeletesOnlyTheTemporaryCopy() = runBlocking {
        val bytes = "encrypted file bytes".toByteArray()
        val source = temp.newFile().apply { writeBytes(bytes) }
        val meta = metadata("download", bytes = bytes.size.toLong(), md5 = DriveBackupApi.digest(source))
        val selected = file(meta)
        server.enqueue(response(list(meta))); server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        val target = temp.newFile()
        api.download(selected, target); assertArrayEquals(bytes, target.readBytes())
        assertEquals("appDataFolder", server.takeRequest().requestUrl!!.queryParameter("spaces"))
        assertEquals("media", server.takeRequest().requestUrl!!.queryParameter("alt"))
        server.enqueue(response(list(meta))); server.enqueue(MockResponse().setBody("changed file bytes!!"))
        try { api.download(selected, target); fail() } catch (_: IllegalArgumentException) { }
        assertFalse(target.exists())
        allowed = false
        try { api.backups(); fail() } catch (_: IllegalStateException) { }
        assertEquals(4, server.requestCount)
    }
}

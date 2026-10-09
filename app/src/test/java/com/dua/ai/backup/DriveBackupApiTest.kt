package com.dua.ai.backup

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.SavedChat
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DriveBackupApiTest {
    private fun snapshot() = CloudSnapshot(listOf(SavedChat("chat-1", "Dua", "saved draft", emptyList(), 12)), listOf("Prefers Hindi"))

    @Test fun uploadCreatesIndependentSnapshotInPrivateAppFolder() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"id\":\"backup_1\"}"))
            DriveBackupApi(baseUrl = server.url("/").toString().removeSuffix("/")).upload("test-token", snapshot())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("/upload/drive/v3/files", request.requestUrl!!.encodedPath)
            val body = request.body.readUtf8()
            assertTrue(body.contains("appDataFolder"))
            assertTrue(body.contains("dua-cloud-snapshot"))
            assertTrue(body.contains("saved draft"))
            assertTrue(body.contains("Prefers Hindi"))
        }
    }

    @Test fun restoreReadsOnlyNewestTaggedAppDataBackup() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"files\":[{\"id\":\"backup_1\"}]}"))
            server.enqueue(MockResponse().setBody(CloudSnapshotCodec.encode(snapshot()).toString()))
            val restored = DriveBackupApi(baseUrl = server.url("/").toString().removeSuffix("/")).downloadLatest("test-token")
            val list = server.takeRequest().requestUrl!!
            assertEquals("appDataFolder", list.queryParameter("spaces"))
            assertEquals("createdTime desc", list.queryParameter("orderBy"))
            assertEquals("1", list.queryParameter("pageSize"))
            assertTrue(list.queryParameter("q")!!.contains("duaBackup"))
            assertEquals("/drive/v3/files/backup_1?alt=media", server.takeRequest().path)
            assertEquals(snapshot().memory, restored.memory)
            assertEquals("saved draft", restored.chats.single().draft)
        }
    }

    @Test fun missingBackupDoesNotProduceEmptyRestore() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"files\":[]}"))
            try {
                DriveBackupApi(baseUrl = server.url("/").toString().removeSuffix("/")).downloadLatest("test-token")
                fail("Missing backup must fail")
            } catch (error: IllegalStateException) { assertTrue(error.message!!.contains("No Dua backup")) }
        }
    }

    @Test fun revokedPermissionRequiresReconnect() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            try {
                DriveBackupApi(baseUrl = server.url("/").toString().removeSuffix("/")).upload("expired-token", snapshot())
                fail("Revoked permission must fail")
            } catch (_: DrivePermissionRequired) { }
        }
    }

    @Test fun invalidSnapshotCannotBeUploaded() {
        MockWebServer().use { server ->
            try {
                DriveBackupApi(baseUrl = server.url("/").toString().removeSuffix("/")).upload("test-token", CloudSnapshot(emptyList(), listOf("")))
                fail("Invalid snapshot must fail before network access")
            } catch (_: IllegalArgumentException) { assertEquals(0, server.requestCount) }
        }
    }
}

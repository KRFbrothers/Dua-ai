package com.dua.ai.files

import android.app.Application
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StorageOverviewTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun cacheTotalsIncludeMediaAsASubsetWithoutReadingExternalLinksOrDeletingFiles() = runBlocking {
        val cache = temp.newFolder()
        val media = File(cache, "generated-media").apply { mkdirs() }
        File(media, "image").writeBytes(ByteArray(1024))
        File(cache, "temporary").writeBytes(ByteArray(200))
        val external = temp.newFile().apply { writeBytes(ByteArray(9999)) }
        Files.createSymbolicLink(File(cache, "link").toPath(), external.toPath())
        assertEquals(CacheUsage(1224, 1024), StorageOverview.cacheUsage(cache))
        assertEquals(9999L, external.length())
        assertEquals(1024L, File(media, "image").length())
    }

    @Test fun realZeroUsageIsAcceptedButMissingStringNegativeAndFractionalValuesAreRejected() {
        val valid = JSONObject().put("files", 0).put("bytes", 0).put("maxBytes", 52428800)
        assertEquals(StorageUsage(0, 0, 52428800), StorageOverview.usage(valid))
        for (bad in listOf(JSONObject(), JSONObject(valid.toString()).put("files", "0"), JSONObject(valid.toString()).put("bytes", -1),
            JSONObject(valid.toString()).put("files", 0.5), JSONObject(valid.toString()).put("maxBytes", 0))) {
            assertThrows(IllegalArgumentException::class.java) { StorageOverview.usage(bad) }
        }
    }
}

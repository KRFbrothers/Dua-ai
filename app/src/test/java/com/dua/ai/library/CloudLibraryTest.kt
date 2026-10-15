package com.dua.ai.library

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudLibraryTest {
    private val owner = "11111111-2222-4333-8444-555555555555"
    @Test fun inventoryFiltersOtherOwnersAndNonCanonicalPathsAndDeduplicates() {
        val json = JSONObject("""{"files":[
          {"path":"$owner/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png","name":"real.png","mimeType":"image/png","size":123},
          {"path":"$owner/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png","name":"duplicate.png","mimeType":"image/png","size":123},
          {"path":"99999999-2222-4333-8444-555555555555/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png","name":"foreign.png","mimeType":"image/png","size":1},
          {"path":"$owner/../private.png","name":"bad.png","mimeType":"image/png","size":1}]}""")
        val files = CloudLibrary.files(json, owner)
        assertEquals(1, files.size)
        assertEquals("real.png", files.single().name)
        assertEquals(1, LibraryIndex.build(emptyList(), "draft", emptyList(), emptyList(), cloudFiles = files).count { it.kind == LibraryKind.Images })
    }
}

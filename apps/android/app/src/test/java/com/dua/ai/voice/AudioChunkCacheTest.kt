package com.dua.ai.voice

import org.junit.Assert.*
import org.junit.Test

class AudioChunkCacheTest {
    @Test fun returnsCopiesSoPlaybackWipesDoNotCorruptTheCache() {
        val cache = AudioChunkCache(1000)
        val original = byteArrayOf(1, 2, 3)
        cache.put("a", original); original.fill(0)
        val first = cache.get("a")!!; assertArrayEquals(byteArrayOf(1, 2, 3), first)
        first.fill(0); assertArrayEquals(byteArrayOf(1, 2, 3), cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test fun evictsLeastRecentlyUsedAndSkipsHugeEntries() {
        val cache = AudioChunkCache(1000)
        cache.put("a", ByteArray(400) { 1 }); cache.put("b", ByteArray(400) { 2 })
        cache.get("a")
        cache.put("c", ByteArray(400) { 3 })
        assertNotNull(cache.get("a")); assertNull(cache.get("b")); assertNotNull(cache.get("c"))
        assertEquals(800, cache.bytes())
        cache.put("huge", ByteArray(600)); assertNull(cache.get("huge"))
        cache.put("a", ByteArray(100)); assertEquals(500, cache.bytes())
        cache.clear(); assertEquals(0, cache.count()); assertEquals(0, cache.bytes())
    }
}

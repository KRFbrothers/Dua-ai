package com.dua.ai.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSessionPreloadTest {
    private class Store(var value: AccountSession?, val failFirst: Boolean = false) : SessionPersistence {
        @Volatile var reads = 0
        override fun read(): AccountSession? { reads++; if (failFirst && reads == 1) error("keystore"); return value }
        override fun write(value: AccountSession?) { this.value = value }
    }

    @Test fun firstReadUsesPreloadThenReadsDirectly() {
        val store = Store(null)
        AccountSessionPreload.start(store)
        val wrapped = AccountSessionPreload.store(store)
        assertNull(wrapped.read())
        assertNull(wrapped.read())
        assertEquals(2, store.reads)  // one preload + one direct read; the first wrapped read waited for the preload
    }

    @Test fun failedPreloadFallsBackToNormalRead() {
        val store = Store(null, failFirst = true)
        AccountSessionPreload.start(store)
        val wrapped = AccountSessionPreload.store(store)
        assertNull(wrapped.read())
        assertEquals(2, store.reads)
    }
}

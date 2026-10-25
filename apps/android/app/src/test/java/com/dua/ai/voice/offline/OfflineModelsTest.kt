package com.dua.ai.voice.offline

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModelsTest {
    @get:Rule val temp = TemporaryFolder()

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, body) -> zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry() } }
    }.toByteArray()
    private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    private val good = zipBytes("m/am/final.mdl" to "am", "m/conf/model.conf" to "conf", "m/README" to "x")
    private fun model(bytes: ByteArray = good, md5: String = md5(bytes)) = OfflineModel("t", "m", "Test", bytes.size.toLong(), md5)

    @Test fun catalogMatchesOfficialSmallModels() {
        val hi = OfflineModels.byId("hi")!!
        assertEquals("https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", hi.url)
        assertEquals(44_458_845L, hi.sizeBytes)
        assertTrue(OfflineModels.catalog.all { it.md5.matches(Regex("[0-9a-f]{32}")) && it.url.startsWith("https://alphacephei.com/vosk/models/") })
        assertNull(OfflineModels.byId("ur"))
    }

    @Test fun routingNeedsAModelAndFollowsTheMode() {
        assertFalse(OfflineModels.useOffline("always", modelReady = false, phoneRecognizerAvailable = false))
        assertFalse(OfflineModels.useOffline("off", true, false))
        assertTrue(OfflineModels.useOffline("fallback", true, false))
        assertFalse(OfflineModels.useOffline("fallback", true, true))
        assertTrue(OfflineModels.useOffline("always", true, true))
    }

    @Test fun installVerifiesChecksumThenUnzipsAndDeletes() {
        val store = OfflineModelStore(temp.newFolder("vosk"))
        val zip = temp.newFile("m.zip").apply { writeBytes(good) }
        assertThrows(IOException::class.java) { store.install(model(md5 = "0".repeat(32)), zip) }
        assertFalse(store.installed(model()))
        assertThrows(IOException::class.java) { store.install(model().copy(sizeBytes = 1), zip) }
        store.install(model(), zip)
        assertTrue(store.installed(model()))
        assertEquals("am", File(store.dir(model()), "am/final.mdl").readText())
        store.delete(model())
        assertFalse(store.dir(model()).exists())
    }

    @Test fun rejectsZipSlipAndArchivesWithoutModelFiles() {
        val store = OfflineModelStore(temp.newFolder("vosk"))
        val evil = zipBytes("m/am/x" to "a", "../../evil.txt" to "no")
        assertThrows(IOException::class.java) { store.install(model(evil), temp.newFile("e.zip").apply { writeBytes(evil) }) }
        assertFalse(File(temp.root, "evil.txt").exists())
        val empty = zipBytes("m/README" to "x")
        assertThrows(IOException::class.java) { store.install(model(empty), temp.newFile("n.zip").apply { writeBytes(empty) }) }
        assertFalse(store.installed(model(empty)))
    }

    private fun manager(scope: TestScope, wifi: () -> Boolean, body: ByteArray = good, length: Long = body.size.toLong(), opened: MutableList<String> = mutableListOf()) =
        OfflineModelManager(OfflineModelStore(temp.newFolder()), { url -> opened += url; ByteArrayInputStream(body) to length }, wifi,
            scope = TestScope(StandardTestDispatcher(scope.testScheduler)), catalog = listOf(model()))

    @Test fun downloadNeedsWifi() = runTest {
        val opened = mutableListOf<String>()
        val m = manager(this, { false }, opened = opened)
        m.download("t"); advanceUntilIdle()
        assertTrue((m.status.value["t"] as ModelStatus.Failed).message.contains("Wi-Fi"))
        assertTrue(opened.isEmpty())
    }

    @Test fun downloadVerifiesInstallsAndCanBeDeleted() = runTest {
        val opened = mutableListOf<String>()
        val m = manager(this, { true }, opened = opened)
        m.download("t")
        assertTrue(m.status.value["t"] is ModelStatus.Downloading)
        advanceUntilIdle()
        assertEquals(ModelStatus.Installed, m.status.value["t"])
        assertEquals(listOf("https://alphacephei.com/vosk/models/m.zip"), opened)
        assertNotNull(m.ready("t"))
        assertFalse(m.store.partFile(model()).exists())
        m.delete("t"); advanceUntilIdle()
        assertEquals(ModelStatus.NotInstalled, m.status.value["t"])
        assertNull(m.ready("t"))
    }

    @Test fun wrongSizeOrDamagedDownloadFailsAndLeavesNothing() = runTest {
        val m = manager(this, { true }, length = 5)
        m.download("t"); advanceUntilIdle()
        assertTrue(m.status.value["t"] is ModelStatus.Failed)
        val damaged = good.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val d = manager(this, { true }, body = damaged)
        d.download("t"); advanceUntilIdle()
        assertTrue((d.status.value["t"] as ModelStatus.Failed).message.contains("checksum") || d.status.value["t"] is ModelStatus.Failed)
        assertNull(d.ready("t"))
    }
}

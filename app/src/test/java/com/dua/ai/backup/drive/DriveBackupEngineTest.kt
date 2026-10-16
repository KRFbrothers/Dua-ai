package com.dua.ai.backup.drive

import android.app.Application
import com.dua.ai.backup.FullBackupSnapshot
import com.dua.ai.history.ChatBackup
import com.dua.ai.projects.ProjectCodec
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveBackupEngineTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = "11111111-2222-4333-8444-555555555555"
    private val email = "owner@example.test"
    private lateinit var store: DriveBackupStore
    private lateinit var root: File
    private var current: Pair<String?, String?> = owner to email
    private var failWrite = false
    private var failUpload = false
    private var failRetention = false
    private var authorizationRevoked = false
    private var driveEmail = email
    private var cloudEnabled = true
    private var writeWait: CompletableDeferred<Unit>? = null
    private var writeStarted = CompletableDeferred<Unit>()
    private var disableDuringUpload = false
    private var writes = 0
    private var uploads = 0
    private var prunes = 0
    private var openedPassword: CharArray? = null
    private lateinit var engine: DriveBackupEngine
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(DriveBackupStore.PREFS, 0).edit().clear().commit()
        store = DriveBackupStore(app, TestPasswordProtector())
        store.connect(owner, email); store.enable(owner, "private backup password".toCharArray())
        root = temp.newFolder()
        val remote = object : DriveBackupRemote {
            override suspend fun email() = driveEmail
            override suspend fun backups() = emptyList<DriveBackupFile>()
            override suspend fun upload(file: File, onProgress: (Int) -> Unit): DriveBackupFile {
                uploads++
                assertEquals("encrypted payload", file.readText()); assertFalse(file.readText().contains("password"))
                if (failUpload) throw DriveFailure("Network failed", true)
                onProgress(100)
                if (disableDuringUpload) store.disable()
                return DriveBackupFile("new-file", "backup.duabackup", file.length(), 1234, "0".repeat(32))
            }
            override suspend fun retainThree(uploaded: DriveBackupFile) {
                prunes++; assertEquals("new-file", store.state.value.lastFileId)
                if (failRetention) throw IOException("cleanup unavailable")
            }
            override suspend fun download(selected: DriveBackupFile, target: File) = error("not called")
        }
        engine = DriveBackupEngine(store, root, { current }, { FullBackupSnapshot(ChatBackup(emptyList(), ProjectCodec.defaults), listOf("Personal note"), owner, cloudEnabled) },
            { _, _ -> if (authorizationRevoked) throw DriveSetupRequired(); remote },
            { snapshot, _, target, password, progress ->
                writes++; assertEquals(listOf("Personal note"), snapshot.memory); openedPassword = password
                writeStarted.complete(Unit); writeWait?.await()
                if (failWrite) throw IOException("disk failed")
                progress("Encrypting…"); target.writeText("encrypted payload")
            }, clock = { 1234 })
    }
    @Test fun uploadMustFinishBeforeSuccessAndRetentionAndPasswordIsWiped() = runBlocking {
        assertEquals(DriveRunResult.Success, engine.execute())
        assertEquals(1, writes); assertEquals(1, uploads); assertEquals(1, prunes)
        assertEquals(1234L, store.state.value.lastSuccess); assertEquals("success", store.state.value.phase)
        assertTrue(openedPassword!!.all { it == '\u0000' }); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun failedWriteOrUploadNeverPrunesAndKeepsPreviousSuccess() = runBlocking {
        val record = store.state.value; store.success(record, "previous", 100)
        failWrite = true
        assertEquals(DriveRunResult.Retry, engine.execute()); assertEquals(0, uploads); assertEquals(0, prunes)
        failWrite = false; failUpload = true
        assertEquals(DriveRunResult.Retry, engine.execute()); assertEquals(0, prunes)
        assertEquals(100L, store.state.value.lastSuccess); assertEquals("previous", store.state.value.lastFileId)
        assertTrue(openedPassword!!.all { it == '\u0000' })
    }
    @Test fun revokedPermissionOrDifferentGoogleAccountNeverWritesOrUploadsData() = runBlocking {
        authorizationRevoked = true
        assertEquals(DriveRunResult.NeedsSetup, engine.execute()); assertEquals("setup", store.state.value.phase)
        authorizationRevoked = false; driveEmail = "different@example.test"
        assertEquals(DriveRunResult.NeedsSetup, engine.execute())
        assertEquals(0, writes); assertEquals(0, uploads); assertEquals(0, prunes)
    }
    @Test fun changedDuaAccountOrDisabledCloudFilesFailsBeforePreparingArchive() = runBlocking {
        current = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee" to email
        assertEquals(DriveRunResult.NeedsSetup, engine.execute())
        current = owner to email; cloudEnabled = false
        assertEquals(DriveRunResult.NeedsSetup, engine.execute())
        assertEquals(0, writes); assertEquals(0, uploads)
    }
    @Test fun disablingDuringAnUploadPreventsSuccessAndAllRetentionDeletes() = runBlocking {
        disableDuringUpload = true
        engine.execute()
        assertEquals(0, store.state.value.lastSuccess); assertEquals(0, prunes)
        assertFalse(store.state.value.enabled); assertEquals("off", store.state.value.phase)
    }
    @Test fun cleanupFailureKeepsTheNewVerifiedBackupAndDoesNotRetryTheUpload() = runBlocking {
        failRetention = true
        assertEquals(DriveRunResult.Success, engine.execute())
        assertEquals(1234L, store.state.value.lastSuccess); assertEquals("warning", store.state.value.phase)
        assertEquals(1, uploads)
    }
    @Test fun overlappingWorkIsDeferredAndCancellationAlwaysReleasesTheLockAndSecret() = runBlocking {
        writeWait = CompletableDeferred()
        val job = launch { engine.execute() }
        writeStarted.await()
        assertEquals(DriveRunResult.Retry, engine.execute())
        job.cancelAndJoin()
        assertTrue(openedPassword!!.all { it == '\u0000' }); assertTrue(root.listFiles()!!.isEmpty())
        assertEquals("queued", store.state.value.phase); assertEquals(0, prunes)
        writeWait = null
        assertEquals(DriveRunResult.Success, engine.execute()); assertEquals(1, uploads)
    }
    @Test fun abandonedPrivateStagingFromAnInterruptedProcessIsCleanedBeforeNewArchive() = runBlocking {
        File(root, "old-workspace").apply { mkdirs(); File(this, "raw-private-file").writeText("private bytes") }
        assertEquals(DriveRunResult.Success, engine.execute())
        assertTrue(root.listFiles()!!.isEmpty())
    }
}

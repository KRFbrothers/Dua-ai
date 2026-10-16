package com.dua.ai.backup.drive

import android.app.Application
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveBackupSchedulerTest {
    @Test fun dailyScheduleIsUniqueUnmeteredAndCancellationStopsDailyAndManualWork() {
        val app = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val work = WorkManager.getInstance(app)
        val scheduler = DriveBackupScheduler(app)
        scheduler.ensureDaily(); shadowOf(android.os.Looper.getMainLooper()).idle()
        val first = work.getWorkInfosForUniqueWork(DriveBackupScheduler.DAILY).get(5, TimeUnit.SECONDS).single()
        assertEquals(NetworkType.UNMETERED, first.constraints.requiredNetworkType)
        assertTrue(first.constraints.requiresBatteryNotLow()); assertTrue(first.constraints.requiresStorageNotLow())
        assertEquals(TimeUnit.HOURS.toMillis(24), first.initialDelayMillis)
        scheduler.ensureDaily(); shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(first.id, work.getWorkInfosForUniqueWork(DriveBackupScheduler.DAILY).get(5, TimeUnit.SECONDS).single().id)
        scheduler.backupNow(); shadowOf(android.os.Looper.getMainLooper()).idle()
        val now = work.getWorkInfosForUniqueWork(DriveBackupScheduler.NOW).get(5, TimeUnit.SECONDS).single()
        assertEquals(NetworkType.UNMETERED, now.constraints.requiredNetworkType)
        scheduler.cancel(); shadowOf(android.os.Looper.getMainLooper()).idle()
        val all = work.getWorkInfosByTag(DriveBackupScheduler.TAG).get(5, TimeUnit.SECONDS)
        assertEquals(2, all.size); assertTrue(all.all { it.state == WorkInfo.State.CANCELLED })
    }
}

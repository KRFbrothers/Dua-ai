package com.dua.ai.backup.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.dua.ai.MainActivity
import com.dua.ai.R
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class DriveBackupScheduler(private val context: Context) {
    fun ensureDaily() {
        val work = PeriodicWorkRequestBuilder<DriveBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS).setConstraints(constraints()).addTag(TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, work)
    }
    fun backupNow() {
        val work = OneTimeWorkRequestBuilder<DriveBackupWorker>().setConstraints(constraints()).addTag(TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, work)
    }
    fun cancel() { WorkManager.getInstance(context).cancelAllWorkByTag(TAG) }
    companion object {
        const val TAG = "dua-automatic-drive-backup"
        const val DAILY = "$TAG-daily"
        const val NOW = "$TAG-now"
        fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()
    }
}

class DriveBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val notificationId = (id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
    override suspend fun getForegroundInfo() = foreground("Preparing an encrypted backup…", null)
    override suspend fun doWork(): Result {
        val backups = AutomaticDriveBackups.get(applicationContext)
        val expected = backups.store.state.value
        if (!expected.enabled) return Result.success()
        try {
            // A dataSync foreground service keeps large archives within Android's long-running work model.
            setForeground(getForegroundInfo())
            return when (backups.engine.execute { text, percent ->
                runCatching { applicationContext.getSystemService(NotificationManager::class.java).notify(notificationId, notification(text, percent)) }
            }) {
                DriveRunResult.Success -> Result.success()
                DriveRunResult.NeedsSetup -> Result.failure()
                DriveRunResult.Retry -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            com.dua.ai.diagnostics.DuaLog.soft(com.dua.ai.diagnostics.SoftErrorArea.DriveBackup, failure)
            backups.store.report(expected, "error", "Android could not run the background backup. Open Storage and retry; existing backups were kept.")
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
    private fun foreground(text: String, percent: Int?): ForegroundInfo {
        val notification = notification(text, percent)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(notificationId, notification)
    }
    private fun notification(text: String, percent: Int?): Notification {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Drive backups", NotificationManager.IMPORTANCE_LOW))
        val intent = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(R.drawable.ic_stat_dua).setContentTitle("Dua AI backup")
            .setContentText(text).setOnlyAlertOnce(true).setOngoing(true).setContentIntent(intent)
            .setProgress(100, percent ?: 0, percent == null).build()
    }
    companion object { private const val CHANNEL = "dua_drive_backup" }
}

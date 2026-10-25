package com.dua.ai.voice.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service (type microphone) that keeps a live voice session running with the screen off or the app
 * in the background. It is only started from the live voice screen while the app is visible, as Android 14+
 * requires for microphone services. The notification shows the session and has an End action.
 */
class LiveVoiceService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) { LiveVoiceController.end("Ended from the notification."); stopSelf(); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        } catch (_: Exception) {
            LiveVoiceController.end("Live voice could not keep the microphone on. Open Dua and try again.")
            stopSelf(); return START_NOT_STICKY
        }
        LiveVoiceController.connect(this)
        if (!LiveVoiceController.active) stopSelf()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { LiveVoiceController.end("Android stopped the live session."); stopSelf() }

    override fun onDestroy() {
        if (LiveVoiceController.active) LiveVoiceController.end("Session ended.")
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Live voice", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a live voice conversation is using the microphone." })
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val end = PendingIntent.getService(this, 1, Intent(this, LiveVoiceService::class.java).setAction(ACTION_END), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(com.dua.ai.R.drawable.ic_stat_dua)
            .setContentTitle("Live voice with Dua").setContentText("Microphone on · audio streams to xAI while the session runs")
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_CALL).setContentIntent(open)
            .addAction(0, "End", end).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
    }

    companion object {
        private const val CHANNEL = "dua-live-voice"
        private const val NOTIFICATION_ID = 42
        const val ACTION_END = "com.dua.ai.live.END"
    }
}

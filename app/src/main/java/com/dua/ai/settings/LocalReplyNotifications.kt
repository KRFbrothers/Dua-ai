package com.dua.ai.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dua.ai.MainActivity

object LocalReplyNotifications {
    fun available(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun show(context: Context, chatId: String) {
        if (!available(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("dua-replies", "Dua replies", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chat_id", chatId)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.notify(1, NotificationCompat.Builder(context, "dua-replies").setSmallIcon(com.dua.ai.R.drawable.ic_stat_dua)
                .setContentTitle("Dua").setContentText("Reply ready. Open Dua to read it.")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pending).build())
        } catch (_: SecurityException) { }
    }
}

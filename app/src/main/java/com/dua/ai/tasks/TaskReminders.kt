package com.dua.ai.tasks

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dua.ai.MainActivity
import com.dua.ai.R
import com.dua.ai.settings.DevicePreferences

/** One inexact device alarm; server snapshots contain deadlines, never notification consent/history. */
object TaskReminders {
    private const val CHANNEL = "dua-task-reminders"
    private const val ACTION = "com.dua.ai.TASK_REMINDER"
    private const val STORE = "dua_task_reminders"
    @Volatile private var lastError = ""
    private fun storage(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = runCatching { storage(context).getBoolean("enabled", false) }.getOrDefault(false)
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun status(context: Context): String = when {
        lastError.isNotBlank() -> lastError
        !enabled(context) -> "Task reminders are off on this phone."
        !available(context) -> "Task reminders are blocked. Allow notifications in app Settings."
        else -> "Task reminders are on. Android battery saving can delay delivery."
    }
    @Synchronized fun setEnabled(context: Context, value: Boolean) {
        require(!value || available(context)) { "Allow task notifications in app Settings first." }
        check(storage(context).edit().putBoolean("enabled", value).commit()) { "Reminder settings could not be saved." }
        refresh(context)
    }
    @Synchronized fun reset(context: Context) {
        check(storage(context).edit().clear().commit()) { "Reminder settings could not be reset." }
        refresh(context)
    }
    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, 2,
        Intent(context, TaskReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun refresh(context: Context, deliver: Boolean = false, now: Long = System.currentTimeMillis()): Boolean {
        return try {
            val manager = context.getSystemService(AlarmManager::class.java)
            val pending = alarm(context)
            manager.cancel(pending)
            val preferences = DevicePreferences(context).also { it.requireReadable() }.state.value
            val store = storage(context)
            val delivered = store.getStringSet("delivered", emptySet())!!.toSet()
            check(delivered.size <= 2000 && delivered.all { it.matches(Regex("[a-f0-9]{64}")) }) { "Reset task reminder settings to recover their history." }
            val plan = TaskReminderPolicy.plan(preferences.projects, delivered, now, enabled(context) && available(context))
            if (!enabled(context) || preferences.projects.none { p -> !p.archived && p.tasks.any { !it.completed && it.dueAt?.let { at -> at <= now } == true } }) {
                context.getSystemService(NotificationManager::class.java).cancel(2)
            }
            val kept = delivered.intersect(plan.known)
            val nextDelivered = if (deliver) kept + plan.due else kept
            // Persist the delivery marker before posting; never run a repeated alarm on a failed write.
            if (nextDelivered != delivered) check(store.edit().putStringSet("delivered", nextDelivered).commit()) { "Reminder history could not be saved. Retry from notification settings." }
            if (deliver && plan.due.isNotEmpty() && !show(context, plan.due.size)) {
                store.edit().putStringSet("delivered", kept).commit()
                lastError = "Task notification could not be shown. Check notification settings."
                return false
            }
            val next = TaskReminderPolicy.plan(preferences.projects, nextDelivered, now, enabled(context) && available(context)).nextAt
            if (next != null) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            lastError = ""
            true
        } catch (failure: Exception) {
            lastError = failure.message ?: "Task reminders could not be scheduled. Check notification settings."
            false
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun show(context: Context, count: Int): Boolean = try {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT))
        if (!available(context)) false else {
            val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(2, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_dua)
                .setContentTitle("Dua task reminder").setContentText("$count ${if (count == 1) "task is" else "tasks are"} due. Open Projects → Tasks to review.")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(open).build())
            true
        }
    } catch (_: SecurityException) { false }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskReminders.refresh(context, deliver = intent.action == "com.dua.ai.TASK_REMINDER")
    }
}

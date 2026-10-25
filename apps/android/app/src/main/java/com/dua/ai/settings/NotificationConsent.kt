package com.dua.ai.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dua.ai.tasks.TaskReminders

internal enum class NotificationKind { Reply, Reminder }

/** Device consent is saved only after checking the actual permission and notification channel. */
internal class NotificationConsent(private val context: Context, private val busy: () -> Boolean,
    private val saveReply: (Boolean) -> Boolean) {
    fun requiresPermission(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun update(kind: NotificationKind, enabled: Boolean): String {
        if (busy()) return "Finish the current data operation before changing notifications."
        val allowed = if (kind == NotificationKind.Reply) LocalReplyNotifications.available(context) else TaskReminders.available(context)
        if (enabled && !allowed) return "Notifications are blocked by Android. Open Android notification settings to allow them."
        return try {
            if (kind == NotificationKind.Reply) {
                if (saveReply(enabled)) "Saved on this device." else "Notification settings could not be saved. Try again."
            } else {
                TaskReminders.setEnabled(context, enabled)
                TaskReminders.status(context)
            }
        } catch (failure: Exception) { failure.message ?: "Notification settings could not be saved. Try again." }
    }
}

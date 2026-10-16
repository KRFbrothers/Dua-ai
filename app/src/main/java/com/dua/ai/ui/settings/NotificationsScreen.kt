package com.dua.ai.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dua.ai.settings.LocalReplyNotifications
import com.dua.ai.settings.NotificationConsent
import com.dua.ai.settings.NotificationKind
import com.dua.ai.tasks.TaskReminders
import com.dua.ai.ui.components.*
import com.dua.ai.viewmodel.ChatViewModel

internal data class DeviceNotificationState(val repliesAllowed: Boolean, val remindersAllowed: Boolean, val remindersEnabled: Boolean)

/** Refresh when returning from Android settings; permission availability is distinct from user consent. */
@Composable
internal fun rememberDeviceNotifications(refresh: Int = 0): DeviceNotificationState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun read() = DeviceNotificationState(LocalReplyNotifications.available(context), TaskReminders.available(context), TaskReminders.enabled(context))
    var state by remember(context, refresh) { mutableStateOf(read()) }
    DisposableEffect(lifecycle, context, refresh) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) state = read() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}

internal fun notificationSummary(replies: Boolean, device: DeviceNotificationState): String = when {
    (replies && !device.repliesAllowed) || (device.remindersEnabled && !device.remindersAllowed) -> "Blocked by Android"
    replies || device.remindersEnabled -> "On"
    else -> "Off"
}

/** Package-scoped settings only; no global permission changes or hidden grants. */
internal fun openAndroidSettings(context: Context, notifications: Boolean = false): Boolean {
    val intent = if (notifications) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
}

@Composable
internal fun NotificationsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.localSettings.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    val status by viewModel.settingsStatus.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val device = rememberDeviceNotifications(refresh)
    var reminders by remember { mutableStateOf(TaskReminders.enabled(context)) }
    var pending by rememberSaveable { mutableStateOf<NotificationKind?>(null) }
    var notice by rememberSaveable { mutableStateOf("") }
    val consent = remember(context, viewModel) {
        NotificationConsent(context, busy = { viewModel.dataBusy.value }, saveReply = { enabled ->
            viewModel.updateSettings(viewModel.localSettings.value.copy(notifications = enabled))
            viewModel.localSettings.value.notifications == enabled
        })
    }

    fun apply(target: NotificationKind, enabled: Boolean) {
        notice = consent.update(target, enabled)
        reminders = TaskReminders.enabled(context)
        refresh++
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pending
        pending = null
        if (granted && target != null) apply(target, true)
        else if (target != null) notice = "Notification permission was not granted. Your notification preferences are unchanged."
    }
    fun toggle(target: NotificationKind, enabled: Boolean) {
        notice = ""
        if (enabled && consent.requiresPermission()) {
            pending = target
            try { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            catch (_: IllegalStateException) { pending = null; notice = "Permission request unavailable. Open Android notification settings." }
        } else apply(target, enabled)
    }

    NotificationsContent(settings.notifications, reminders, device, enabled = !busy && pending == null,
        status = notice.ifBlank { status }, onBack = onBack,
        onReplies = { toggle(NotificationKind.Reply, it) }, onReminders = { toggle(NotificationKind.Reminder, it) },
        onAndroidSettings = { if (!openAndroidSettings(context, notifications = true)) notice = "Android notification settings are unavailable on this device." })
}

@Composable
internal fun NotificationsContent(replies: Boolean, reminders: Boolean, device: DeviceNotificationState, enabled: Boolean,
    status: String, onBack: () -> Unit, onReplies: (Boolean) -> Unit, onReminders: (Boolean) -> Unit, onAndroidSettings: () -> Unit) {
    DuaPageScaffold("Notifications", onBack) {
        SettingsGroup("On this phone") {
            SettingsToggleRow(Icons.Outlined.Notifications, "Reply notifications", "Alert when a reply finishes while Dua AI is in the background.",
                replies, enabled, onReplies)
            SettingsRowDivider()
            SettingsToggleRow(null, "Task reminders", "Reminders for due tasks in your projects. Android battery saving can delay delivery.",
                reminders, enabled, onReminders)
        }
        if ((replies && !device.repliesAllowed) || (reminders && !device.remindersAllowed))
            PageStatus("Notifications are blocked by Android. Your saved preference is kept; allow the relevant channel in Android settings.")
        SettingsGroup(null) {
            SettingsNavRow(Icons.Outlined.Notifications, "Android notification settings", "Manage app permission, sounds and notification channels", onClick = onAndroidSettings)
        }
        PageNote("Notification consent is specific to this device. Alerts do not include your chat text or task titles.")
        if (status.isNotBlank()) PageStatus(status)
    }
}

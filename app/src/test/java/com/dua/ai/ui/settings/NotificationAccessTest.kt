package com.dua.ai.ui.settings

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import com.dua.ai.settings.LocalReplyNotifications
import com.dua.ai.settings.NotificationConsent
import com.dua.ai.settings.NotificationKind
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.tasks.TaskReminders
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationAccessTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun deniedPermissionNeverReportsReplyDeliveryAvailableOrPostsAnAlert() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(LocalReplyNotifications.available(app))
        LocalReplyNotifications.show(app, "chat-id")
        assertTrue(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
        assertEquals("Blocked by Android", notificationSummary(true, DeviceNotificationState(false, true, false)))
    }

    @Test fun disabledReplyChannelIsBlockedEvenWhenAppPermissionIsGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("dua-replies", "Dua replies", NotificationManager.IMPORTANCE_NONE))
        assertFalse(LocalReplyNotifications.available(app))
        LocalReplyNotifications.show(app, "chat-id")
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun reminderOnlyConsentAndBlockedChannelAreReflectedInHubSummary() {
        assertEquals("Off", notificationSummary(false, DeviceNotificationState(false, false, false)))
        assertEquals("On", notificationSummary(false, DeviceNotificationState(true, true, true)))
        assertEquals("Blocked by Android", notificationSummary(false, DeviceNotificationState(true, false, true)))
        assertEquals("On", notificationSummary(true, DeviceNotificationState(true, false, false)))
    }

    @Test fun androidRecoveryIntentsAreScopedToThisApp() {
        assertTrue(openAndroidSettings(app, notifications = true))
        val notifications = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications.action)
        assertEquals(app.packageName, notifications.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertTrue(openAndroidSettings(app))
        val permissions = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, permissions.action)
        assertEquals("package:${app.packageName}", permissions.data.toString())
    }

    @Test fun actualConsentRejectsDeniedOrBusyRequestsAndPersistsReplyAndReminderIndependently() {
        app.getSharedPreferences("dua_preferences", 0).edit().clear().commit()
        TaskReminders.reset(app)
        val store = DevicePreferences(app)
        var busy = false
        val consent = NotificationConsent(app, { busy }) { value ->
            store.update(store.state.value.copy(notifications = value)); true
        }
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(consent.requiresPermission())
        assertTrue(consent.update(NotificationKind.Reply, true).contains("blocked"))
        assertTrue(consent.update(NotificationKind.Reminder, true).contains("blocked"))
        assertFalse(store.state.value.notifications)
        assertFalse(TaskReminders.enabled(app))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(consent.requiresPermission())
        // Merely granting permission does not opt in to either kind of alert.
        assertFalse(store.state.value.notifications); assertFalse(TaskReminders.enabled(app))
        consent.update(NotificationKind.Reply, true)
        assertTrue(DevicePreferences(app).state.value.notifications); assertFalse(TaskReminders.enabled(app))
        consent.update(NotificationKind.Reminder, true)
        assertTrue(TaskReminders.enabled(app))
        busy = true
        assertTrue(consent.update(NotificationKind.Reply, false).contains("Finish"))
        assertTrue(DevicePreferences(app).state.value.notifications)
        busy = false
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        consent.update(NotificationKind.Reply, false); consent.update(NotificationKind.Reminder, false)
        assertFalse(DevicePreferences(app).state.value.notifications); assertFalse(TaskReminders.enabled(app))
    }
}

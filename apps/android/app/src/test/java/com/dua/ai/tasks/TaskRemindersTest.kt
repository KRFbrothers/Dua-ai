package com.dua.ai.tasks

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import com.dua.ai.projects.ProjectTask
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaskRemindersTest {
    private lateinit var app: Application
    private val due = System.currentTimeMillis() + 60_000
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_preferences", "dua_task_reminders")) app.getSharedPreferences(name, 0).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TaskReminders.reset(app)
    }
    private fun save(completed: Boolean = false, dueAt: Long = due) = DevicePreferences(app).update(LocalPreferences(projects = com.dua.ai.projects.ProjectFixtures.owned.map {
        if (it.id == "coding") it.copy(tasks = listOf(ProjectTask("t1", "Private client task", completed, dueAt))) else it
    }))
    @Test fun consentDefaultsOffAndTaskEditsReconcileOneAlarmWithoutErasingDueDates() {
        save()
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        assertFalse(TaskReminders.enabled(app)); assertTrue(alarms.scheduledAlarms.isEmpty())
        TaskReminders.setEnabled(app, true)
        assertEquals(1, alarms.scheduledAlarms.size)
        save(dueAt = due + 60_000)
        assertEquals(1, alarms.scheduledAlarms.size)
        save(completed = true)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        TaskReminders.reset(app)
        assertFalse(TaskReminders.enabled(app))
        assertEquals(due, DevicePreferences(app).state.value.projects[1].tasks.single().dueAt)
    }
    @Test fun reminderReceiverUsesLatestCacheAndPostsPrivateGenericNotificationOnce() {
        save(); TaskReminders.setEnabled(app, true)
        assertTrue(TaskReminders.refresh(app, deliver = true, now = due + 1))
        val manager = app.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.single()
        assertFalse(notification.extras.toString().contains("Private client task"))
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
        manager.cancel(2)
        assertTrue(TaskReminders.refresh(app, deliver = true, now = due + 2))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        // Reopening a completed task keeps its delivery key; setting a new deadline is required.
        save(completed = true); save(completed = false)
        TaskReminders.refresh(app, deliver = true, now = due + 3)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun bootRebuildsAlarmAndDeletedTasksRejectStaleBroadcasts() {
        save(); TaskReminders.setEnabled(app, true)
        TaskReminderReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        DevicePreferences(app).update(LocalPreferences())
        TaskReminderReceiver().onReceive(app, Intent("com.dua.ai.TASK_REMINDER"))
        assertTrue(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }
}

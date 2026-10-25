package com.dua.ai.widget

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.FrameLayout
import com.dua.ai.MainActivity
import com.dua.ai.R
import com.dua.ai.entry.EntryAction
import com.dua.ai.entry.ExternalEntry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class DuaToolbarWidgetTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Test fun everyRemoteViewsButtonLaunchesItsOwnImmutableActivityPendingIntent() {
        val view = DuaToolbarWidgetReceiver.views(app, 42).apply(app, FrameLayout(app))
        for ((id, action) in DuaToolbarWidgetReceiver.buttons) {
            assertTrue(view.findViewById<View>(id).performClick())
            val launch = shadowOf(app).nextStartedActivity
            assertNotNull(launch); assertEquals(MainActivity::class.java.name, launch.component!!.className)
            assertEquals(action, ExternalEntry.parse(launch, app.packageName)!!.action)
        }
        val a = PendingIntent.getActivity(app, 1, DuaToolbarWidgetReceiver.intent(app, 1, EntryAction.Query), PendingIntent.FLAG_IMMUTABLE)
        val b = PendingIntent.getActivity(app, 1, DuaToolbarWidgetReceiver.intent(app, 1, EntryAction.Mic), PendingIntent.FLAG_IMMUTABLE)
        val c = PendingIntent.getActivity(app, 2, DuaToolbarWidgetReceiver.intent(app, 2, EntryAction.Query), PendingIntent.FLAG_IMMUTABLE)
        assertNotEquals(a, b); assertNotEquals(a, c)
        assertTrue(shadowOf(a).flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }
    @Test fun minimumLayoutKeepsSevenAccessibleTouchTargetsInsideTheWidget() {
        for (width in listOf(250, 360)) {
            val view = DuaToolbarWidgetReceiver.views(app, width).apply(app, FrameLayout(app))
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(144, View.MeasureSpec.EXACTLY)); view.layout(0, 0, width, 144)
            for (id in DuaToolbarWidgetReceiver.buttons.keys) {
                val button = view.findViewById<View>(id)
                assertTrue(button.width >= 48); assertTrue(button.height >= 48); assertFalse(button.contentDescription.isNullOrBlank())
                val location = IntArray(2); button.getLocationInWindow(location)
                assertTrue(location[0] >= 0 && location[0] + button.width <= width)
                assertTrue(location[1] >= 0 && location[1] + button.height <= 144)
            }
        }
    }
    @Test fun manifestRegistersShareMimesAndTextOnlyRememberAliasAndPrivateWidgetReceiver() {
        val pm = app.packageManager
        for (type in listOf("text/plain", "text/csv", "image/png", "audio/mpeg", "video/mp4", "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                val matches = pm.queryIntentActivities(Intent(action).setType(type).setPackage(app.packageName), PackageManager.MATCH_DEFAULT_ONLY)
                assertTrue("$type $action", matches.any { it.activityInfo.name == MainActivity::class.java.name })
            }
        }
        val alias = ComponentName(app, "${app.packageName}.RememberThisShareTargetActivity")
        val info = pm.getActivityInfo(alias, 0)
        assertEquals(MainActivity::class.java.name, info.targetActivity); assertEquals("Remember this", info.loadLabel(pm).toString()); assertEquals(R.drawable.gs_bookmark_add, info.icon)
        assertFalse(pm.getReceiverInfo(ComponentName(app, DuaToolbarWidgetReceiver::class.java), 0).exported)
    }
}

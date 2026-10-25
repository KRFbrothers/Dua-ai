package com.dua.ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.dua.ai.MainActivity
import com.dua.ai.R
import com.dua.ai.entry.EntryAction

/** Static toolbar: no microphone, account data, network request or background service in the widget. */
class DuaToolbarWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { manager.updateAppWidget(it, views(context, it)) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = manager.updateAppWidget(id, views(context, id))

    companion object {
        const val EXTRA_WIDGET_ACTION = "EXTRA_WIDGET_ACTION"
        internal val buttons = linkedMapOf(R.id.widget_query to EntryAction.Query, R.id.widget_mic to EntryAction.Mic,
            R.id.widget_live to EntryAction.Live, R.id.widget_camera to EntryAction.Camera, R.id.widget_photos to EntryAction.Photos,
            R.id.widget_files to EntryAction.Files, R.id.widget_screenshare to EntryAction.Screenshare)
        internal fun intent(context: Context, id: Int, action: EntryAction) = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("dua-widget://toolbar/$id/${action.wire}"))
            .putExtra(EXTRA_WIDGET_ACTION, action.wire)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        internal fun views(context: Context, id: Int) = RemoteViews(context.packageName, R.layout.dua_toolbar_widget).apply {
            buttons.forEach { (view, action) ->
                setOnClickPendingIntent(view, PendingIntent.getActivity(context, id, intent(context, id, action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            setInt(R.id.widget_live, "setColorFilter", context.getColor(R.color.dua_widget_on_purple))
        }
    }
}

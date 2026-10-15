package com.dua.ai.ui.projects

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dua.ai.MainActivity
import com.dua.ai.R
import com.dua.ai.projects.ChatProject

internal object ProjectShortcuts {
    fun pin(context: Context, project: ChatProject): String {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return "Your launcher does not support pinned project shortcuts."
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .putExtra("open_project_id", project.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = ShortcutInfoCompat.Builder(context, "dua-project-${project.id}").setShortLabel(project.name.take(40))
            .setLongLabel(project.name).setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_dua)).setIntent(intent).build()
        return if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) "Confirm the project shortcut in your launcher." else "Shortcut could not be requested."
    }
}

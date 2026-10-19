/*
 * Adapted from SociaLite's NotificationHelper.kt (Contact.toShortcut and pushShortcut) and
 * res/xml/sharing_shortcuts.xml (https://github.com/android/socialite, read through the fork
 * https://github.com/KRFbrothers/socialite), Copyright 2023 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md. Changes: Dua chats
 * instead of contacts, no Person/LocusId/bubbles/notifications, Dua's launcher icon only, at most
 * four chats, launcher actions (New chat, Voice, Imagine) and removal of deleted chats.
 */
package com.dua.ai.entry

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dua.ai.MainActivity
import com.dua.ai.R
import com.dua.ai.history.SavedChat
import com.dua.ai.widget.DuaToolbarWidgetReceiver

/** Recent chats as Direct Share targets plus launcher long-press actions. Nothing here sends a message. */
object ChatShortcuts {
    /** Must match res/xml/sharing_shortcuts.xml. */
    const val SHARE_CATEGORY = "com.dua.ai.category.CHAT_SHARE_TARGET"
    const val MAX_CHATS = 4
    private const val CHAT_PREFIX = "dua-chat-"

    data class Target(val chatId: String, val label: String)
    data class Action(val id: String, val label: String, val longLabel: String, val icon: Int, val action: EntryAction)

    val actions = listOf(
        Action("dua-new-chat", "New chat", "Start a new chat", R.drawable.shortcut_new_chat, EntryAction.NewChat),
        Action("dua-voice", "Voice", "Talk to Dua", R.drawable.shortcut_voice, EntryAction.Mic),
        Action("dua-imagine", "Imagine", "Create an image", R.drawable.shortcut_imagine, EntryAction.Imagine),
    )

    fun shortcutId(chatId: String) = CHAT_PREFIX + chatId
    fun chatId(shortcutId: String?): String? = shortcutId?.takeIf { it.startsWith(CHAT_PREFIX) }?.removePrefix(CHAT_PREFIX)?.takeIf { it.isNotBlank() }

    /** Newest chats that have at least one message, pinned first; labels are trimmed for the share sheet. */
    fun recent(chats: List<SavedChat>, max: Int = MAX_CHATS): List<Target> = chats
        .filter { chat -> chat.messages.any { it.isUser } }
        .sortedWith(compareByDescending<SavedChat> { it.pinned }.thenByDescending { it.updatedAt })
        .take(max.coerceAtLeast(0))
        .map { Target(it.id, it.title.replace(Regex("\\s+"), " ").trim().take(40).ifBlank { "Dua chat" }) }

    /** Replaces Dua's dynamic shortcuts; chats that disappeared (deleted) are removed from the share sheet too. Call off the main thread. */
    fun publish(context: Context, targets: List<Target>) {
        val app = context.applicationContext
        val room = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(app) - actions.size).coerceAtLeast(0)
        val chats = targets.take(minOf(MAX_CHATS, room))
        val launcher = actions.mapIndexed { rank, action ->
            ShortcutInfoCompat.Builder(app, action.id).setShortLabel(action.label).setLongLabel(action.longLabel)
                .setIcon(IconCompat.createWithResource(app, action.icon)).setRank(rank)
                .setIntent(launchIntent(app, action.action)).build()
        }
        val share = chats.mapIndexed { index, target ->
            ShortcutInfoCompat.Builder(app, shortcutId(target.chatId))
                .setActivity(ComponentName(app, MainActivity::class.java))
                .setShortLabel(target.label).setLongLabel(target.label)
                .setIcon(IconCompat.createWithResource(app, R.mipmap.ic_launcher))
                .setLongLived(true).setRank(actions.size + index)
                .setCategories(setOf(SHARE_CATEGORY))
                .setIntent(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("open_chat_id", target.chatId))
                .build()
        }
        val keep = share.map { it.id }.toSet()
        val stale = ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED)
            .map { it.id }.filter { chatId(it) != null && it !in keep }
        if (stale.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(app, stale)
        ShortcutManagerCompat.setDynamicShortcuts(app, launcher + share)
    }

    internal fun launchIntent(context: Context, action: EntryAction) = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW).setData(Uri.parse("dua-shortcut://launcher/${action.wire}"))
        .putExtra(DuaToolbarWidgetReceiver.EXTRA_WIDGET_ACTION, action.wire)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

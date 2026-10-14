package com.dua.ai.ui.history

import com.dua.ai.history.SavedChat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class HistoryBucket(val label: String) { Pinned("Pinned"), Today("Today"), Yesterday("Yesterday"), Previous7Days("Previous 7 days"), Older("Older") }

/** Groups saved chats by their last update for the history drawer. */
object HistoryGrouping {
    fun bucket(updatedAt: Long, now: Long, zone: ZoneId): HistoryBucket {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> HistoryBucket.Today
            days == 1L -> HistoryBucket.Yesterday
            days <= 7 -> HistoryBucket.Previous7Days
            else -> HistoryBucket.Older
        }
    }

    /**
     * Chats shown in the drawer's recent list. Hides placeholders that were never sent (no user message and no custom
     * title, i.e. titled "Draft") and collapses duplicate ids to the newest copy. Display only: nothing is deleted.
     */
    fun recent(chats: List<SavedChat>): List<SavedChat> =
        chats.filter { chat -> chat.customTitle != null || chat.messages.any { it.isUser && it.text.isNotBlank() } }
            .groupBy { it.id }.values.map { copies -> copies.maxBy { it.updatedAt } }

    fun filter(chats: List<SavedChat>, query: String): List<SavedChat> {
        return HistorySearch.search(chats, query).map { it.chat }
    }

    fun group(chats: List<SavedChat>, now: Long, zone: ZoneId): List<Pair<HistoryBucket, List<SavedChat>>> =
        chats.sortedByDescending { it.updatedAt }
            .groupBy { if (it.pinned) HistoryBucket.Pinned else bucket(it.updatedAt, now, zone) }
            .toList()
            .sortedBy { it.first.ordinal }
}

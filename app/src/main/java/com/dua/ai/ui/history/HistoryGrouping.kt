package com.dua.ai.ui.history

import com.dua.ai.history.SavedChat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class HistoryBucket(val label: String) { Today("Today"), Yesterday("Yesterday"), Previous7Days("Previous 7 days"), Older("Older") }

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

    fun filter(chats: List<SavedChat>, query: String): List<SavedChat> {
        val needle = query.trim()
        return if (needle.isEmpty()) chats else chats.filter { (it.title + " " + it.preview).contains(needle, ignoreCase = true) }
    }

    fun group(chats: List<SavedChat>, now: Long, zone: ZoneId): List<Pair<HistoryBucket, List<SavedChat>>> =
        chats.sortedByDescending { it.updatedAt }
            .groupBy { bucket(it.updatedAt, now, zone) }
            .toList()
            .sortedBy { it.first.ordinal }
}

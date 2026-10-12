package com.dua.ai.ui.history

import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryGroupingTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun chat(id: String, updatedAt: Long, text: String = id) =
        SavedChat(id, "Dua", "", listOf(ChatMessage(text = text, isUser = true)), updatedAt)

    @Test fun pinnedChatsAppearOnceBeforeDateGroupsAndKeepStableRecentOrder() {
        val now = at(8, 12)
        val old = chat("old", 1).copy(pinned = true)
        val newer = chat("newer", at(7, 12)).copy(pinned = true)
        val recent = chat("recent", now)
        val groups = HistoryGrouping.group(listOf(old, recent, newer), now, zone)
        assertEquals(listOf(HistoryBucket.Pinned, HistoryBucket.Today), groups.map { it.first })
        assertEquals(listOf("newer", "old"), groups.first().second.map { it.id })
        assertEquals(3, groups.sumOf { it.second.size })
        assertEquals(listOf("old"), HistoryGrouping.filter(listOf(old, recent), "old").map { it.id })
        assertEquals(HistoryBucket.Older, HistoryGrouping.group(listOf(old.copy(pinned = false)), now, zone).single().first)
    }
    @Test fun bucketsUseLocalCalendarDays() {
        val now = at(8, 1)
        assertEquals(HistoryBucket.Today, HistoryGrouping.bucket(at(8, 0), now, zone))
        assertEquals(HistoryBucket.Yesterday, HistoryGrouping.bucket(at(7, 23), now, zone))
        assertEquals(HistoryBucket.Previous7Days, HistoryGrouping.bucket(at(1, 12), now, zone))
        assertEquals(HistoryBucket.Older, HistoryGrouping.bucket(at(1, 0) - 1, now, zone))
        assertEquals(HistoryBucket.Today, HistoryGrouping.bucket(at(9, 0), now, zone))
    }

    @Test fun groupsInBucketOrderWithNewestFirst() {
        val now = at(8, 12)
        val groups = HistoryGrouping.group(listOf(chat("old", at(1, 0) - 1), chat("today-early", at(8, 1)),
            chat("yesterday", at(7, 9)), chat("today-late", at(8, 11))), now, zone)
        assertEquals(listOf(HistoryBucket.Today, HistoryBucket.Yesterday, HistoryBucket.Older), groups.map { it.first })
        assertEquals(listOf("today-late", "today-early"), groups.first().second.map { it.id })
    }

    @Test fun filterMatchesTitleAndPreviewIgnoringCase() {
        val chats = listOf(chat("a", 1, "Plan a trip to Goa"), chat("b", 2, "Kotlin coroutines"))
        assertEquals(listOf("a"), HistoryGrouping.filter(chats, "  goa ").map { it.id })
        assertEquals(chats, HistoryGrouping.filter(chats, " "))
    }
}

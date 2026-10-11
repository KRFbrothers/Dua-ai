package com.dua.ai.tasks

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import com.dua.ai.projects.ProjectCodec

object TaskDates {
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    fun display(at: Long, zone: ZoneId = ZoneId.systemDefault()): String = format.format(Instant.ofEpochMilli(at).atZone(zone))
    fun parse(date: String, time: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (date.isBlank() && time.isBlank()) return null
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && time.matches(Regex("\\d{2}:\\d{2}"))) { "Use YYYY-MM-DD and 24-hour HH:mm, or clear both fields." }
        val local = try { LocalDateTime.parse("$date $time", format) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid date and 24-hour time.") }
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "This time does not exist in your time zone. Choose another time." }
        // At a clock-back overlap choose the earlier occurrence; saved deadlines are absolute instants.
        return local.toInstant(offsets.first()).toEpochMilli().also { require(it in 1..ProjectCodec.MAX_DUE_AT) { "Due date is outside the supported range." } }
    }
}

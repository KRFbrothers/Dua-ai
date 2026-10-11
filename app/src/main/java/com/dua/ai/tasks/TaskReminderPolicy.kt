package com.dua.ai.tasks

import com.dua.ai.projects.ChatProject
import java.security.MessageDigest

data class ReminderPlan(val known: Set<String>, val due: Set<String>, val nextAt: Long?)

object TaskReminderPolicy {
    fun key(projectId: String, taskId: String, dueAt: Long): String = MessageDigest.getInstance("SHA-256")
        .digest("$projectId\u0000$taskId\u0000$dueAt".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun plan(projects: List<ChatProject>, delivered: Set<String>, now: Long, enabled: Boolean): ReminderPlan {
        val all = projects.flatMap { project -> project.tasks.mapNotNull { task -> task.dueAt?.let {
            Triple(key(project.id, task.id, it), it, !project.archived && !task.completed)
        } } }
        val pending = if (enabled) all.filter { it.third && it.first !in delivered } else emptyList()
        val due = pending.filter { it.second <= now }.map { it.first }.toSet()
        val first = pending.minOfOrNull { it.second }
        return ReminderPlan(all.map { it.first }.toSet(), due, first?.coerceAtLeast(now + 1000))
    }
}

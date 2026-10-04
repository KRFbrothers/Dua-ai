package com.dua.ai.setup

import com.dua.ai.account.CloudSnapshot

/** Native sync integration can reuse these guards. Scheduling is not enabled by this policy. */
data class AutomationConsent(val enabled: Boolean = false, val accountId: String? = null) {
    fun permits(currentAccountId: String?): Boolean = enabled && !accountId.isNullOrBlank() && accountId == currentAccountId
}

data class AutomationBaseline(val chatMessageCounts: Map<String, Int>, val memory: Set<String>)

object AutomationSyncPolicy {
    fun baseline(snapshot: CloudSnapshot): AutomationBaseline = AutomationBaseline(
        snapshot.chats.associate { it.id to it.messages.size }, snapshot.memory.toSet()
    )

    fun requiresDeletionReview(previous: AutomationBaseline?, snapshot: CloudSnapshot): Boolean {
        if (previous == null) return false
        val current = baseline(snapshot)
        return previous.chatMessageCounts.any { (id, count) -> current.chatMessageCounts[id]?.let { it < count } ?: true } ||
            previous.memory.any { it !in current.memory }
    }

    /** Stop after five failures; retry should require an explicit user action thereafter. */
    fun retryDelayMillis(failureCount: Int): Long? = if (failureCount in 1..4) minOf(60_000L, 1_000L shl failureCount) else null
}

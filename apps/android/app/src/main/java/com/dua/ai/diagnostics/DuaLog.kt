/*
 * Idea adapted from Androidify's CrashlyticsTree.kt (https://github.com/android/androidify),
 * Copyright 2025 The Android Open Source Project, licensed under the Apache License, Version 2.0.
 * See THIRD_PARTY_NOTICES.md. Rewritten without Timber, and it never sends message text.
 */
package com.dua.ai.diagnostics

import androidx.annotation.VisibleForTesting
import com.dua.ai.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** Fixed places a handled ("soft") failure can come from. Only these names are reported. */
enum class SoftErrorArea(val key: String) {
    ChatReply("chat_reply"),
    ChatRetry("chat_retry"),
    CloudSync("cloud_sync"),
    DriveBackup("drive_backup"),
    ImagineUsage("imagine_usage"),
    VoiceUsage("voice_usage"),
    FileDelete("file_delete"),
    ReplyNotification("reply_notification"),
}

/**
 * The privacy-safe copy of a handled failure that may be sent to Crashlytics. It keeps only the
 * area, exception class names, an HTTP-like status when the failure has one, and stack frames
 * (class and method names). The original message is dropped: messages can hold server replies,
 * emails, tokens or chat text.
 */
class SoftError internal constructor(message: String, cause: SoftError?) : Exception(message, cause) {
    companion object {
        private const val MAX_CAUSES = 4

        fun from(area: SoftErrorArea, failure: Throwable): SoftError {
            val chain = generateSequence(failure) { it.cause.takeIf { next -> next !== it } }.take(MAX_CAUSES + 1).toList()
            var cause: SoftError? = null
            for (item in chain.drop(1).reversed()) cause = copy(describe(item), item, cause)
            return copy("${area.key}: ${describe(failure)}", failure, cause)
        }

        private fun copy(message: String, source: Throwable, cause: SoftError?) =
            SoftError(message, cause).also { it.stackTrace = source.stackTrace }

        /** Class name plus the HTTP status of Dua's API errors; never [Throwable.message]. */
        internal fun describe(failure: Throwable): String {
            val status = when (failure) {
                is com.dua.ai.account.AccountException -> failure.status
                is com.dua.ai.network.DuaApiException -> failure.status.takeIf { it > 0 }
                else -> null
            }
            return failure.javaClass.name + (status?.let { " (status $it)" } ?: "")
        }
    }
}

/**
 * Reports handled failures to Firebase Crashlytics in release builds only, so real bugs that users
 * only see as a notice ("Reply interrupted", "Sync failed") become visible. Debug builds report nothing.
 * Never pass user text here: only a fixed [SoftErrorArea] and the exception, which is sanitised by [SoftError].
 */
object DuaLog {
    /** Each area + failure type is reported at most this many times per app run. */
    internal const val MAX_PER_KIND = 3

    private val counts = ConcurrentHashMap<String, Int>()

    /** Where reports go. Tests replace it; null (or a debug build) means nothing is sent. */
    @Volatile internal var sink: ((SoftErrorArea, SoftError) -> Unit)? = if (BuildConfig.DEBUG) null else ::crashlytics

    fun soft(area: SoftErrorArea, failure: Throwable) {
        if (failure is CancellationException || offline(failure)) return
        val target = sink ?: return
        val kind = area.key + "/" + failure.javaClass.name
        if (counts.merge(kind, 1, Int::plus)!! > MAX_PER_KIND) return
        runCatching { target(area, SoftError.from(area, failure)) }
    }

    /** "No internet" is the phone's state, not a bug, so it is not reported. */
    private fun offline(failure: Throwable) = generateSequence(failure) { it.cause.takeIf { next -> next !== it } }.take(5)
        .any { it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException }

    @VisibleForTesting internal fun resetForTest() = counts.clear()

    private fun crashlytics(area: SoftErrorArea, error: SoftError) {
        val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("soft_area", area.key)
        crashlytics.recordException(error)
    }
}

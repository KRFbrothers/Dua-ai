/*
 * Adapted from Androidify's GeminiNanoDownloaderImpl.kt and GeminiNanoGenerationDataSource.kt
 * (https://github.com/android/androidify), Copyright 2025 The Android Open Source Project, licensed under the
 * Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md. Changes: no Remote Config or Hilt, an AICore presence
 * check before touching ML Kit, every failure becomes "unsupported" or null, and nothing is logged (prompts may
 * contain chat text).
 */
package com.dua.ai.ondevice

import android.content.Context
import android.content.pm.PackageManager
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Gemini Nano through ML Kit GenAI Prompt. Only phones with Android AICore and a supported model can use it. */
class GeminiNanoTextModel(context: Context) : OnDeviceTextModel {
    private val context = context.applicationContext
    private val lock = Mutex()
    private var client: GenerativeModel? = null
    private var unsupported = false

    /** ML Kit asks AICore for the model; without AICore installed the answer is always "unavailable". */
    private fun hasAiCore(): Boolean = runCatching {
        context.packageManager.getApplicationInfo(AICORE_PACKAGE, 0).enabled
    }.getOrDefault(false)

    private fun model(): GenerativeModel? {
        if (unsupported) return null
        client?.let { return it }
        if (!hasAiCore()) { unsupported = true; return null }
        return try { Generation.getClient().also { client = it } } catch (_: Throwable) { unsupported = true; null }
    }

    override suspend fun status(): OnDeviceStatus = lock.withLock { statusLocked() }

    private suspend fun statusLocked(): OnDeviceStatus {
        val model = model() ?: return OnDeviceStatus.Unsupported
        return try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> OnDeviceStatus.Ready
                FeatureStatus.DOWNLOADABLE -> OnDeviceStatus.Downloadable
                FeatureStatus.DOWNLOADING -> OnDeviceStatus.Downloading
                else -> OnDeviceStatus.Unsupported
            }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { OnDeviceStatus.Unsupported }
    }

    override suspend fun prepare(): Boolean = lock.withLock {
        when (statusLocked()) {
            OnDeviceStatus.Ready -> true
            OnDeviceStatus.Unsupported -> false
            OnDeviceStatus.Downloadable, OnDeviceStatus.Downloading -> try {
                val model = model() ?: return@withLock false
                val last = model.download().first { it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed }
                if (last is DownloadStatus.DownloadCompleted) { runCatching { model.warmup() }; true } else false
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { false }
        }
    }

    override suspend fun generate(prompt: String, maxOutputTokens: Int, temperature: Float): String? {
        val model = lock.withLock { if (statusLocked() == OnDeviceStatus.Ready) model() else null } ?: return null
        return try {
            model.generateContent(generateContentRequest(TextPart(prompt)) {
                this.temperature = temperature
                topK = 16
                candidateCount = 1
                this.maxOutputTokens = maxOutputTokens
            }).candidates.firstOrNull()?.text
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { null }
    }

    companion object {
        const val AICORE_PACKAGE = "com.google.android.aicore"
    }
}

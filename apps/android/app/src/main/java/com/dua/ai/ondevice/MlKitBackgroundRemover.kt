/*
 * Adapted from Androidify's LocalSegmentationDataSource.kt (https://github.com/android/androidify),
 * Copyright 2025 The Android Open Source Project, licensed under the Apache License, Version 2.0.
 * See THIRD_PARTY_NOTICES.md. Changes: no Hilt or Timber, a Google Play services check, a small Task await
 * helper instead of repeated callbacks, the segmenter is closed after use, and Dua's user-facing error messages.
 */
package com.dua.ai.ondevice

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** ML Kit subject segmentation delivered by Google Play services (the model is downloaded once by Play services). */
class MlKitBackgroundRemover(context: Context) : BackgroundRemover {
    private val context = context.applicationContext

    override fun available(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    override suspend fun remove(bitmap: Bitmap): Bitmap {
        if (!available()) throw BackgroundRemovalException("Remove background needs Google Play services on this phone.")
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        try {
            val modules = ModuleInstall.getClient(context)
            val ready = try { modules.areModulesAvailable(segmenter).await().areModulesAvailable() }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            if (!ready) withTimeout(DOWNLOAD_TIMEOUT_MS) { install(modules, segmenter) }
            val result = try { segmenter.process(InputImage.fromBitmap(bitmap, 0)).await() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { throw BackgroundRemovalException("Could not remove the background from this image.") }
            return result.foregroundBitmap ?: throw BackgroundRemovalException("No clear subject was found in this image.")
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            throw BackgroundRemovalException("The background tool is still downloading. Try again in a minute.")
        } finally {
            runCatching { segmenter.close() }
        }
    }

    private suspend fun install(modules: com.google.android.gms.common.moduleinstall.ModuleInstallClient,
        segmenter: com.google.mlkit.vision.segmentation.subject.SubjectSegmenter) = suspendCancellableCoroutine { continuation ->
        val listener = object : InstallStatusListener {
            override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                if (!continuation.isActive) return
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> { modules.unregisterListener(this); continuation.resume(Unit) }
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED, ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                        modules.unregisterListener(this)
                        continuation.resumeWithException(BackgroundRemovalException("Could not download the background tool. Check your connection and try again."))
                    }
                }
            }
        }
        continuation.invokeOnCancellation { modules.unregisterListener(listener) }
        modules.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).setListener(listener).build())
            .addOnSuccessListener { response -> if (response.areModulesAlreadyInstalled() && continuation.isActive) { modules.unregisterListener(listener); continuation.resume(Unit) } }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(BackgroundRemovalException("Could not download the background tool. Check your connection and try again.")) }
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 90_000L
    }
}

/** Waits for a Play services [Task] without adding kotlinx-coroutines-play-services. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

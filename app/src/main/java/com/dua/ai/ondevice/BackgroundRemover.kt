package com.dua.ai.ondevice

import android.graphics.Bitmap

/** Cuts the main subject out of a photo on the phone. Tests use a fake; the app uses [MlKitBackgroundRemover]. */
interface BackgroundRemover {
    /** False when this phone cannot run it (for example no Google Play services). */
    fun available(): Boolean
    /** The subject on a transparent background. Throws [BackgroundRemovalException] with a user-facing message on failure. */
    suspend fun remove(bitmap: Bitmap): Bitmap
}

class BackgroundRemovalException(message: String) : Exception(message)

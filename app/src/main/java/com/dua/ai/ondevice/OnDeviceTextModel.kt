package com.dua.ai.ondevice

/** What this phone can do with the small on-device model. */
enum class OnDeviceStatus { Unsupported, Downloadable, Downloading, Ready }

/** A small text model that runs on the phone. Tests use a fake; the app uses [GeminiNanoTextModel]. */
interface OnDeviceTextModel {
    /** Never throws: anything unexpected means [OnDeviceStatus.Unsupported]. */
    suspend fun status(): OnDeviceStatus
    /** Downloads the model if needed (the system does the download). True when it is ready to use. */
    suspend fun prepare(): Boolean
    /** One short answer, or null. Callers clean the text with [OnDeviceText]. */
    suspend fun generate(prompt: String, maxOutputTokens: Int, temperature: Float): String?
}

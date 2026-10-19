package com.dua.ai.ondevice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chat titles and Imagine prompt polish on the phone. Every method returns null instead of failing, so callers can
 * fall back to the normal server path. Titles never start a model download; only an explicit "Polish my prompt"
 * tap may ask the system to download the model.
 */
class OnDeviceAi(private val model: OnDeviceTextModel, private val scope: CoroutineScope) {
    private val _status = MutableStateFlow<OnDeviceStatus?>(null)
    /** null until the first check finishes. */
    val status: StateFlow<OnDeviceStatus?> = _status.asStateFlow()
    val supported: Boolean get() = _status.value.let { it != null && it != OnDeviceStatus.Unsupported }

    fun refresh() { scope.launch { _status.value = model.status() } }

    suspend fun title(user: String, reply: String): String? {
        if (_status.value != OnDeviceStatus.Ready) return null
        return withTimeoutOrNull(TITLE_TIMEOUT_MS) {
            OnDeviceText.cleanTitle(model.generate(OnDeviceText.titlePrompt(user, reply), maxOutputTokens = 24, temperature = 0.2f))
        }
    }

    suspend fun polish(draft: String): String? {
        if (!supported || draft.isBlank()) return null
        val result = withTimeoutOrNull(POLISH_TIMEOUT_MS) {
            if (!model.prepare()) null
            else OnDeviceText.cleanPolish(model.generate(OnDeviceText.polishPrompt(draft), maxOutputTokens = 160, temperature = 0.6f), draft)
        }
        _status.value = model.status()
        return result
    }

    companion object {
        const val TITLE_TIMEOUT_MS = 10_000L
        /** Includes a first-time model download by the system. */
        const val POLISH_TIMEOUT_MS = 180_000L
    }
}

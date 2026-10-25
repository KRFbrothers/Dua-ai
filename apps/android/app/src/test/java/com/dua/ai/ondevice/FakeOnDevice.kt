package com.dua.ai.ondevice

import android.graphics.Bitmap
import android.graphics.Color

/** Test fake: no phone model needed. */
class FakeTextModel(var current: OnDeviceStatus = OnDeviceStatus.Ready, var answer: String? = "Answer", var prepareResult: Boolean = true) : OnDeviceTextModel {
    val prompts = mutableListOf<String>()
    var prepares = 0
    override suspend fun status() = current
    override suspend fun prepare(): Boolean { prepares++; if (prepareResult && current != OnDeviceStatus.Unsupported) current = OnDeviceStatus.Ready; return prepareResult }
    override suspend fun generate(prompt: String, maxOutputTokens: Int, temperature: Float): String? { prompts += prompt; return answer }
}

/** Test fake: makes the left half transparent, like a cut-out. */
class FakeBackgroundRemover(var available: Boolean = true, var failure: String? = null) : BackgroundRemover {
    var calls = 0
    override fun available() = available
    override suspend fun remove(bitmap: Bitmap): Bitmap {
        calls++
        failure?.let { throw BackgroundRemovalException(it) }
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (x in 0 until out.width / 2) for (y in 0 until out.height) out.setPixel(x, y, Color.TRANSPARENT)
        return out
    }
}

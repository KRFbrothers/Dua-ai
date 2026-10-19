package com.dua.ai.ondevice

import android.content.Context
import androidx.annotation.VisibleForTesting

/** Where the app gets its on-device helpers. Tests swap in fakes and call [reset] afterwards. */
object OnDeviceServices {
    @Volatile @VisibleForTesting var textModel: (Context) -> OnDeviceTextModel = { GeminiNanoTextModel(it) }
    @Volatile @VisibleForTesting var backgroundRemover: (Context) -> BackgroundRemover = { MlKitBackgroundRemover(it) }

    @VisibleForTesting fun reset() {
        textModel = { GeminiNanoTextModel(it) }
        backgroundRemover = { MlKitBackgroundRemover(it) }
    }
}

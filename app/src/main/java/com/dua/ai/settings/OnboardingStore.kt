package com.dua.ai.settings

import android.content.Context

/** Remembers that the welcome screen was skipped, so it is not shown on every launch. Sign-in stays available in Profile. */
class OnboardingStore(context: Context) {
    private val storage = context.getSharedPreferences("dua_onboarding", Context.MODE_PRIVATE)
    var skipped: Boolean
        get() = storage.getBoolean("skipped", false)
        set(value) { storage.edit().putBoolean("skipped", value).apply() }
}

/*
 * Copyright 2026 The Dua AI Authors
 * Adapted from Now in Android (benchmarks/), Copyright 2022 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dua.ai.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile for the Dua app. Run on a connected phone:
 *   ./gradlew :app:generateBaselineProfile
 * Journeys: cold start, open drawer, open chat, scroll, type + send (no network).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        allowNotifications()
        skipOnboardingIfShown()
        waitForHome()
    }

    @Test
    fun coreJourneys() = rule.collect(PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()
        allowNotifications()
        skipOnboardingIfShown()
        waitForHome()
        typeAndSendOffline()
        scrollChat()
        openDrawer()
        if (openFirstChat()) scrollChat()
    }
}

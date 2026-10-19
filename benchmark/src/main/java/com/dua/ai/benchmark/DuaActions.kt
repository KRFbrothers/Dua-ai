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

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.dua.ai"
private const val WAIT_MS = 5_000L

/** Grants notifications so the permission dialog never blocks a journey (as in Now in Android). */
fun MacrobenchmarkScope.allowNotifications() {
    device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
}

/** First run shows onboarding; skip it if present. */
fun MacrobenchmarkScope.skipOnboardingIfShown() {
    device.wait(Until.findObject(By.text("Skip for now")), 2_000)?.click()
    device.waitForIdle()
}

/** Wait until Home's composer is visible. */
fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.desc("Open chat history")), WAIT_MS)
}

fun MacrobenchmarkScope.openDrawer() {
    device.findObject(By.desc("Open chat history"))?.click()
    device.waitForIdle()
}

const val BENCHMARK_MESSAGE = "Hello from the benchmark"

/** Opens the chat created by [typeAndSendOffline] from the drawer. Returns false if it isn't listed. */
fun MacrobenchmarkScope.openFirstChat(): Boolean {
    val row = device.wait(Until.findObject(By.textContains(BENCHMARK_MESSAGE.take(12))), 2_000) ?: run {
        device.pressBack(); return false
    }
    row.click()
    device.waitForIdle()
    return true
}

fun MacrobenchmarkScope.scrollChat() {
    val list = device.findObject(By.scrollable(true)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) { list.fling(Direction.UP) }
    list.fling(Direction.DOWN)
    device.waitForIdle()
}

/**
 * Types and sends a message with networking switched off ("fake"/no network), so the
 * benchmark never calls the real Dua servers and the offline/error path is exercised.
 */
fun MacrobenchmarkScope.typeAndSendOffline(text: String = BENCHMARK_MESSAGE) {
    device.executeShellCommand("cmd connectivity airplane-mode enable")
    try {
        val input = device.wait(Until.findObject(By.text("Ask Dua AI…")), WAIT_MS)
            ?: device.findObject(By.clazz("android.widget.EditText")) ?: return
        input.click()
        input.text = text
        device.wait(Until.findObject(By.desc("Send message")), WAIT_MS)?.click()
        device.waitForIdle()
        Thread.sleep(1_000)
    } finally {
        device.executeShellCommand("cmd connectivity airplane-mode disable")
    }
}

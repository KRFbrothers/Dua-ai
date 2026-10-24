/*
 * Copyright 2026 The Dua AI Authors. Adapted from Now in Android (benchmarks/build.gradle.kts),
 * Copyright 2022 The Android Open Source Project, Apache License 2.0. See THIRD_PARTY_NOTICES.md.
 */
// Test-only module: macrobenchmarks + Baseline Profile generator for :app.
// Nothing here is packaged in the Dua app. Needs a real phone (USB or wireless adb); see docs/PERFORMANCE.md.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.dua.ai.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    // Use the phone connected over adb (no Gradle-managed emulator).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
}

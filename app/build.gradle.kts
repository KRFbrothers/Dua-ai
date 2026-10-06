import org.gradle.api.execution.TaskExecutionGraph
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val duaApiBaseUrl = providers.gradleProperty("duaApiBaseUrl")
    .orElse(providers.environmentVariable("DUA_API_BASE_URL"))
    .orElse("https://dua-cyan.vercel.app").get()
require(duaApiBaseUrl.startsWith("https://") && !duaApiBaseUrl.contains('"')) {
    "duaApiBaseUrl must be an HTTPS URL"
}

fun releaseSetting(propertyName: String, environmentName: String) =
    providers.gradleProperty(propertyName).orElse(providers.environmentVariable(environmentName)).orNull

val releaseStoreFile = releaseSetting("duaReleaseStoreFile", "DUA_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSetting("duaReleaseStorePassword", "DUA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("duaReleaseKeyAlias", "DUA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("duaReleaseKeyPassword", "DUA_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
require(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningConfigured) {
    "Release signing requires DUA_RELEASE_STORE_FILE, DUA_RELEASE_STORE_PASSWORD, DUA_RELEASE_KEY_ALIAS, and DUA_RELEASE_KEY_PASSWORD."
}

gradle.taskGraph.whenReady({
    val releaseArtifactRequested = allTasks.any { task ->
        task.name == "assembleRelease" || task.name == "bundleRelease" || task.name.startsWith("packageRelease")
    }
    if (releaseArtifactRequested) {
        require(releaseSigningConfigured) {
            "Release APK/AAB tasks require signing credentials from environment variables or Gradle properties; unsigned release artifacts are not produced."
        }
        require(File(releaseStoreFile!!).isFile) {
            "DUA_RELEASE_STORE_FILE must point to an existing keystore file."
        }
    }
})

android {
    namespace = "com.dua.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dua.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
        buildConfigField("String", "DUA_API_BASE_URL", "\"" + duaApiBaseUrl + "\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.create("release").apply {
                    storeFile = File(releaseStoreFile!!)
                    storePassword = releaseStorePassword!!
                    keyAlias = releaseKeyAlias!!
                    keyPassword = releaseKeyPassword!!
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.json:json:20240303")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.ui.tooling)
}

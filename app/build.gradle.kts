import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Account, cloud sync, files, Imagine, voice, setup and connectors: the Node API (web/) on Cloud Run (dua-api).
// Override with -PduaApiBaseUrl=https://... (or DUA_API_BASE_URL) for a different Node API deployment.
val duaBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()?.takeIf { it in 1..1_000_000 }
val duaVersionCode = 12 + (duaBuildNumber ?: 0)
val duaVersionName = "1.0.11" + (duaBuildNumber?.let { " (build $it)" } ?: "")

val duaApiBaseUrl = providers.gradleProperty("duaApiBaseUrl")
    .orElse(providers.environmentVariable("DUA_API_BASE_URL"))
    .orElse("https://dua-api-228305912525.us-central1.run.app").get()
require(duaApiBaseUrl.startsWith("https://") && !duaApiBaseUrl.contains('"')) {
    "duaApiBaseUrl must be an HTTPS URL"
}

// Chat (POST /api/chat) can use a separate engine, by default the Google Cloud Run backend
// (google-cloud-backend/). Everything else (account, files, imagine, voice, setup) stays on duaApiBaseUrl.
// Set -PduaChatBaseUrl=https://dua-api-228305912525.us-central1.run.app (or DUA_CHAT_BASE_URL) to send chat to the Node API instead.
val duaChatBaseUrl = providers.gradleProperty("duaChatBaseUrl")
    .orElse(providers.environmentVariable("DUA_CHAT_BASE_URL"))
    .orElse("https://dua-ai-engine-228305912525.us-central1.run.app").get()
require(duaChatBaseUrl.startsWith("https://") && !duaChatBaseUrl.contains('"')) {
    "duaChatBaseUrl must be an HTTPS URL"
}
// Optional shared secret for the Cloud Run engine (its DUA_APP_TOKEN). Never commit it: pass
// -PduaAppToken=... or the DUA_APP_TOKEN environment variable when building. Empty = header not sent.
val duaAppToken = providers.gradleProperty("duaAppToken")
    .orElse(providers.environmentVariable("DUA_APP_TOKEN"))
    .orElse("").get().trim()
require(duaAppToken.matches(Regex("[A-Za-z0-9._-]*"))) { "DUA_APP_TOKEN may only use letters, digits, '.', '_' and '-'." }

val googleWebClientId = providers.gradleProperty("duaGoogleWebClientId")
    .orElse(providers.environmentVariable("GOOGLE_CLIENT_ID"))
    .orElse(providers.gradleProperty("duaDefaultGoogleWebClientId"))
    .orElse("").get().trim()
require(googleWebClientId.matches(Regex("[A-Za-z0-9._-]*"))) { "GOOGLE_CLIENT_ID contains invalid characters." }

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

// Public certificate only. The private keystore and its passwords stay outside the repository.
val ownerCertificate = Properties().apply {
    file("signing-certificate.properties").inputStream().use { load(it) }
}
val ownerCertificateSha256 = ownerCertificate.getProperty("sha256").replace(":", "").uppercase()
require(ownerCertificateSha256.matches(Regex("[0-9A-F]{64}"))) {
    "signing-certificate.properties must contain the owner's SHA-256 certificate fingerprint."
}

fun validatePersonalReleaseSigning() {
    require(releaseSigningConfigured) {
        "Personal release builds require the permanent keystore and all four DUA_RELEASE_* signing values. See SIGNING.md."
    }
    require(googleWebClientId.matches(Regex("[A-Za-z0-9._-]+\\.apps\\.googleusercontent\\.com"))) {
        "A release APK requires the Google OAuth web client ID. See SIGNING.md."
    }
    val keyFile = File(releaseStoreFile!!)
    require(keyFile.isFile) { "DUA_RELEASE_STORE_FILE must point to the permanent keystore." }
    val store = KeyStore.getInstance(keyFile, releaseStorePassword!!.toCharArray())
    require(store.isKeyEntry(releaseKeyAlias!!)) { "The configured signing alias has no private key." }
    require(store.getKey(releaseKeyAlias, releaseKeyPassword!!.toCharArray()) != null) {
        "The configured signing key could not be unlocked."
    }
    val certificate = store.getCertificate(releaseKeyAlias)
    val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        .joinToString("") { "%02X".format(it) }
    require(actual == ownerCertificateSha256) {
        "Signing certificate does not match the permanent owner certificate. Restore the original keystore; do not generate a replacement."
    }
}

tasks.register("validatePersonalReleaseSigning") {
    group = "verification"
    description = "Checks the permanent signing certificate and Google OAuth client before a personal release."
    doLast { validatePersonalReleaseSigning() }
}

gradle.taskGraph.whenReady({
    val releaseArtifactRequested = allTasks.any { task ->
        task.name == "assembleRelease" || task.name == "bundleRelease" || task.name.startsWith("packageRelease")
    }
    if (releaseArtifactRequested) {
        validatePersonalReleaseSigning()
    }
})

android {
    namespace = "com.dua.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dua.ai"
        minSdk = 26
        targetSdk = 35
        // Each personal-APK workflow run gets a higher versionCode, so Android sees a real update
        // (local builds stay at the base). Never lower the base: Android refuses downgrades.
        versionCode = duaVersionCode
        versionName = duaVersionName
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"" + googleWebClientId + "\"")
        buildConfigField("String", "DUA_API_BASE_URL", "\"" + duaApiBaseUrl + "\"")
        buildConfigField("String", "DUA_CHAT_BASE_URL", "\"" + duaChatBaseUrl + "\"")
        buildConfigField("String", "DUA_APP_TOKEN", "\"" + duaAppToken + "\"")
    }

    buildTypes {
        release {
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
            // R8 removes unused code (most of material-icons-extended) and unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.create("release").apply {
                    storeFile = File(releaseStoreFile!!)
                    storePassword = releaseStorePassword!!
                    keyAlias = releaseKeyAlias!!
                    keyPassword = releaseKeyPassword!!
                }
            } else {
                // Missing keys are rejected before any release packaging task runs.
                null
            }
            // Names are kept (-dontobfuscate), so there is no mapping file to upload to Crashlytics.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> { mappingFileUploadEnabled = false }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Roborazzi screenshot tests (src/test/.../screenshots) need Robolectric's Android runtime
// download and are slow, so the default test run skips them. Record with:
//   ./gradlew :app:recordRoborazziDebug -Pdua.screenshots
val screenshotTestsEnabled = providers.gradleProperty("dua.screenshots").isPresent
tasks.withType<Test>().configureEach {
    if (!screenshotTestsEnabled) exclude("**/screenshots/**")
    if (providers.gradleProperty("dua.ui-record").isPresent) systemProperty("roborazzi.test.record", "true")
}

dependencies {
    // Streaming authenticated encryption for password-protected, bounded-memory data backups.
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.work:work-testing:2.10.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.json:json:20240303")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation(libs.androidx.core.ktx)
    // Installs the bundled baseline profile (src/main/baseline-prof.txt) on sideloaded APKs too: faster cold start.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)
    // Imagine: inline and fullscreen playback of generated videos.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

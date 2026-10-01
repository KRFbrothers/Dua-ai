# Nova — Gradle + resources

Latest verified stable BOM (2026-09): `androidx.compose:compose-bom:2026.09.00`
That BOM pins Material 3 to `1.4.0` and Compose UI to `1.12.1`.

## Module `app/build.gradle.kts` (needed bits)

```kotlin
android {
    namespace = "com.nova.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nova.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" } // or use Kotlin 2.0 compose compiler plugin
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

If your AGP is 9+ / Kotlin 2.2+, drop `composeOptions` and apply:

```
plugins { id("org.jetbrains.kotlin.plugin.compose") }
```

## Drawables / vectors to add later
No custom XML vectors are required to compile. Icons come from Material Icons Extended.

Optional brand assets:
- `ic_nova_mark.xml` — 24dp AutoAwesome-style ray in a ring
- `ic_launcher_foreground.xml` — same mark, 108dp safe zone
- `onboard_focus.xml`, `onboard_notes.xml`, `onboard_habits.xml` if you replace icon tiles with illustration

## AndroidManifest
```xml
<application
    android:label="@string/app_name"
    android:theme="@style/Theme.Nova"
    android:icon="@mipmap/ic_launcher">
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:windowSoftInputMode="adjustResize">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

## File map
```
app/src/main/java/com/nova/app/MainActivity.kt
app/src/main/java/com/nova/app/ui/theme/Color.kt
app/src/main/java/com/nova/app/ui/theme/Type.kt
app/src/main/java/com/nova/app/ui/theme/Shape.kt
app/src/main/java/com/nova/app/ui/theme/Theme.kt
app/src/main/java/com/nova/app/ui/navigation/Routes.kt
app/src/main/java/com/nova/app/ui/navigation/NavGraph.kt
app/src/main/java/com/nova/app/ui/components/NovaComponents.kt
app/src/main/java/com/nova/app/ui/screens/SplashScreen.kt
app/src/main/java/com/nova/app/ui/screens/OnboardingScreen.kt
app/src/main/java/com/nova/app/ui/screens/AuthScreens.kt
app/src/main/java/com/nova/app/ui/screens/HomeScreen.kt
app/src/main/java/com/nova/app/ui/screens/TaskScreens.kt
app/src/main/java/com/nova/app/ui/screens/NoteScreens.kt
app/src/main/java/com/nova/app/ui/screens/HabitsScreen.kt
app/src/main/java/com/nova/app/ui/screens/ProfileScreens.kt
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
```

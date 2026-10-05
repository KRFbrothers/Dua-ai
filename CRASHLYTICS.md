# Android crash reports

Dua's native Android client uses Firebase Crashlytics in the Firebase project
`dua-ansari`, registered as `com.dua.ai`. Vercel hosting, Supabase accounts and the
Google coding agent project remain separate. No Analytics SDK is included.

The Google Services plugin reads `app/google-services.json`; Firebase's startup
provider initializes the SDK. No Activity initialization or forced crash is needed.
The JSON is Android client configuration, not an Admin SDK service-account key.
Do not replace it with a service-account credential. Keep private backend keys out
of Android resources.

Automatic crash reporting is enabled only in the `release` build type. It is disabled
in debug builds, including routine development checks. The collection setting is
the manifest placeholder `crashlyticsCollectionEnabled` in `app/build.gradle.kts`.
Crash reports contain stack traces and diagnostic device/OS/app information sent
to Firebase. No custom chat text, account IDs, API responses or breadcrumbs are
recorded by this integration. Before distributing a release, disclose crash-data
collection in the app's privacy information and applicable store data declarations.
There is currently no in-app crash-reporting toggle.

Pinned dependencies: Firebase BoM 34.7.0 (Crashlytics 20.0.3), Google Services
plugin 4.4.4 and Crashlytics plugin 3.0.6. They use the existing Google/Maven
repositories. The existing AGP 8.8.0 and Gradle 8.11.1 meet the Crashlytics v3
plugin requirements. Firebase Android SDK is Apache-2.0 licensed; verify current
[Firebase terms](https://firebase.google.com/terms) for service use. Crashlytics
is a no-cost Firebase product; this change does not enable other paid services.

## Verify without packaging an APK

With JDK 17 and Android SDK 35 configured, run:

```sh
bash ./gradlew :app:testDebugUnitTest :app:compileReleaseKotlin :app:processReleaseMainManifest --no-daemon --max-workers=2
```

Check that generated debug/release manifests contain false/true respectively for
`firebase_crashlytics_collection_enabled`, and that generated Google resources
match `dua-ansari` and the registered Android App ID. Do not publish generated
resource dumps. Unit tests and compilation cannot prove reports reach Firebase.

## Later live verification

Merging code does not update an already installed Android app. A later Android
release must include this integration; APK packaging is deferred at the owner's
request. Open Firebase console -> `dua-ansari` -> Crashlytics and finish any product
onboarding if prompted. To verify delivery later, use a dedicated internal release
build with a temporary test-only crash, on a test device without unsaved work.
Restart after the crash with network access and check for the report in Firebase.
Remove the test trigger before distribution. This repository includes no test crash.
An ordinary debug build will not automatically submit reports.

Reference: [Crashlytics Android setup](https://firebase.google.com/docs/crashlytics/get-started?platform=android).

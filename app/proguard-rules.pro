# Dua release build: R8 shrinking + optimization, resource shrinking. Most libraries ship their own
# consumer rules (OkHttp, Compose, Media3, Firebase, AndroidX); the rules below cover the rest.

# Keep class and method names so Crashlytics stack traces stay readable without uploading a
# mapping file (this is a personal app; size comes from removing unused code, not renaming).
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Credential Manager loads its Play Services provider by reflection (androidx.credentials docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
# Google ID token credential types are created from Bundles by name.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# OkHttp optional platform integrations that are not on Android.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

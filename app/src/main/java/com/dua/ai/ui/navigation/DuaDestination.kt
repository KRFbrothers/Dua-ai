package com.dua.ai.ui.navigation

/** Every screen in the app. Routes are plain strings; no screen takes navigation arguments. */
enum class DuaDestination(val route: String) {
    Onboarding("onboarding"),
    Chat("chat"),
    Voice("voice"),
    HandsFree("voice/hands-free"),
    LiveVoice("voice/live"),
    Settings("settings"),
    Profile("settings/account/profile"),
    CloudSync("settings/account/cloud-sync"),
    Appearance("settings/appearance"),
    Memory("settings/memory"),
    DataControls("settings/data-controls"),
    Privacy("settings/privacy"),
    Sharing("settings/sharing"),
    Permissions("settings/permissions"),
    Notifications("settings/notifications"),
    Imagine("settings/imagine"),
    VoiceSettings("settings/voice"),
    VoicePicker("settings/voice/voices"),
    ReplyLanguage("settings/voice/reply-language"),
    InputLanguage("settings/voice/input-language"),
    LiveVoicePicker("settings/voice/live-voices"),
    FeatureSetup("settings/feature-setup");

    companion object {
        /** Maps the page names used inside the device settings screens to destinations. */
        fun fromPage(name: String): DuaDestination? = when (name) {
            "ColorMode", "FontStyle", "Appearance" -> Appearance
            "Memory" -> Memory
            "DataControls" -> DataControls
            "Privacy" -> Privacy
            "Sharing" -> Sharing
            "Permissions" -> Permissions
            "Notifications" -> Notifications
            "Imagine" -> Imagine
            "IntegrationSetup", "FeatureSetup" -> FeatureSetup
            "Voice" -> Voice
            "HandsFree" -> HandsFree
            "VoiceSettings" -> VoiceSettings
            "LiveVoice" -> LiveVoice
            else -> null
        }
    }
}

package com.dua.ai.ui.navigation

/** Every screen in the app. Routes are plain strings; no screen takes navigation arguments. */
enum class DuaDestination(val route: String) {
    Chat("chat"),
    Voice("voice"),
    HandsFree("voice/hands-free"),
    Settings("settings"),
    Profile("settings/account/profile"),
    CloudSync("settings/account/cloud-sync"),
    Logout("settings/account/log-out"),
    Appearance("settings/appearance"),
    Memory("settings/memory"),
    DataControls("settings/data-controls"),
    Privacy("settings/privacy"),
    Sharing("settings/sharing"),
    Permissions("settings/permissions"),
    Notifications("settings/notifications"),
    Imagine("settings/imagine"),
    FeatureSetup("settings/feature-setup");

    companion object {
        /** Maps the page names used inside the account and device settings screens to destinations. */
        fun fromPage(name: String): DuaDestination? = when (name) {
            "Profile" -> Profile
            "CloudSync" -> CloudSync
            "Logout" -> Logout
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
            else -> null
        }
    }
}

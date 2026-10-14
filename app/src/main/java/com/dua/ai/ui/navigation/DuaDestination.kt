package com.dua.ai.ui.navigation

/** Every screen in the app. Routes are plain strings; no screen takes navigation arguments. */
enum class DuaDestination(val route: String) {
    Onboarding("onboarding"),
    Chat("chat"),
    Voice("voice"),
    Computer("computer"),
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
    FeatureSetup("settings/feature-setup"),
    Account("settings/account"),
    EmailAccount("settings/account/email"),
    Accent("settings/accent"),
    Images("images"), Library("library"), Agent("agent"), Work("work"), Plugins("plugins"),
    General("settings/general"), UsageLimits("settings/usage-limits"), Safety("settings/safety"),
    SecurityLogin("settings/security-login"), Storage("settings/storage"), HistoryData("settings/history-data"),
    SettingsSearch("settings/search"), AvatarEdit("settings/avatar");

    /** Dedicated screens absent from the source inventory; navigation stays honest about availability. */
    val comingSoonTitle: String? get() = when (this) {
        Images -> "Images"; Library -> "Library"; Work -> "Work"; Plugins -> "Plugins"
        General -> "General"; UsageLimits -> "Usage and limits"; Safety -> "Safety"
        SecurityLogin -> "Security and login"; Storage -> "Storage"
        SettingsSearch -> "Search settings"; AvatarEdit -> "Edit profile photo"
        Memory -> "Memory"; Privacy -> "Privacy"; Sharing -> "Sharing"; Permissions -> "Permissions"; Notifications -> "Notifications"
        else -> null
    }
}

package com.dua.ai.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.components.SettingsDivider
import com.dua.ai.ui.components.SettingsRow
import com.dua.ai.ui.components.SettingsSection
import com.dua.ai.ui.navigation.DuaDestination

/** Existing data actions grouped under the profile's History and data entry. */
@Composable
fun HistoryDataScreen(onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    DuaPageScaffold("History and data", onBack) {
        SettingsSection(null) {
            SettingsRow(Icons.Outlined.Storage, "Data controls", "Export, import or clear chats and files") { onOpen(DuaDestination.DataControls) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Shield, "Your data", "Chats and memory live with your account") { onOpen(DuaDestination.Privacy) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.IosShare, "Sharing", "Share a conversation as text") { onOpen(DuaDestination.Sharing) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Security, "Permissions", "Microphone, camera and files") { onOpen(DuaDestination.Permissions) }
        }
    }
}

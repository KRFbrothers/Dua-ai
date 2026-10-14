package com.dua.ai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.chat.DuaDialog
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.screens.ProfileActions
import com.dua.ai.ui.screens.ProfileUiState
import com.dua.ai.ui.theme.*

/** Account details use existing password, sync and logout actions; unsupported phone/Pro changes are explicit. */
@Composable
internal fun AccountContent(state: ProfileUiState, actions: ProfileActions, onBack: () -> Unit,
    onLogOutRequest: () -> Unit, onDeleteRequest: () -> Unit, showLogout: Boolean) {
    val c = SettingsTheme.colors
    var phoneOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailable by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState()).padding(SettingsSizes.screenPadding).testTag("account-screen")) {
        Box(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch), contentAlignment = Alignment.Center) {
            Text("Account", style = SettingsNameStyle, color = c.name, modifier = Modifier.semantics { heading() })
            Box(Modifier.align(Alignment.CenterStart)) { CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) }
        }
        Spacer(Modifier.height(SettingsSizes.cardGap))
        SettingsCardBox {
            SettingsItemRow(ReferenceIcons.Email, "Email", state.email, enabled = false) {}
            SettingsItemRow(ReferenceIcons.Phone, "Phone number", state.phoneNumber.ifBlank { "Add" }, enabled = !state.busy) {
                phoneOwner = state.accountId
            }
            SettingsItemRow(ReferenceIcons.Key, "Set email password", enabled = state.emailAuthConfigured && !state.busy) { actions.onEmail() }
            SettingsItemRow(ReferenceIcons.Sync, "Sync", state.sync, enabled = !state.busy) { actions.onSync() }
            SettingsItemRow(ReferenceIcons.Sparkle, "Upgrade to Dua AI Pro", tint = c.upgrade, showDivider = false) { unavailable = "Dua AI Pro" }
        }
        if (showLogout) {
            Spacer(Modifier.height(SettingsSizes.cardGap))
            SettingsCardBox {
                SettingsItemRow(ReferenceIcons.LogOut, "Log out", tint = c.danger, showDivider = false, enabled = !state.busy) { onLogOutRequest() }
            }
        }
        // Preserve the existing destructive account action and its typed confirmation.
        if (state.deletionConfigured) SheetTextButton("Delete account", onClick = onDeleteRequest, enabled = !state.busy,
            destructive = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (state.notice.isNotBlank()) Text(state.notice, style = SettingsValueStyle, color = c.value, modifier = Modifier.padding(top = SettingsSizes.rowGap))
    }
    phoneOwner?.let { owner -> PhoneNumberDialog(state.phoneNumber, state.busy, onDismiss = { phoneOwner = null },
        onSave = { phone -> actions.onSavePhone(phone, owner) }) }
    unavailable?.let { title -> DuaDialog(onDismissRequest = { unavailable = null }, title = title,
        text = { Text("Coming soon. Pro subscriptions are not available.", style = SheetBodyStyle) },
        confirmButton = { SheetTextButton("Close", onClick = { unavailable = null }) }) }
}

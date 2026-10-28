package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dua.ai.ui.components.AccountAvatar
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetSubStyle
import com.dua.ai.ui.theme.SheetTheme
import com.dua.ai.ui.theme.SheetTitleStyle
import com.dua.ai.ui.theme.Spacing

/** Dismissible Home account overlay. Each row opens an existing Dua destination. */
@Composable
internal fun HomeProfileDialog(state: ChatUiState, onDismiss: () -> Unit,
    onSettings: () -> Unit, onDestination: (DuaDestination) -> Unit) {
    val colors = SheetTheme.colors
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Spacing.sm)
                .testTag("home-profile-dialog"), shape = SheetShapes.dialog, color = colors.container) {
                Column(Modifier.padding(Spacing.md)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dua AI", style = SheetTitleStyle, color = colors.title,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        IconButton(onClick = onDismiss, modifier = Modifier.size(SheetSizes.touch)) {
                            Icon(Icons.Outlined.Close, "Close profile", tint = colors.icon)
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SheetGroup {
                            Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                AccountAvatar(state.profileEmail, state.profileName, state.profilePhotoUrl, SheetSizes.touch,
                                    tag = "home-profile-avatar")
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(state.profileName.ifBlank { if (state.profileEmail.isNullOrBlank()) "Guest" else "Your account" }, style = SheetTitleStyle, color = colors.title)
                                    state.profileEmail?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = SheetSubStyle, color = colors.subText)
                                    }
                                }
                            }
                            SheetActionRow(if (state.profileEmail.isNullOrBlank()) "Sign in" else "Manage account",
                                Icons.Outlined.PersonOutline, { onDestination(DuaDestination.Account) })
                        }
                        SheetGroup {
                            SheetActionRow("Usage limits", Icons.Outlined.DataUsage, { onDestination(DuaDestination.UsageLimits) })
                            SheetDivider()
                            SheetActionRow("Personal memory", Icons.Outlined.Psychology, { onDestination(DuaDestination.Memory) })
                            SheetDivider()
                            SheetActionRow("Notifications", Icons.Outlined.NotificationsNone, { onDestination(DuaDestination.Notifications) })
                            SheetDivider()
                            SheetActionRow("Settings", Icons.Outlined.Settings, onSettings)
                        }
                    }
                }
            }
        }
    }
}

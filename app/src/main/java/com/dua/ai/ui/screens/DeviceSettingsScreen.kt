package com.dua.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.theme.SettingsAvatarStyle
import com.dua.ai.ui.theme.SettingsNameStyle
import com.dua.ai.ui.theme.SettingsRowStyle
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.SettingsTheme
import com.dua.ai.ui.theme.SettingsValueStyle
import com.dua.ai.viewmodel.ChatViewModel

@Composable
fun DeviceSettingsScreen(
    viewModel: ChatViewModel,
    page: String = "",
    onBack: () -> Unit = {},
    onPage: (String) -> Unit = {},
    onAttachments: () -> Unit = {}
) {
    val account by viewModel.account.state.collectAsState()
    val header = com.dua.ai.ui.settings.accountHeader(account.user?.displayName, account.user?.email)
    val userEmail = header.email
    val userName = header.name
    val c = SettingsTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(SettingsSizes.screenPadding)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. TOP HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SettingsSizes.headerBottom),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(SettingsSizes.touch)
                    .padding((SettingsSizes.touch - SettingsSizes.circleButton) / 2)
                    .clip(SettingsShapes.circle)
                    .background(c.circleButton)
                    .border(SettingsSizes.border, c.circleBorder, SettingsShapes.circle)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = c.circleIcon)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(SettingsSizes.avatar)
                            .clip(CircleShape)
                            .background(c.avatar),
                        contentAlignment = Alignment.Center
                    ) {
                        // Account initial, or a neutral person icon when signed out.
                        if (header.initial != null) Text(text = header.initial, style = SettingsAvatarStyle, color = c.onAvatar)
                        else Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = c.onAvatar)
                    }
                    Box(
                        modifier = Modifier
                            .size(SettingsSizes.editBadge)
                            .clip(SettingsShapes.circle)
                            .background(c.editBadge)
                            .border(SettingsSizes.border, c.circleBorder, SettingsShapes.circle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(SettingsSizes.editIcon), tint = c.editIcon)
                    }
                }
                Spacer(modifier = Modifier.height(SettingsSizes.nameTop))
                Text(userName, style = SettingsNameStyle, color = c.name, modifier = Modifier.semantics { heading() })
            }

            IconButton(
                onClick = { onPage("About") },
                modifier = Modifier
                    .size(SettingsSizes.touch)
                    .padding((SettingsSizes.touch - SettingsSizes.circleButton) / 2)
                    .clip(SettingsShapes.circle)
                    .background(c.circleButton)
                    .border(SettingsSizes.border, c.circleBorder, SettingsShapes.circle)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = "About", tint = c.circleIcon)
            }
        }

        Spacer(modifier = Modifier.height(SettingsSizes.cardsTop))

        // --- CARD 1: ACCOUNT ONLY ---
        SettingsCardBox {
            SettingsRowItem(
                icon = Icons.Outlined.Person,
                label = "Account",
                value = userEmail,
                onClick = { onPage("Account") }
            )
        }

        Spacer(modifier = Modifier.height(SettingsSizes.cardGap))

        // --- CARD 2: PERSONALIZATION GROUP ---
        SettingsCardBox {
            SettingsRowItem(Icons.Outlined.SentimentSatisfied, "Personalization", showDivider = true) {
                onPage("Personalization")
            }
            SettingsRowItem(Icons.Outlined.MenuBook, "Memory", showDivider = true) {
                onPage("Memory")
            }
            SettingsRowItem(Icons.Outlined.Extension, "Plugins", showDivider = true) {
                onPage("Plugins")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsSizes.touch)
                    .clickable(onClickLabel = "Open accent color", role = Role.Button) { onPage("AccentColor") }
                    .padding(SettingsSizes.rowPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = c.rowIcon, modifier = Modifier.size(SettingsSizes.rowIcon))
                    Text("Accent color", style = SettingsRowStyle, color = c.rowText)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.accentGap)) {
                    Box(modifier = Modifier.size(SettingsSizes.accentDot).clip(SettingsShapes.circle).background(c.upgrade))
                    Text("Purple", style = SettingsValueStyle, color = c.accentValue)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = c.arrow, modifier = Modifier.size(SettingsSizes.arrow))
                }
            }
        }

        Spacer(modifier = Modifier.height(SettingsSizes.cardGap))

        // --- CARD 3: GENERAL SETTINGS (Exact 8 items) ---
        SettingsCardBox {
            SettingsRowItem(Icons.Outlined.Settings, "General", showDivider = true) { onPage("General") }
            SettingsRowItem(Icons.Outlined.TrendingUp, "Usage and limits", showDivider = true) { onPage("Usage") }
            SettingsRowItem(Icons.Outlined.Notifications, "Notifications", showDivider = true) { onPage("Notifications") }
            SettingsRowItem(Icons.Outlined.GraphicEq, "Voice", showDivider = true) { onPage("Voice") }
            SettingsRowItem(Icons.Outlined.Shield, "Safety", showDivider = true) { onPage("Safety") }
            SettingsRowItem(Icons.Outlined.Lock, "Security and login", showDivider = true) { onPage("Security") }
            SettingsRowItem(Icons.Outlined.Storage, "Storage", showDivider = true) { onPage("Storage") }
            SettingsRowItem(Icons.Outlined.Archive, "History and data", showDivider = false) { onPage("DataControls") }
        }
    }
}

@Composable
private fun SettingsCardBox(content: @Composable ColumnScope.() -> Unit) {
    val c = SettingsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsShapes.card)
            .background(c.card)
            .border(SettingsSizes.border, c.cardBorder, SettingsShapes.card),
        content = content
    )
}

@Composable
private fun SettingsRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String? = null,
    showDivider: Boolean = false,
    onClick: () -> Unit
) {
    val c = SettingsTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsSizes.touch)
                .clickable(onClickLabel = "Open $label", role = Role.Button, onClick = onClick)
                .padding(SettingsSizes.rowPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = c.rowIcon, modifier = Modifier.size(SettingsSizes.rowIcon))
                Text(label, style = SettingsRowStyle, color = c.rowText)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.valueGap)) {
                if (value != null) Text(value, style = SettingsValueStyle, color = c.value)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.chevron, modifier = Modifier.size(SettingsSizes.chevron))
            }
        }
        if (showDivider) {
            HorizontalDivider(color = c.divider, thickness = SettingsSizes.border, modifier = Modifier.padding(start = SettingsSizes.dividerStart))
        }
    }
}

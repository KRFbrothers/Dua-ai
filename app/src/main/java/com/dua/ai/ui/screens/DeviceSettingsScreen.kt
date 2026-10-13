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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.viewmodel.ChatViewModel

@Composable
fun DeviceSettingsScreen(
    viewModel: ChatViewModel,
    page: String = "",
    onBack: () -> Unit,
    onPage: (String) -> Unit = {}
) {
    val accountState by viewModel.account.state.collectAsState()
    val userEmail = accountState.email ?: "fareedkrf@gmail.com"
    val userName = accountState.displayName.ifBlank { "Fareed" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. TOP HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE5E7EB), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1F2937))
            }

            // User Profile Photo + Name
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFDE68A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.firstOrNull()?.uppercase() ?: "F",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                    }
                    // Edit pencil badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE5E7EB), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(12.dp), tint = Color(0xFF374151))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(userName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            }

            IconButton(
                onClick = { onPage("About") },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE5E7EB), CircleShape)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = "Info", tint = Color(0xFF1F2937))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- CARD 1: ACCOUNT ONLY ---
        SettingsCardBox {
            SettingsRowItem(
                icon = Icons.Outlined.Person,
                label = "Account",
                value = userEmail,
                onClick = { onPage("Account") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

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

            // Accent Color (Purple Dropdown)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPage("AccentColor") }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                    Text("Accent color", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF8B5CF6)))
                    Text("Purple", fontSize = 13.sp, color = Color(0xFF4B5563))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(24.dp)),
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
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (value != null) {
                    Text(value, fontSize = 13.sp, color = Color(0xFF6B7280))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
            }
        }
        if (showDivider) {
            Divider(color = Color(0xFFF3F4F6), thickness = 1.dp, modifier = Modifier.padding(start = 46.dp))
        }
    }
}

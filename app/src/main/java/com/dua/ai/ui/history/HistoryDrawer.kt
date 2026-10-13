package com.dua.ai.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.history.SavedChat

@Composable
fun SearchField(
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // SearchField stub for SidebarPanel
}

@Composable
fun HistoryDrawer(
    chats: List<SavedChat> = emptyList(),
    currentChatId: String? = null,
    onSelectChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onSearch: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onDeleteChat: (String) -> Unit = {},
    onRenameChat: (String, String) -> Unit = { _, _ -> },
    serverDeletionEnabled: Boolean = false,
    onDeleteServerChat: (String) -> Unit = {},
    onOpenSettings: () -> Unit = onNavigateSettings,
    onProjects: () -> Unit = {},
    onDestination: (Any) -> Unit = {},
    profileName: String = "Fareed",
    activeChatId: String? = null,
    drawerOpen: Boolean = false,
    onCloseDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isAuraExpanded by remember { mutableStateOf(false) }

    val pluginsList = listOf(
        "Plugins",
        "Canva",
        "Figma",
        "Higgsfield",
        "Runway Assets",
        "tldraw"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: "Dua" + Search (🔍)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dua",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F4F6))
                        .clickable { onSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 6 Fixed Menu Items
            DrawerMenuRow(Icons.Outlined.Image, "Images")
            DrawerMenuRow(Icons.Outlined.CollectionsBookmark, "Library")
            DrawerMenuRow(Icons.Outlined.Folder, "Projects", onClick = onProjects)
            DrawerMenuRow(Icons.Outlined.DesktopWindows, "Computer")
            DrawerMenuRow(Icons.Outlined.SmartToy, "AI Agent")
            DrawerMenuRow(Icons.Outlined.WorkOutline, "Work")

            // --- EXPANDABLE AURA (Plugins Hub) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isAuraExpanded = !isAuraExpanded }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF111827),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Aura",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827)
                    )
                }

                Icon(
                    imageVector = if (isAuraExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isAuraExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isAuraExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 38.dp, top = 2.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pluginsList.forEach { pluginName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (pluginName == "Plugins") Icons.Outlined.Extension else Icons.Outlined.Grain,
                                contentDescription = null,
                                tint = if (pluginName == "Plugins") Color(0xFF8B5CF6) else Color(0xFF4B5563),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = pluginName,
                                fontSize = 15.sp,
                                fontWeight = if (pluginName == "Plugins") FontWeight.SemiBold else FontWeight.Normal,
                                color = if (pluginName == "Plugins") Color(0xFF111827) else Color(0xFF374151)
                            )
                        }
                    }
                }
            }

            Divider(
                color = Color(0xFFE5E7EB),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // --- RECENT CHAT HISTORY ---
            if (chats.isNotEmpty()) {
                chats.forEach { chat ->
                    Text(
                        text = chat.title.ifBlank { "New chat" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF1F2937),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChat(chat.id) }
                            .padding(vertical = 11.dp)
                    )
                }
            } else {
                val sampleHistory = listOf(
                    "Replace Dua",
                    "Tldraw samjhana",
                    "APK audit options",
                    "Greeting exchange",
                    "Git Pull Se Update",
                    "Image Error Explanation"
                )
                sampleHistory.forEach { title ->
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF1F2937),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChat(title) }
                            .padding(vertical = 11.dp)
                    )
                }
            }
        }

        // --- 2. BOTTOM FIXED DOCK ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF8B5CF6))
                    .clickable { onNewChat() }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Chat",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFDE68A))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profileName.firstOrNull()?.uppercase() ?: "F",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        fontSize = 16.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8B5CF6))
                        .clickable { onVoiceClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Voice",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF111827),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111827)
        )
    }
}

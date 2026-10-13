package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatTopBar(
    newChatEnabled: Boolean = true,
    showComputer: Boolean = true,
    onMenu: () -> Unit = {},
    onComputer: () -> Unit = {},
    onNewChat: () -> Unit = {},
    projectName: String = "Personal",
    onProjects: () -> Unit = {},
    onSearch: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left Menu Button (☰)
        IconButton(
            onClick = onMenu,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFF3F4F6))
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Open Drawer",
                tint = Color(0xFF1F2937)
            )
        }

        // 2. Center Pill Button: [ 💻 Computer ]
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF3F4F6))
                .clickable { onComputer() }
                .padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DesktopWindows,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF111827)
            )
            Text(
                text = "Computer",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
        }

        // 3. Right New Chat/Compose Button (Edit)
        IconButton(
            onClick = onNewChat,
            enabled = newChatEnabled,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFF3F4F6))
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = "New chat",
                tint = Color(0xFF1F2937)
            )
        }
   
    }
}

package com.dua.ai.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme

private data class SidebarItem(val title: String, val icon: ImageVector, val action: () -> Unit)

/** Only [recentChats] scrolls; all other controls belong to the fixed column. */
@Composable
fun SidebarPanel(aura: Boolean, onAura: (Boolean) -> Unit, query: String, onQuery: (String) -> Unit,
    newChatEnabled: Boolean, onNewChat: () -> Unit, onProfile: () -> Unit, onProjects: () -> Unit,
    onDestination: (DuaDestination) -> Unit, profileName: String = "", recentChats: @Composable ColumnScope.() -> Unit) {
    val availableWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    ModalDrawerSheet(modifier = Modifier.width(minOf(availableWidth, if (aura) 520.dp else 360.dp)).imePadding(),
        drawerContainerColor = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The approved vertical layout is used normally. Two columns preserve 48dp targets
            // on short windows (including the keyboard), without making menu/footer scroll.
            val compact = maxHeight < if (aura) 440.dp else 610.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("sidebar-header"), verticalAlignment = Alignment.CenterVertically) {
                    if (aura) IconButton(onClick = { onAura(false) }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to sidebar") }
                    Icon(if (aura) Icons.Outlined.AutoAwesome else SidebarCube, null,
                        tint = if (aura) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    Text(if (aura) "Aura" else "Dua AI", Modifier.padding(start = 12.dp).weight(1f),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 27.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SearchField(query, onQuery, Modifier.padding(bottom = 8.dp))
                val menu = if (aura) listOf(
                    SidebarItem("Plugins", Icons.Outlined.Extension) { onDestination(DuaDestination.Plugins) },
                    SidebarItem("Computer", Icons.Outlined.Computer) { onDestination(DuaDestination.Computer) },
                    SidebarItem("AI Agent", Icons.Outlined.SmartToy) { onDestination(DuaDestination.Agent) },
                    SidebarItem("Work", Icons.Outlined.WorkOutline) { onDestination(DuaDestination.Work) },
                ) else listOf(
                    SidebarItem("Images", Icons.Outlined.Image) { onDestination(DuaDestination.Images) },
                    SidebarItem("Library", Icons.AutoMirrored.Outlined.MenuBook) { onDestination(DuaDestination.Library) },
                    SidebarItem("Projects", Icons.Outlined.FolderOpen, onProjects),
                    SidebarItem("Computer", Icons.Outlined.Computer) { onDestination(DuaDestination.Computer) },
                    SidebarItem("AI Agent", Icons.Outlined.SmartToy) { onDestination(DuaDestination.Agent) },
                    SidebarItem("Work", Icons.Outlined.WorkOutline) { onDestination(DuaDestination.Work) },
                    SidebarItem("Aura", Icons.Outlined.AutoAwesome) { onAura(true) },
                )
                Column(Modifier.testTag("sidebar-menu")) {
                    if (compact) menu.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth()) { pair.forEach { item -> SidebarMenuRow(item, aura, Modifier.weight(1f), true) } }
                    } else menu.forEach { SidebarMenuRow(it, aura, Modifier.fillMaxWidth(), false) }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Recent chats", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                recentChats()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("sidebar-footer"), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Button(onClick = onNewChat, enabled = newChatEnabled, modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth().heightIn(min = 52.dp), shape = DuaShape.chip,
                            colors = ButtonDefaults.buttonColors(containerColor = DuaTheme.accent.color, contentColor = DuaTheme.accent.onColor)) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                            Text("New chat", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(onClick = onProfile, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Profile & Settings" }, contentColor = MaterialTheme.colorScheme.onSurface) {
                        Box(contentAlignment = Alignment.Center) {
                            // This is an account initial, never a fabricated profile photo.
                            val initial = profileName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                            if (initial == null) Icon(Icons.Outlined.PersonOutline, "Profile & Settings")
                            else Text(initial.toString(), modifier = Modifier, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarMenuRow(item: SidebarItem, chevron: Boolean, modifier: Modifier, compact: Boolean) {
    Row(modifier.heightIn(min = if (compact) 48.dp else 52.dp).clickable(role = Role.Button, onClick = item.action)
        .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(item.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.title, Modifier.weight(1f).padding(start = if (compact) 8.dp else 20.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chevron && !compact) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
    }
}

// Exact outlined cube from the approved header, separate from the unchanged full Dua logo.
private val SidebarCube by lazy {
    ImageVector.Builder("Dua cube", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 2f); lineTo(21f, 7f); lineTo(21f, 17f); lineTo(12f, 22f); lineTo(3f, 17f); lineTo(3f, 7f); close()
            moveTo(3f, 7f); lineTo(12f, 12f); lineTo(21f, 7f)
            moveTo(12f, 12f); lineTo(12f, 22f)
        }
    }.build()
}

package com.dua.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.icons.DuaIcons
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** "Computer" (from the Home top bar): a placeholder until computer use ships. Back arrow, monitor icon, title and a short note. */
@Composable
fun ComputerScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = DuaTheme.colors
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Sizes.topBarHeight).padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            // 44 dp round button inside a 48 dp touch target, like the chat top bar.
            Box(Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
                Surface(onClick = onBack, shape = CircleShape, color = colors.composer, contentColor = scheme.onSurface.copy(alpha = .86f),
                    modifier = Modifier.size(Sizes.topBarButton).semantics { contentDescription = "Back"; role = Role.Button }) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(Sizes.icon)) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().heightIn(min = 420.dp).padding(horizontal = Spacing.xxl).align(BiasAlignment(0f, -0.2f)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Spacer(Modifier.height(Spacing.huge))
                // Monitor in a soft silver ring, with the gold sparkle used on Home.
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(112.dp).background(colors.composer, CircleShape).border(1.dp, colors.composerBorder, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.DesktopWindows, contentDescription = null, tint = scheme.onSurface.copy(alpha = .9f), modifier = Modifier.size(52.dp))
                    }
                    Icon(DuaIcons.Sparkle, contentDescription = null, tint = colors.sparkle,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-2).dp).size(26.dp))
                }
                Spacer(Modifier.height(Spacing.xxl + Spacing.sm))
                Text("Computer", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.md))
                Surface(shape = DuaShape.chip, color = colors.chipSelected, contentColor = colors.onChipSelected) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DuaIcons.Sparkle, contentDescription = null, tint = colors.chipSelectedIcon, modifier = Modifier.size(Sizes.iconTiny))
                        Text("Coming soon", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.xs + Spacing.xxs))
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                Text("This feature is coming soon.", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.xs))
                Text("Dua will be able to use a computer for you.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
                Spacer(Modifier.height(Spacing.huge))
            }
        }
    }
}

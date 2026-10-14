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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import com.dua.ai.ui.icons.DuaIcons
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import androidx.compose.foundation.BorderStroke
import com.dua.ai.ui.theme.DuaPageTheme
import com.dua.ai.ui.theme.PageBodyStyle
import com.dua.ai.ui.theme.PageHeadlineStyle
import com.dua.ai.ui.theme.PageNoteStyle
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.SettingsTheme
import com.dua.ai.ui.theme.SheetButtonStyle
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.Spacing

/** "Computer" (from the Home top bar): a placeholder until computer use ships. Back arrow, monitor icon, title and a short note. */
@Composable
fun ComputerScreen(onBack: () -> Unit) = DuaPageTheme {
    val p = PageTheme.colors
    val c = SettingsTheme.colors
    val colors = DuaTheme.colors
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Sizes.topBarHeight).padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            // Settings-style bordered round back button filling the 48 dp touch target.
            Box(Modifier.size(SettingsSizes.touch), contentAlignment = Alignment.Center) {
                Surface(onClick = onBack, shape = SettingsShapes.circle, color = c.circleButton, contentColor = c.circleIcon,
                    border = BorderStroke(SettingsSizes.border, c.circleBorder),
                    modifier = Modifier.size(SettingsSizes.touch).semantics { contentDescription = "Back"; role = Role.Button }) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(Sizes.icon)) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().heightIn(min = PageSizes.heroMinHeight).padding(horizontal = Spacing.xxl).align(BiasAlignment(0f, -0.2f)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Spacer(Modifier.height(Spacing.huge))
                // Monitor in a bordered Settings-card ring, with the gold sparkle used on Home.
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(PageSizes.heroRing).background(p.card, SettingsShapes.circle).border(SettingsSizes.border, p.cardBorder, SettingsShapes.circle),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.DesktopWindows, contentDescription = null, tint = p.text, modifier = Modifier.size(PageSizes.heroIcon))
                    }
                    Icon(DuaIcons.Sparkle, contentDescription = null, tint = colors.sparkle,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = PageSizes.heroSparkleX, y = PageSizes.heroSparkleY).size(PageSizes.heroSparkle))
                }
                Spacer(Modifier.height(Spacing.xxl + Spacing.sm))
                Text("Computer", style = PageHeadlineStyle, color = p.text, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.md))
                Surface(shape = SheetShapes.button, color = p.accentContainer, contentColor = p.onAccentContainer) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DuaIcons.Sparkle, contentDescription = null, tint = p.accent, modifier = Modifier.size(Sizes.iconTiny))
                        Text("Coming soon", style = SheetButtonStyle, modifier = Modifier.padding(start = Spacing.xs + Spacing.xxs))
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                Text("This feature is coming soon.", style = PageBodyStyle, color = p.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.xs))
                Text("Dua will be able to use a computer for you.", style = PageNoteStyle, color = p.subText,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = PageSizes.heroTextMaxWidth))
                Spacer(Modifier.height(Spacing.huge))
            }
        }
    }
}

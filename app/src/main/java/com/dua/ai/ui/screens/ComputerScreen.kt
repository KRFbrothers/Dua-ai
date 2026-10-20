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
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dua.ai.ui.components.DuaIcons
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.DuaPageTheme
import com.dua.ai.ui.theme.PageBodyStyle
import com.dua.ai.ui.theme.PageNoteStyle
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.SheetButtonStyle
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.components.FeatureTopBar

/** "Computer" (from the Home top bar): a placeholder until computer use ships. Back arrow, monitor icon, title and a short note. */
@Composable
fun ComputerScreen(onBack: () -> Unit) = DuaPageTheme {
    val p = PageTheme.colors
    val colors = DuaTheme.colors
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        FeatureTopBar("Computer", onBack, onNewSession = {}, newSessionEnabled = false)
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

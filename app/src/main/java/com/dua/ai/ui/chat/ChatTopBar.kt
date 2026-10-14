package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.theme.HomeSegmentSelectedStyle
import com.dua.ai.ui.theme.HomeSegmentStyle
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.components.ReferenceIcons

/** Top-level tab in the centre pill, as in the reference shell. */
enum class HomeTab(val label: String) { Chat("Chat"), Work("Work") }

/**
 * Top bar from the reference shell: menu, centre [ Chat | Work ] pill and dashed new-chat icon.
 * In a conversation an extra "Find in chat" button sits beside the new-chat action.
 */
@Composable
fun ChatTopBar(
    newChatEnabled: Boolean = true,
    onMenu: () -> Unit = {},
    onNewChat: () -> Unit = {},
    tab: HomeTab = HomeTab.Chat,
    onTab: (HomeTab) -> Unit = {},
    onSearch: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = HomeSizes.topBarPaddingH, vertical = HomeSizes.topBarPaddingV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Icons.Outlined.Menu, "Open chat history", onMenu)
        SegmentedPill(tab, onTab, Modifier.weight(1f, fill = false))
        Row(verticalAlignment = Alignment.CenterVertically) {
            onSearch?.let { RoundButton(ReferenceIcons.Search, "Find in chat", it) }
            RoundButton(ReferenceIcons.NewChat, "New chat", onNewChat, newChatEnabled)
        }
    }
}

@Composable
private fun SegmentedPill(tab: HomeTab, onTab: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    val c = HomeTheme.colors
    val inset = HomeSizes.segmentTrackInset
    Row(
        modifier
            .heightIn(min = Sizes.touchTarget)
            .drawBehind {
                // The reference track: tab height plus a 3 dp inset, centred in the 48 dp touch row.
                val h = (HomeSizes.segmentTab + inset * 2).toPx()
                drawRoundRect(c.segmentTrack, Offset(0f, (size.height - h) / 2), Size(size.width, h),
                    CornerRadius(HomeSizes.segmentTab.toPx() / 2 + inset.toPx()))
            }
            .padding(horizontal = inset)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { t ->
            val selected = t == tab
            Box(
                Modifier.weight(1f, fill = false).heightIn(min = Sizes.touchTarget)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTab(t) }),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.height(HomeSizes.segmentTab).clip(HomeShapes.segmentTab)
                        .background(if (selected) c.segmentSelected else Color.Transparent)
                        .padding(horizontal = HomeSizes.segmentTabPaddingH),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.label, style = if (selected) HomeSegmentSelectedStyle else HomeSegmentStyle,
                        color = if (selected) c.segmentSelectedText else c.segmentText, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = HomeTheme.colors
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = description }) {
        Box(Modifier.size(HomeSizes.roundButton).clip(HomeShapes.round).background(c.roundButton), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.roundButtonIcon)
        }
    }
}

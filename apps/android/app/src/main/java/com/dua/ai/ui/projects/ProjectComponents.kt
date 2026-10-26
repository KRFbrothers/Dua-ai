package com.dua.ai.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.theme.*

/** Reuse verified reference glyphs where present. Remaining native glyphs need the supplied SVG assets for exact matching. */
internal object ProjectIcons {
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Plus = Icons.Outlined.Add
    val Folder = ReferenceIcons.Projects
    val Search = ReferenceIcons.Search
    val NewChat = ReferenceIcons.Chat
    val Plugins = ReferenceIcons.Plugins
    val Book = Icons.AutoMirrored.Outlined.MenuBook
    val Money = Icons.Outlined.AttachMoney
    val Writing = Icons.Outlined.Edit
    val Emoji = Icons.Outlined.AddReaction
    val More = Icons.Outlined.MoreVert
    val Pin = Icons.Outlined.PushPin
    val Settings = Icons.Outlined.Tune
    val Home = Icons.Outlined.Home
    val Delete = Icons.Outlined.DeleteOutline
    val Camera = Icons.Outlined.PhotoCamera
    val Photos = Icons.Outlined.Photo
    val File = Icons.Outlined.AttachFile
    val Mic = Icons.Outlined.Mic
    val Voice = Icons.Outlined.GraphicEq
}

@Composable internal fun projectIcon(id: String) = when (id) {
    "book" -> ProjectIcons.Book; "money" -> ProjectIcons.Money; "writing" -> ProjectIcons.Writing; else -> ProjectIcons.Folder
}
@Composable internal fun projectBadgeColors(id: String): Pair<Color, Color> = ProjectTheme.colors.let {
    when (id) { "blue" -> it.blueBg to it.blue; "coral" -> it.coralBg to it.coral; "teal" -> it.tealBg to it.teal; else -> it.purpleBg to it.purple }
}

@Composable internal fun ProjectCircle(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val c = ProjectTheme.colors
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = label }) {
        Surface(Modifier.size(ProjectSizes.circle), CircleShape, color = c.surface, shadowElevation = ProjectSizes.elevation) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(ProjectSizes.icon), tint = c.text.copy(alpha = if (enabled) 1f else SheetDisabledAlpha)) }
        }
    }
}
@Composable internal fun ProjectHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit) {
    val c = ProjectTheme.colors
    val stacked = LocalDensity.current.fontScale > 1.3f && title.length > 8
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = ProjectSizes.headerPadding, vertical = Spacing.sm)
        .testTag("projects-header")) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                ProjectCircle("Back", ProjectIcons.Back, onClick = onBack)
                trailing()
            }
            if (!stacked) Text(title, Modifier.align(Alignment.Center).padding(horizontal = Sizes.touchTarget + Spacing.sm).semantics { heading() },
                style = MaterialTheme.typography.titleLarge.merge(ProjectHeaderStyle), color = c.text,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (stacked) Text(title, Modifier.fillMaxWidth().padding(top = Spacing.sm).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.merge(ProjectHeaderStyle), color = c.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable internal fun ProjectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ProjectTheme.colors
    Box(modifier.heightIn(min = Sizes.touchTarget).selectable(selected, role = Role.Tab, onClick = onClick), contentAlignment = Alignment.Center) {
        Surface(shape = ProjectShapes.chip, color = if (selected) c.selected else Color.Transparent) {
            Text(text, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), color = if (selected) c.text else c.subText,
                style = MaterialTheme.typography.labelLarge.merge(if (selected) ProjectTabSelectedStyle else ProjectTabStyle))
        }
    }
}
@Composable internal fun ProjectPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ProjectTheme.colors
    Button(onClick, modifier.heightIn(min = ProjectSizes.createHeight), enabled = enabled, shape = ProjectShapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = c.blackButton, contentColor = c.onBlackButton)) {
        Text(text, style = MaterialTheme.typography.labelLarge.merge(ProjectTabSelectedStyle))
    }
}
@Composable internal fun ProjectNotice(text: String) {
    if (text.isNotBlank()) Text(text, Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = ProjectTheme.colors.subText)
}
@Composable internal fun ProjectPopupItem(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val c = ProjectTheme.colors
    DropdownMenuItem(text = { Text(label, style = MaterialTheme.typography.bodyLarge.merge(ProjectTabStyle), color = if (danger) c.danger else c.text) },
        leadingIcon = { Icon(icon, null, Modifier.size(ProjectSizes.icon), tint = if (danger) c.danger else c.text) },
        onClick = onClick, modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics { contentDescription = label })
}

package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.SheetButtonStyle
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
import com.dua.ai.ui.theme.SheetTitleStyle

/** Dabba-style modal sheet: white (gray-900 dark) surface, 28 dp top corners, slim gray handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuaSheet(onDismiss: () -> Unit, sheetState: SheetState = rememberModalBottomSheetState(), content: @Composable ColumnScope.() -> Unit) {
    val colors = SheetTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.container, contentColor = colors.text,
        shape = SheetShapes.sheet, scrimColor = colors.scrim, dragHandle = {
            Box(Modifier.padding(vertical = SheetSizes.handlePadding).size(SheetSizes.handleWidth, SheetSizes.handleHeight)
                .background(colors.handle, SheetShapes.handle))
        }, content = content)
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = SheetTitleStyle, color = SheetTheme.colors.title,
        modifier = modifier.padding(bottom = SheetSizes.titleBottom).semantics { heading() })
}

/** Gray #F3F4F6 rounded group, rows separated by E5E7EB dividers indented past the icon. */
@Composable
fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = SheetShapes.group, color = SheetTheme.colors.fill, contentColor = SheetTheme.colors.text) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun SheetDivider() {
    HorizontalDivider(color = SheetTheme.colors.divider, thickness = SheetSizes.dividerThickness, modifier = Modifier.padding(start = SheetSizes.dividerIndent))
}

/** Purple-on switch with gray track off. */
@Composable
fun sheetSwitchColors() = SheetTheme.colors.let { c ->
    SwitchDefaults.colors(checkedTrackColor = c.accent, checkedBorderColor = c.accent, checkedThumbColor = c.onAccent, checkedIconColor = c.accent,
        uncheckedTrackColor = c.switchTrackOff, uncheckedBorderColor = c.switchTrackOff, uncheckedThumbColor = c.switchThumbOff)
}

@Composable
fun SheetTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false, modifier: Modifier = Modifier) {
    val c = SheetTheme.colors
    TextButton(onClick = onClick, enabled = enabled, shape = SheetShapes.button, modifier = modifier.heightIn(min = SheetSizes.touch),
        colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) c.destructive else c.accent)) {
        Text(text, style = SheetButtonStyle)
    }
}

/** AlertDialog in the sheet palette (rename / delete confirms). */
@Composable
fun DuaDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    text: @Composable (() -> Unit)? = null,
) {
    val c = SheetTheme.colors
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, modifier = modifier,
        properties = properties, title = { Text(title, style = SheetTitleStyle) }, text = text, shape = SheetShapes.dialog,
        containerColor = c.container, titleContentColor = c.title, textContentColor = c.subText, tonalElevation = 0.dp)
}

/** Tappable icon + label row inside a [SheetGroup] (long-press menus, pickers). 56 dp tall, destructive rows in red. */
@Composable
fun SheetActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true,
    destructive: Boolean = false, detail: String = "", trailing: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val c = SheetTheme.colors
    val alpha = if (enabled) 1f else com.dua.ai.ui.theme.SheetDisabledAlpha
    val tint = (if (destructive) c.destructive else c.icon).copy(alpha = alpha)
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin)
        .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
        .padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(SheetSizes.rowIcon))
        Column(Modifier.weight(1f).padding(start = SheetSizes.rowGap)) {
            Text(label, style = com.dua.ai.ui.theme.SheetRowStyle, color = (if (destructive) c.destructive else c.text).copy(alpha = alpha))
            if (detail.isNotEmpty()) Text(detail, style = com.dua.ai.ui.theme.SheetSubStyle, color = c.subText.copy(alpha = alpha))
        }
        trailing?.invoke()
    }
}

/** Primary purple pill button (Save, Record, Stop). */
@Composable
fun SheetFilledButton(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier) {
    val c = SheetTheme.colors
    androidx.compose.material3.Button(onClick = onClick, enabled = enabled, shape = SheetShapes.button,
        modifier = modifier.heightIn(min = SheetSizes.touch),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent,
            disabledContainerColor = c.fill, disabledContentColor = c.subText)) {
        if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(SheetSizes.rowIcon))
        Text(text, style = SheetButtonStyle, modifier = if (icon != null) Modifier.padding(start = SheetSizes.handlePadding / 2) else Modifier)
    }
}

/** Secondary gray-fill pill button (Choose file, Add note, Daily planner). */
@Composable
fun SheetTonalButton(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier) {
    val c = SheetTheme.colors
    androidx.compose.material3.Button(onClick = onClick, enabled = enabled, shape = SheetShapes.button,
        modifier = modifier.heightIn(min = SheetSizes.touch), elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = c.fill, contentColor = c.text,
            disabledContainerColor = c.fill, disabledContentColor = c.subText)) {
        if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(SheetSizes.rowIcon))
        Text(text, style = SheetButtonStyle, modifier = if (icon != null) Modifier.padding(start = SheetSizes.handlePadding / 2) else Modifier)
    }
}


/** Dabba-style popup menu: sheet surface (white / gray-900), group corners, no tonal tint. */
@Composable
fun DuaDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = SheetTheme.colors
    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier,
        shape = SheetShapes.badge, containerColor = c.container, tonalElevation = 0.dp, shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(SheetSizes.dividerThickness, c.divider), content = content)
}

/** Menu row: 16 sp semibold, 22 dp icon, 48 dp min height, accent check/tint when selected. */
@Composable
fun DuaDropdownItem(label: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean = false, modifier: Modifier = Modifier) {
    val c = SheetTheme.colors
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, style = com.dua.ai.ui.theme.SheetRowStyle, color = if (selected) c.accent else c.text) },
        onClick = onClick, modifier = modifier.heightIn(min = SheetSizes.touch),
        leadingIcon = icon?.let { { androidx.compose.material3.Icon(it, contentDescription = null, tint = if (selected) c.accent else c.icon, modifier = Modifier.size(SheetSizes.rowIcon)) } },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = SheetSizes.rowPaddingH))
}

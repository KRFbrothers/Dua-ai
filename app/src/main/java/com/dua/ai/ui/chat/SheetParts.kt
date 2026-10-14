package com.dua.ai.ui.chat

import androidx.compose.foundation.background
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
    TextButton(onClick = onClick, enabled = enabled, shape = SheetShapes.button, modifier = modifier,
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

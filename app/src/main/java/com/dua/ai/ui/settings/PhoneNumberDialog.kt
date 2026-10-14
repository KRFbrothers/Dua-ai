package com.dua.ai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.dua.ai.account.PhoneNumbers
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.components.pageTextFieldColors
import com.dua.ai.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PhoneNumberDialog(initialPhone: String, accountBusy: Boolean, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DuaPageTheme {
            BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Column(Modifier.heightIn(max = maxHeight).clip(SettingsShapes.card).background(PageTheme.colors.card)
                    .verticalScroll(rememberScrollState()).padding(SettingsSizes.rowPadding)) {
                    Text("Phone number", style = SettingsNameStyle, color = SettingsTheme.colors.name,
                        modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Spacing.md))
                    PhoneNumberForm(initialPhone, accountBusy, onDismiss, onSave)
                }
            }
        }
    }
}

/** Reusable native form: keep the editor logic testable separately from Android's dialog window/keyboard. */
@Composable
internal fun PhoneNumberForm(initialPhone: String, accountBusy: Boolean, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    var phone by remember { mutableStateOf(initialPhone) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val normalized = PhoneNumbers.normalize(phone)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Add a contact number with its country code. This does not enable phone sign-in or verify the number. Leave it blank to remove it.", style = SheetBodyStyle)
        OutlinedTextField(phone, { phone = it.take(64); error = null }, label = { Text("Phone number with country code") },
            placeholder = { Text("+91…") }, singleLine = true, enabled = !busy,
            isError = error != null || normalized == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Contact phone number" }, colors = pageTextFieldColors())
        if (normalized == null) Text("Use + and 7–15 digits, including the country code.", style = SheetBodyStyle, color = SettingsTheme.colors.danger)
        error?.let { Text(it, style = SheetBodyStyle, color = SettingsTheme.colors.danger,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SheetTextButton("Cancel", onClick = onDismiss)
            SheetTextButton(if (busy) "Saving…" else "Save", enabled = !busy && !accountBusy && normalized != null && normalized != initialPhone,
                onClick = {
                    val value = normalized ?: return@SheetTextButton
                    busy = true; error = null
                    scope.launch {
                        try { onSave(value); onDismiss() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "Phone number could not be saved. Retry." }
                        finally { busy = false }
                    }
                })
        }
    }
}

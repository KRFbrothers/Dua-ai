package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import com.dua.ai.ondevice.RewriteStyle
import com.dua.ai.ondevice.WritingSession
import com.dua.ai.ondevice.WritingTask
import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetRowStyle
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
import com.dua.ai.ui.theme.Spacing

/** Callbacks for the Summarize / Rewrite sheet. */
class WritingActions(
    val onStyle: (RewriteStyle) -> Unit = {},
    val onUse: () -> Unit = {},
    val onAskServer: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WritingToolsSheet(session: WritingSession, actions: WritingActions) {
    DuaSheet(onDismiss = actions.onDismiss) { WritingToolsContent(session, actions) }
}

/** Summary or rewrite made on the phone, shown before anything changes. Text label "On-device" only; no brand art. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingToolsContent(session: WritingSession, actions: WritingActions, modifier: Modifier = Modifier) {
    val c = SheetTheme.colors
    val clipboard = LocalClipboardManager.current
    val summarize = session.task == WritingTask.Summarize
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = SheetSizes.paddingH)
        .padding(bottom = SheetSizes.paddingBottom).testTag("writing-tools-sheet"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SheetTitle(if (summarize) "Summarize" else "Rewrite")
        Text("On-device · " + if (session.fromDraft) "your message" else "this reply", style = SheetNoteStyle, color = c.subText)
        when (session.phase) {
            WritingSession.Phase.Choose -> SheetGroup {
                RewriteStyle.entries.forEachIndexed { index, style ->
                    if (index > 0) SheetDivider()
                    SheetActionRow(style.label, Icons.Outlined.EditNote, onClick = { actions.onStyle(style) },
                        modifier = Modifier.testTag("rewrite-${style.name}"))
                }
            }
            WritingSession.Phase.Working -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("writing-progress"), color = c.accent, trackColor = c.divider)
                Text(if (summarize) "Summarizing on this phone…" else "Rewriting on this phone…", style = SheetNoteStyle, color = c.subText,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            WritingSession.Phase.Result -> SheetGroup {
                SelectionContainer {
                    Text(session.result, style = SheetRowStyle, color = c.text, modifier = Modifier.padding(SheetSizes.rowPaddingH).testTag("writing-result"))
                }
            }
            WritingSession.Phase.Unavailable, WritingSession.Phase.Failed -> Text(
                if (session.phase == WritingSession.Phase.Unavailable) "On-device AI is off or not available on this phone. Dua's normal chat can do it instead: the request goes into the message box and nothing is sent until you tap Send."
                else "It didn't work on this phone right now. Dua's normal chat can do it instead: the request goes into the message box and nothing is sent until you tap Send.",
                style = SheetNoteStyle, color = c.subText, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, androidx.compose.ui.Alignment.End)) {
            SheetTextButton("Close", onClick = actions.onDismiss)
            when (session.phase) {
                WritingSession.Phase.Result -> {
                    SheetTextButton("Copy", onClick = { clipboard.setText(AnnotatedString(session.result)) })
                    SheetTextButton(if (session.fromDraft) "Replace message" else "Use in message box", onClick = actions.onUse)
                }
                WritingSession.Phase.Unavailable, WritingSession.Phase.Failed -> SheetTextButton("Ask Dua instead", onClick = actions.onAskServer)
                else -> Unit
            }
        }
    }
}

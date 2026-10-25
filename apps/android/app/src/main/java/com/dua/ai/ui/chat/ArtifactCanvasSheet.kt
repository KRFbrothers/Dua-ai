package com.dua.ai.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.model.ChatArtifact
import com.dua.ai.ui.theme.CodeTextStyle
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.KrfBrothersCodeFont
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.launch

/** A transient viewer of the exact message source. Preview never executes generated UI code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtifactCanvasSheet(artifacts: List<ChatArtifact>, onDismiss: () -> Unit) {
    if (artifacts.isEmpty()) return
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val artifact = artifacts[selected.coerceIn(artifacts.indices)]
    val kind = codePreviewKind(artifact.language)
    var preview by rememberSaveable(artifact.id) { mutableStateOf(kind != null) }
    val textActions = rememberTextMessageActions(artifact.code, shareTitle = "Share artifact")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    DuaSheet(onDismiss, sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).testTag("artifact-canvas-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Outlined.Close, "Close canvas")
                }
                Text("Canvas", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = textActions.copy, modifier = Modifier.size(Sizes.touchTarget).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Icon(if (textActions.copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        if (textActions.copied) "Artifact copied" else "Copy artifact source")
                }
                IconButton(onClick = { textActions.share() }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Outlined.Share, "Share artifact")
                }
            }
            if (artifacts.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                artifacts.forEachIndexed { index, item ->
                    FilterChip(selected = index == selected, onClick = { selected = index },
                        label = { Text("${item.language.uppercase()} ${index + 1}", style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag("canvas-artifact-$index"))
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                SegmentedButton(selected = preview, onClick = { preview = true }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag("canvas-preview-tab")) { Text("Preview") }
                SegmentedButton(selected = !preview, onClick = { preview = false }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag("canvas-code-tab")) { Text("Code") }
            }
            textActions.shareError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.lg).semantics { liveRegion = LiveRegionMode.Polite }) }
            HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (preview && kind != null) key(artifact.id) {
                    CodePreview(artifact.code, kind, Modifier.fillMaxSize(), fillViewport = true)
                } else if (preview) {
                    Text("${artifact.language.uppercase()} needs its own UI runtime. Use Code to view or copy the source; it is not executed on your phone.",
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.Center).padding(Spacing.xxl)
                            .testTag("canvas-runtime-required"))
                } else ArtifactSource(artifact)
            }
        }
    }
}

@Composable
private fun ArtifactSource(artifact: ChatArtifact) {
    val colors = DuaTheme.colors
    val text = rememberHighlightedCode(artifact.code, artifact.language, colors)
    SelectionContainer {
        Text(text, style = CodeTextStyle.copy(fontFamily = KrfBrothersCodeFont), color = colors.onCode, softWrap = false,
            modifier = Modifier.fillMaxSize().background(colors.codeBackground).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                .padding(Spacing.lg).testTag("canvas-source"))
    }
}

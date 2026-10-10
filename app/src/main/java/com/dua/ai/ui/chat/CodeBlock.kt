package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.CodeTextStyle
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.delay

private const val MAX_HIGHLIGHT_CHARS = 20_000

internal fun highlightCode(code: String, language: String?, dark: Boolean): AnnotatedString {
    // Unified diffs (Build mode answers): added lines green, removed red, hunk headers blue.
    if (language?.trim()?.lowercase() in setOf("diff", "patch")) return highlightDiff(code, dark)
    val syntax = language?.trim()?.lowercase()?.let { name ->
        SyntaxLanguage.getByName(when (name) { "kt", "kts" -> "kotlin"; "js", "jsx", "mjs" -> "javascript"; "ts", "tsx" -> "typescript"; "rb" -> "ruby"; "rs" -> "rust"; "py" -> "python"; "sh", "bash", "zsh", "shell" -> "shell"; "c++" -> "cpp"; "cs", "c#" -> "csharp"; else -> name })
    }
    if (syntax == null || code.length > MAX_HIGHLIGHT_CHARS) return AnnotatedString(code)
    return runCatching {
        val result = Highlights.Builder().code(code).language(syntax).theme(SyntaxThemes.atom(dark)).build()
        buildAnnotatedString {
            append(code)
            result.getHighlights().forEach { highlight ->
                val start = highlight.location.start.coerceIn(0, code.length)
                val end = highlight.location.end.coerceIn(start, code.length)
                when (highlight) {
                    is ColorHighlight -> addStyle(SpanStyle(color = Color(highlight.rgb).copy(alpha = 1f)), start, end)
                    is BoldHighlight -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
            }
        }
    }.getOrElse { AnnotatedString(code) }
}

/** Line-based unified diff colouring; long diffs stay plain like other code. */
internal fun highlightDiff(code: String, dark: Boolean): AnnotatedString {
    if (code.length > MAX_HIGHLIGHT_CHARS) return AnnotatedString(code)
    val added = if (dark) Color(0xFF7EE787) else Color(0xFF116329)
    val addedBg = if (dark) Color(0x332EA043) else Color(0x262DA44E)
    val removed = if (dark) Color(0xFFFF7B72) else Color(0xFF82071E)
    val removedBg = if (dark) Color(0x33F85149) else Color(0x26FF8182)
    val hunk = if (dark) Color(0xFF79C0FF) else Color(0xFF0550AE)
    return buildAnnotatedString {
        append(code)
        var start = 0
        for (line in code.split('\n')) {
            val end = start + line.length
            when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                line.startsWith("@@") -> addStyle(SpanStyle(color = hunk), start, end)
                line.startsWith("+") -> addStyle(SpanStyle(color = added, background = addedBg), start, end)
                line.startsWith("-") -> addStyle(SpanStyle(color = removed, background = removedBg), start, end)
            }
            start = end + 1
        }
    }
}

/** Fenced code: language label, copy button, monospace text that scrolls sideways instead of wrapping. */
@Composable
fun CodeBlock(code: String, language: String?, modifier: Modifier = Modifier) {
    val colors = DuaTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    val text = rememberHighlightedCode(code.trimEnd('\n'), language, colors.isDark)
    Surface(shape = DuaShape.codeBlock, color = colors.codeBackground, border = BorderStroke(1.dp, colors.codeBorder),
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Column {
            Row(Modifier.fillMaxWidth().background(colors.codeHeader).padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(language?.trim()?.takeIf { it.isNotEmpty() } ?: "code", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }) {
                    Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) "Code copied" else "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconTiny + 2.dp))
                    Text(if (copied) "Copied" else "Copy", modifier = Modifier.padding(start = Spacing.xs), style = MaterialTheme.typography.labelMedium)
                }
            }
            SelectionContainer {
                Text(text, style = CodeTextStyle, color = colors.onCode, softWrap = false,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(Spacing.md))
            }
        }
    }
}

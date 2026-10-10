package com.dua.ai.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.ui.theme.DuaTheme
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding

/** Opens only safe HTTPS links (same rule as the plain-text renderer it replaces). */
internal class SafeUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        val safe = IntegrationCapabilities.safeUrl(uri) ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "A browser is unavailable on this device.", Toast.LENGTH_SHORT).show() }
    }
}

/**
 * Renders an assistant reply as GitHub-flavoured markdown: headings, lists, emphasis,
 * links, inline code, tables and fenced code blocks (see [CodeBlock]). Images are not
 * loaded, and links open only after passing [IntegrationCapabilities.safeUrl].
 */
@Composable
fun MarkdownMessage(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = remember(context) { SafeUriHandler(context) }
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val colors = DuaTheme.colors
    val body = type.bodyLarge.copy(lineHeight = type.bodyLarge.fontSize * 1.55f)
    val inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = body.fontSize * 0.9f)
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Markdown(
            content = remember(markdown) { com.dua.ai.model.CitationMarkers.escape(markdown) },
            modifier = modifier.fillMaxWidth(),
            colors = markdownColor(
                text = scheme.onSurface,
                codeText = colors.onCode,
                inlineCodeText = colors.onCode,
                linkText = scheme.primary,
                codeBackground = colors.codeBackground,
                inlineCodeBackground = colors.codeHeader,
                dividerColor = scheme.outlineVariant,
                tableText = scheme.onSurface,
                tableBackground = colors.codeBackground,
            ),
            typography = markdownTypography(
                h1 = type.headlineSmall, h2 = type.titleLarge, h3 = type.titleMedium,
                h4 = type.titleSmall.copy(fontWeight = FontWeight.SemiBold), h5 = type.titleSmall, h6 = type.titleSmall,
                text = body, paragraph = body, ordered = body, bullet = body, list = body,
                code = com.dua.ai.ui.theme.CodeTextStyle, inlineCode = inlineCode,
                quote = body.copy(fontStyle = FontStyle.Italic, color = scheme.onSurfaceVariant),
                link = body.copy(color = scheme.primary, textDecoration = TextDecoration.Underline),
            ),
            padding = markdownPadding(block = 8.dp, list = 4.dp, listItemBottom = 6.dp, indentList = 12.dp),
            dimens = markdownDimens(codeBackgroundCornerSize = 6.dp, tableCellWidth = 140.dp, tableCellPadding = 12.dp, tableCornerSize = 10.dp),
            components = markdownComponents(
                codeFence = { model -> MarkdownCodeFence(model.content, model.node) { code, language -> CodeBlock(code, language) } },
                codeBlock = { model -> MarkdownCodeBlock(model.content, model.node) { code, language -> CodeBlock(code, language) } },
            ),
        )
    }
}

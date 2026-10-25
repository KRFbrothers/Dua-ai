package com.dua.ai.ui.chat

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.dua.ai.ui.theme.CodePreviewSizes
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Spacing
import java.io.ByteArrayInputStream
import org.json.JSONObject

internal enum class CodePreviewKind { Html, Svg, Markdown, Mermaid }
internal fun codePreviewKind(language: String?): CodePreviewKind? = when (language?.trim()?.lowercase()) {
    "html", "htm" -> CodePreviewKind.Html
    "svg" -> CodePreviewKind.Svg
    "md", "markdown" -> CodePreviewKind.Markdown
    "mermaid", "mmd" -> CodePreviewKind.Mermaid
    else -> null
}

private const val PREVIEW_ORIGIN = "https://dua-preview.invalid/"
internal const val MERMAID_URL = "${PREVIEW_ORIGIN}mermaid.js"
internal const val MAX_PREVIEW_CHARS = 100_000

/** Only the pinned, bundled renderer can run scripts; generated HTML/SVG is always script-free. */
internal fun codePreviewDocument(code: String, kind: CodePreviewKind, dark: Boolean, foreground: Int, background: Int): String {
    fun css(rgb: Int) = "#%06x".format(rgb and 0xffffff)
    val head = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-dua-preview' $PREVIEW_ORIGIN; style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'"><style>html,body{margin:0;padding:8px;color:${css(foreground)};background:${css(background)};font-family:system-ui;overflow-wrap:anywhere}svg{max-width:100%;height:auto}</style></head><body>"""
    val content = if (kind == CodePreviewKind.Mermaid) {
        // JSON and HTML are separate contexts: '<' must never terminate this trusted script element.
        val quoted = JSONObject.quote(code).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
        """<div id="diagram" role="img" aria-label="Mermaid diagram"></div><script nonce="dua-preview" src="$MERMAID_URL"></script><script nonce="dua-preview">mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'${if (dark) "dark" else "default"}',maxTextSize:$MAX_PREVIEW_CHARS,maxEdges:500});mermaid.render('dua-diagram',$quoted).then(function(result){document.getElementById('diagram').innerHTML=result.svg;}).catch(function(){document.body.textContent='Diagram could not be rendered. Check the Mermaid syntax.';});</script>"""
    } else code
    return head + content + "</body></html>"
}

@SuppressLint("SetJavaScriptEnabled") // Only the bundled Mermaid renderer runs JS; arbitrary HTML/SVG never does.
internal fun configureCodePreview(view: WebView, mermaid: Boolean) {
    with(view.settings) {
        javaScriptEnabled = mermaid
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = false
        databaseEnabled = false
        blockNetworkLoads = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
    }
    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            if (mermaid && request.url.toString() == MERMAID_URL && request.method == "GET")
                return WebResourceResponse("application/javascript", "UTF-8", view.context.assets.open("vendor/mermaid/mermaid.min.js"))
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
        }
    }
}

/** End pending loads/scripts and release all renderer callbacks on tab switch or dismissal. */
internal fun releaseCodePreview(view: WebView) {
    view.stopLoading()
    view.settings.javaScriptEnabled = false
    view.loadUrl("about:blank")
    view.clearHistory()
    view.removeAllViews()
    (view.parent as? ViewGroup)?.removeView(view)
    view.webViewClient = WebViewClient()
    view.webChromeClient = null
    view.destroy()
}

/** Inline artifact canvas; no app credentials, network, file access, or JavaScript bridge. */
@Composable
internal fun CodePreview(code: String, kind: CodePreviewKind, modifier: Modifier = Modifier, fillViewport: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val colors = DuaTheme.colors
    Column(modifier.fillMaxWidth().testTag("code-preview")) {
        Text("Preview · local only", style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.md))
        if (code.length > MAX_PREVIEW_CHARS) {
            Text("This preview is too large. Copy or share the source instead.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.md))
        } else if (kind == CodePreviewKind.Markdown) {
            // Reuse the native Markdown renderer and its safe-link policy.
            Box(if (fillViewport) Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth()) {
                MarkdownMessage(code, Modifier.padding(Spacing.md))
            }
        } else {
            val document = remember(code, kind, colors.isDark, scheme.onSurface, scheme.surface) {
                codePreviewDocument(code, kind, colors.isDark, scheme.onSurface.toArgb(), scheme.surface.toArgb())
            }
            key(kind) {
                AndroidView(factory = { context -> WebView(context).also { configureCodePreview(it, kind == CodePreviewKind.Mermaid) } },
                    modifier = (if (fillViewport) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().height(CodePreviewSizes.height))
                        .testTag("artifact-web-preview"),
                    update = { view ->
                        if (view.tag != document) { view.tag = document; view.setBackgroundColor(scheme.surface.toArgb()); view.loadDataWithBaseURL(PREVIEW_ORIGIN, document, "text/html", "UTF-8", null) }
                    },
                    onRelease = ::releaseCodePreview)
            }
        }
    }
}

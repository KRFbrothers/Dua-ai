package com.dua.ai.ui.chat

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodePreviewTest {
    @Test fun supportedArtifactAliasesDoNotConfuseExecutableLanguages() {
        assertEquals(CodePreviewKind.Html, codePreviewKind(" HTML "))
        assertEquals(CodePreviewKind.Svg, codePreviewKind("svg"))
        assertEquals(CodePreviewKind.Markdown, codePreviewKind("md"))
        assertEquals(CodePreviewKind.Mermaid, codePreviewKind("mermaid"))
        assertNull(codePreviewKind("javascript"))
        assertNull(codePreviewKind(null))
    }

    @Test fun hostileDiagramCannotEscapeItsJsonScriptContext() {
        val doc = codePreviewDocument("</script><script>alert('x')</script>&<img src=x>", CodePreviewKind.Mermaid, true, -1, 0)
        assertFalse(doc.contains("</script><script>alert"))
        assertTrue(doc.contains("\\u003c"))
        assertTrue(doc.contains("\\u003e"))
        assertEquals(2, Regex("</script>").findAll(doc).count())
        assertTrue(doc.contains("securityLevel:'strict'"))
        assertTrue(doc.contains("connect-src 'none'"))
        assertTrue(doc.contains(MERMAID_URL))
    }

    @Test fun htmlAndSvgCannotRunScriptsOrAccessFilesNetworkOrAppBridges() {
        val view = WebView(RuntimeEnvironment.getApplication())
        try {
            configureCodePreview(view, mermaid = false)
            assertFalse(view.settings.javaScriptEnabled)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertFalse(view.settings.domStorageEnabled)
            assertTrue(view.settings.blockNetworkLoads)
            val blocked = view.webViewClient.shouldInterceptRequest(view, request("https://example.com/private"))!!
            assertEquals(-1, blocked.data.read())
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request("intent://x")))
        } finally { view.destroy() }
    }

    @Test fun mermaidCanLoadOnlyThePinnedLocalBundle() {
        val view = WebView(RuntimeEnvironment.getApplication())
        try {
            configureCodePreview(view, mermaid = true)
            assertTrue(view.settings.javaScriptEnabled)
            assertTrue(view.settings.blockNetworkLoads)
            val local = view.webViewClient.shouldInterceptRequest(view, request(MERMAID_URL))!!
            local.data.use { assertTrue(it.read() >= 0) }
            val other = view.webViewClient.shouldInterceptRequest(view, request(MERMAID_URL + "?redirect=x"))!!
            other.data.use { assertEquals(-1, it.read()) }
        } finally { view.destroy() }
    }

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
}

package com.dua.ai.model

import org.junit.Assert.*
import org.junit.Test

class MessageArtifactsTest {
    @Test fun findsAllSupportedFencesInSourceOrderWithoutDroppingBlankLines() {
        val source = "```HTML index.html\n<div>Hello</div>\n\n```\n~~~svg\n<svg/>\n~~~\n```mermaid\ngraph TD; A-->B\n```\n```md\n# Title\n```"
        val artifacts = MessageArtifacts.find(source)
        assertEquals(listOf("html", "svg", "mermaid", "md"), artifacts.map { it.language })
        assertEquals("<div>Hello</div>\n\n", artifacts.first().code)
        assertEquals(4, artifacts.map { it.id }.distinct().size)
    }

    @Test fun ignoresUnfinishedMismatchedAndPlainCodeBlocksButKeepsLibraryDefaults() {
        assertTrue(MessageArtifacts.find("```html\n<div>Still streaming").isEmpty())
        assertTrue(MessageArtifacts.find("````svg\n<svg/>\n```").isEmpty())
        assertTrue(MessageArtifacts.find("```python\nprint(1)\nprint(2)\n```").isEmpty())
        assertEquals(listOf("python" to "print(1)"), CodeFences.parse("```python\nprint(1)"))
        val closed = MessageArtifacts.find("````html\n<div>``` is text</div>\n````")
        assertEquals(1, closed.size)
    }

    @Test fun recognizesOnlyMultilineUiCodeWithoutPretendingToCompileIt() {
        assertEquals("jsx", MessageArtifacts.find("```jsx\nconst App = () => (\n<div>Hello</div>\n);\n```").single().language)
        assertEquals("kotlin", MessageArtifacts.find("```kotlin\n@Composable\nfun Card() { Text(\"Hi\") }\n```").single().language)
        assertTrue(MessageArtifacts.find("```kotlin\nval x = 1\nval y = 2\n```").isEmpty())
        assertTrue(MessageArtifacts.find("```javascript\nlet x = 1;\nx++;\n```").isEmpty())
        assertTrue(MessageArtifacts.find("```jsx\n<div>Hello</div>\n```").isEmpty())
    }

    @Test fun recognizesRawMarkupAndMarkdownTablesButNotOrdinaryProse() {
        assertEquals("svg", MessageArtifacts.find("<svg><path d=\"M0 0\"/></svg>").single().language)
        assertEquals("html", MessageArtifacts.find("<!doctype html>\n<html><body>Hello</body></html>").single().language)
        assertEquals("markdown", MessageArtifacts.find("| Task | State |\n| --- | :---: |\n| Build | Done |").single().language)
        assertTrue(MessageArtifacts.find("Hello, how can I help?").isEmpty())
        assertTrue(MessageArtifacts.find("A | sentence\n---").isEmpty())
        assertTrue(MessageArtifacts.find("<div>Not finished").isEmpty())
        assertTrue(MessageArtifacts.find("```\nconst value = 1\n```").isEmpty())
        assertEquals("html", MessageArtifacts.find("```\n<div>Hello</div>\n```").single().language)
    }

    @Test fun boundsDetectionWithoutMutatingTheMessageOrCreatingDemoArtifacts() {
        assertTrue(MessageArtifacts.find("").isEmpty())
        assertTrue(MessageArtifacts.find("x".repeat(500_001)).isEmpty())
        val source = (1..20).joinToString("\n") { "```html\n<div>$it</div>\n```" }
        assertEquals(MessageArtifacts.MAX_ARTIFACTS, MessageArtifacts.find(source).size)
        assertTrue(source.contains("<div>20</div>"))
    }
}

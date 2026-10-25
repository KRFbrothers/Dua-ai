package com.dua.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CitationMarkersTest {
    @Test fun escapesBareMarkersOnly() {
        assertEquals("""Rain on Saturday \[1\]\[2\], and \[3\].""", CitationMarkers.escape("Rain on Saturday [1][2], and [3]."))
        assertEquals("""See \[1\] \[2\]""", CitationMarkers.escape("See [1] [2]"))
        assertEquals("A [link](https://a.example) and [1](https://b.example)", CitationMarkers.escape("A [link](https://a.example) and [1](https://b.example)"))
        assertEquals("[docs][1]", CitationMarkers.escape("[docs][1]"))
        assertEquals("[1]: https://a.example", CitationMarkers.escape("[1]: https://a.example"))
        assertEquals("`arr[1]` and xs[2]", CitationMarkers.escape("`arr[1]` and xs[2]"))
        assertEquals("```\nval a = b [1]\n```\nok \\[1\\]", CitationMarkers.escape("```\nval a = b [1]\n```\nok [1]"))
        assertEquals("""already \[1\]""", CitationMarkers.escape("""already \[1\]"""))
    }
}

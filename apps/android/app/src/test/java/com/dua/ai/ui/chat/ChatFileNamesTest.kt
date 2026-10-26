package com.dua.ai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatFileNamesTest {
    @Test fun ordinaryTextIsPreservedIncludingEmptyInput() {
        for (text in listOf("", "Hello", "Line one\nLine two", "  Keep spaces  ")) {
            assertEquals(text to emptyList<String>(), splitFileNames(text))
        }
    }

    @Test fun extractsAppendedNamesWithoutChangingTheMessageBody() {
        assertEquals("Question\nwith details" to listOf("a.pdf", "b.png"),
            splitFileNames("Question\nwith details\nFiles: a.pdf, b.png"))
        assertEquals("" to listOf("photo.png"), splitFileNames("\nFiles: photo.png"))
    }

    @Test fun lastMarkerOwnsTheSuffixAndEarlierMarkerStaysInBody() {
        assertEquals("Question\nFiles: earlier.pdf\nMore details" to listOf("latest.pdf"),
            splitFileNames("Question\nFiles: earlier.pdf\nMore details\nFiles: latest.pdf"))
    }

    @Test fun onlyTheExactNewlineAndCaseSensitiveMarkerIsRecognized() {
        for (text in listOf("Files: a.pdf", "Question\nfiles: a.pdf", "Question\nFiles:a.pdf")) {
            assertEquals(text to emptyList<String>(), splitFileNames(text))
        }
    }

    @Test fun emptyAndWhitespaceOnlyNamesAreOmitted() {
        assertEquals("Question" to emptyList<String>(), splitFileNames("Question\nFiles: "))
        assertEquals("Question" to listOf("report.pdf"), splitFileNames("Question\nFiles: ,  , report.pdf, "))
    }

    @Test fun separatorIsCommaSpaceAndNamesAreNotTrimmedOrDeduplicated() {
        assertEquals("Question" to listOf("a.pdf,b.png"), splitFileNames("Question\nFiles: a.pdf,b.png"))
        assertEquals("Question" to listOf("a.pdf", " b.png", "a.pdf"),
            splitFileNames("Question\nFiles: a.pdf,  b.png, a.pdf"))
    }

    @Test fun parserRetainsAllNamesRatherThanApplyingTheBubbleDisplayLimit() {
        assertEquals("Question" to listOf("a", "b", "c", "d"), splitFileNames("Question\nFiles: a, b, c, d"))
    }
}

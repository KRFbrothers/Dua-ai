package com.dua.ai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeReplyTest {
    @Test fun hapticFiresOnceWhenThresholdIsCrossed() {
        val s = SwipeReplyState(thresholdPx = 100f)
        assertFalse(s.drag(60f))
        assertFalse(s.armed)
        assertTrue(s.drag(50f))
        assertTrue(s.armed)
        assertFalse(s.drag(30f))
    }

    @Test fun releaseBelowThresholdDoesNotReply() {
        val s = SwipeReplyState(100f)
        s.drag(99f)
        assertFalse(s.release())
        assertEquals(0f, s.offset, 0f)
    }

    @Test fun releaseAfterThresholdRepliesAndResets() {
        val s = SwipeReplyState(100f)
        s.drag(120f)
        assertTrue(s.release())
        assertFalse(s.armed)
        assertEquals(0f, s.offset, 0f)
    }

    @Test fun draggingBackBelowThresholdDisarmsAndCanRearm() {
        val s = SwipeReplyState(100f)
        assertTrue(s.drag(110f))
        s.drag(-40f)
        assertFalse(s.armed)
        assertTrue(s.drag(40f))
    }

    @Test fun offsetIsClampedLikeTelegram() {
        // Telegram: dx = max(-dp(80), min(0, dx)); threshold dp(50). Mirrored to the right.
        val s = SwipeReplyState(50f)
        s.drag(-50f)
        assertEquals(0f, s.offset, 0f)
        s.drag(1000f)
        assertEquals(80f, s.offset, 0f)
    }

    @Test fun replyButtonProgressMatchesDrawReplyButton() {
        val s = SwipeReplyState(50f)
        s.drag(20f); assertEquals(0f, s.progress, 0f)
        s.drag(15f); assertEquals(0.5f, s.progress, 0.001f)
        s.drag(15f); assertEquals(1f, s.progress, 0f)
    }

    @Test fun trackingStartsAfterPointFourCmMostlyHorizontal() {
        assertFalse(SwipeReplyState.shouldStart(20f, 0f, startPx = 25f))
        assertTrue(SwipeReplyState.shouldStart(30f, 9f, startPx = 25f))
        assertFalse(SwipeReplyState.shouldStart(30f, 11f, startPx = 25f))
        assertFalse(SwipeReplyState.shouldStart(-30f, 0f, startPx = 25f))
    }

    @Test fun telegramChatAnimatorCurveAndPopupTimings() {
        val e = TelegramChatAnimator.DEFAULT_INTERPOLATOR
        assertEquals(0f, e.transform(0f), 0.001f)
        assertEquals(1f, e.transform(1f), 0.001f)
        assertTrue(e.transform(0.5f) > 0.5f)
        assertEquals(0.5f, TelegramCubicBezier(0.25f, 0.25f, 0.75f, 0.75f).transform(0.5f), 0.01f)
        assertEquals(230, TelegramPopupTimings.openMillis(5))
        assertTrue(TelegramPopupTimings.childStarted(48f, 30f, 24f))
        assertFalse(TelegramPopupTimings.childStarted(96f, 60f, 24f))
    }

    @Test fun quoteIsSentAsBlockquoteAndSplitBackForDisplay() {
        val q = ReplyQuote.of("1", "Line one\nLine two\nFiles: a.pdf", fromUser = false)
        assertEquals("Line one\nLine two", q.text)
        val sent = ReplyQuote.compose(q, "Explain more")
        assertEquals("> Line one\n> Line two\n\nExplain more", sent)
        val (quote, body) = ReplyQuote.split(sent)
        assertEquals("Line one\nLine two", quote)
        assertEquals("Explain more", body)
    }

    @Test fun noQuoteLeavesTextUntouched() {
        assertEquals("hi", ReplyQuote.compose(null, "hi"))
        assertEquals("hi", ReplyQuote.compose(ReplyQuote("1", "  ", true), "hi"))
        val (quote, body) = ReplyQuote.split("> just a quote with no body")
        assertNull(quote); assertEquals("> just a quote with no body", body)
    }

    @Test fun longQuotesAreClipped() {
        val sent = ReplyQuote.compose(ReplyQuote("1", "x".repeat(2000), false), "ok")
        assertTrue(sent.length < ReplyQuote.MAX_QUOTE + 20)
    }
}

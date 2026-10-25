package com.dua.ai.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveBackTest {
    @Test fun scaleFollowsProgressAndIsClamped() {
        assertEquals(1f, predictiveBackScale(0f), 0.0001f)
        assertEquals(0.96f, predictiveBackScale(0.5f), 0.0001f)
        assertEquals(0.92f, predictiveBackScale(1f), 0.0001f)
        assertEquals(0.92f, predictiveBackScale(3f), 0.0001f)
        assertEquals(1f, predictiveBackScale(-1f), 0.0001f)
    }
}

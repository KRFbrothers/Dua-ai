package com.dua.ai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingTest {
    @Test fun usesFirstProfileNameWithWhitespaceAndUnicode() {
        assertEquals("Ask away, Fareed!", homeGreeting("  Fareed   Ansari  "))
        assertEquals("Ask away, فرید!", homeGreeting("فرید انصاری"))
        assertEquals("Ask away, Amina!", homeGreeting("Amina\nKhan"))
    }

    @Test fun guestsAndEmailFallbacksDoNotExposeAnAddressOrSampleName() {
        for (profile in listOf("", "  \n  ", "owner@example.com")) assertEquals("Ask away!", homeGreeting(profile))
    }
}

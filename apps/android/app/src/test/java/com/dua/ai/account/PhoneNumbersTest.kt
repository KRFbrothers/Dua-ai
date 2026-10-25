package com.dua.ai.account

import org.junit.Assert.*
import org.junit.Test

class PhoneNumbersTest {
    @Test fun normalizesCountryCodeAndFormatsAndAllowsRemoval() {
        assertEquals("+919876543210", PhoneNumbers.normalize("+91 (98765) 43210"))
        assertEquals("", PhoneNumbers.normalize(""))
        assertEquals("", PhoneNumbers.normalize("   "))
    }
    @Test fun rejectsMissingCountryCodeInvalidCharactersAndOversizeNumbers() {
        for (value in listOf("9876543210", "+0", "+123456", "+1234567890123456", "+91\n9876543210", "phone", "+".repeat(65)))
            assertNull(value, PhoneNumbers.normalize(value))
    }
}

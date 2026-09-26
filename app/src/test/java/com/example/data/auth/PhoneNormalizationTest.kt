package com.example.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizationTest {
    @Test
    fun `south african numbers are normalised to E164`() {
        assertEquals("+27821234567", normalizeSouthAfricanPhone("082 123 4567"))
        assertEquals("+27821234567", normalizeSouthAfricanPhone("27821234567"))
        assertEquals("+27821234567", normalizeSouthAfricanPhone("+27 (82) 123-4567"))
    }
}

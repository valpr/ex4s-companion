package com.valpr.bikecompanion.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacValidatorTest {

    @Test
    fun validMac_passes() {
        assertTrue(MacValidator.isValid("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun lowercaseAndWhitespace_normalized() {
        val result = MacValidator.validate("  aa:bb:cc:dd:ee:ff  ")
        assertTrue(result.isSuccess)
        assertEquals("AA:BB:CC:DD:EE:FF", result.getOrThrow())
    }

    @Test
    fun invalidMac_rejected() {
        assertFalse(MacValidator.isValid("ZZ:BB:CC:DD:EE:FF"))
        assertFalse(MacValidator.isValid("AA:BB:CC"))
        assertFalse(MacValidator.isValid(""))
        assertFalse(MacValidator.isValid("AA-BB-CC-DD-EE-FF"))
        assertTrue(MacValidator.validate("not-a-mac").isFailure)
    }
}

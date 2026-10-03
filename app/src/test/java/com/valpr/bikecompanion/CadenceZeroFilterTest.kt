package com.valpr.bikecompanion

import com.valpr.bikecompanion.ble.CadenceZeroFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lone 0-RPM frame must hold last good values; only sustained zeros
 * register as a genuine stop.
 */
class CadenceZeroFilterTest {

    @Test
    fun nonZeroResetsStreak() {
        assertEquals(0, CadenceZeroFilter.nextZeroStreak(0, 85))
        assertEquals(0, CadenceZeroFilter.nextZeroStreak(1, 85))
    }

    @Test
    fun zeroIncrementsStreak() {
        assertEquals(1, CadenceZeroFilter.nextZeroStreak(0, 0))
        assertEquals(2, CadenceZeroFilter.nextZeroStreak(1, 0))
        assertEquals(3, CadenceZeroFilter.nextZeroStreak(2, 0))
    }

    @Test
    fun firstZeroOfStreakIsHeld() {
        assertFalse(CadenceZeroFilter.shouldAcceptZero(0))
        assertFalse(CadenceZeroFilter.shouldAcceptZero(1))
    }

    @Test
    fun sustainedZerosAreAccepted() {
        assertTrue(CadenceZeroFilter.shouldAcceptZero(2))
        assertTrue(CadenceZeroFilter.shouldAcceptZero(5))
    }

    @Test
    fun dropoutThenRecovery_neverAccepts() {
        var streak = 0
        streak = CadenceZeroFilter.nextZeroStreak(streak, 85)
        streak = CadenceZeroFilter.nextZeroStreak(streak, 0)
        assertFalse(CadenceZeroFilter.shouldAcceptZero(streak))
        streak = CadenceZeroFilter.nextZeroStreak(streak, 86)
        assertEquals(0, streak)
    }

    @Test
    fun genuineStop_acceptsOnSecondConsecutiveZero() {
        var streak = 0
        streak = CadenceZeroFilter.nextZeroStreak(streak, 0)
        assertFalse(CadenceZeroFilter.shouldAcceptZero(streak))
        streak = CadenceZeroFilter.nextZeroStreak(streak, 0)
        assertTrue(CadenceZeroFilter.shouldAcceptZero(streak))
    }
}

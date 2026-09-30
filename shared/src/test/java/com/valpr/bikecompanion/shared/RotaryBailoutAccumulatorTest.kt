package com.valpr.bikecompanion.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotaryBailoutAccumulatorTest {

    @Test
    fun backwardFlick_firesAtThreshold() {
        val acc = RotaryBailoutAccumulator()
        assertFalse(acc.onScroll(-20f))
        assertTrue(acc.onScroll(-20f))
    }

    @Test
    fun forwardScroll_neverFiresAndResets() {
        val acc = RotaryBailoutAccumulator()
        assertFalse(acc.onScroll(-20f))
        assertFalse(acc.onScroll(10f)) // forward decays
        // Prior -20 was cancelled by +10; need full -40 again
        assertFalse(acc.onScroll(-20f))
        assertFalse(acc.onScroll(-10f))
        assertTrue(acc.onScroll(-10f))
    }

    @Test
    fun smallBackwardDoesNotFire() {
        val acc = RotaryBailoutAccumulator()
        assertFalse(acc.onScroll(-10f))
        assertFalse(acc.onScroll(-10f))
        assertFalse(acc.onScroll(-10f))
    }
}

package com.valpr.bikecompanion.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerSmootherTest {

    @Test
    fun singleSample_passesThrough() {
        val smoother = PowerSmoother()
        assertEquals(150, smoother.update(150))
    }

    @Test
    fun threeSampleWindow_averagesRolling() {
        val smoother = PowerSmoother()
        smoother.update(150)
        smoother.update(160)
        assertEquals(160, smoother.update(170))
    }

    @Test
    fun gearJump_singleFrameSpike_isAbsorbed() {
        val smoother = PowerSmoother()
        smoother.update(150)
        smoother.update(150)
        // Raw jumps +50W on one frame (resistance shift); display moves ~17W.
        assertEquals(167, smoother.update(200))
    }

    @Test
    fun windowSlides_oldSamplesFallOff() {
        val smoother = PowerSmoother(windowSize = 3)
        smoother.update(100)
        smoother.update(100)
        smoother.update(100)
        // Window now [100, 100, 100]; three 200s flush it out.
        smoother.update(200)
        smoother.update(200)
        assertEquals(200, smoother.update(200))
    }

    @Test
    fun zeroSample_clearsImmediately_noLinger() {
        val smoother = PowerSmoother()
        smoother.update(200)
        smoother.update(200)
        assertEquals(0, smoother.update(0))
        assertEquals(0, smoother.value)
        // Next pedal stroke restarts clean, not dragged by stale history.
        assertEquals(180, smoother.update(180))
    }

    @Test
    fun reset_clearsWindowAndValue() {
        val smoother = PowerSmoother()
        smoother.update(200)
        smoother.reset()
        assertEquals(0, smoother.value)
        assertEquals(120, smoother.update(120))
    }

    @Test
    fun customWindowSize_honored() {
        val smoother = PowerSmoother(windowSize = 1)
        smoother.update(150)
        assertEquals(200, smoother.update(200))
    }

    @Test
    fun windowSize_clampedToValidRange() {
        val noAverage = PowerSmoother(windowSize = 0)
        noAverage.update(150)
        assertEquals(200, noAverage.update(200))
    }
}

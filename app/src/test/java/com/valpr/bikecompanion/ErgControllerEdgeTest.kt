package com.valpr.bikecompanion

import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.engine.ErgState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErgControllerEdgeTest {

    @Test
    fun reset_clearsIntegratorAndState() {
        val c = ErgController()
        // Drive into bailout then reset
        c.update(200, 100, 30.0, 1.0, false)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, c.state)
        c.reset()
        assertEquals(ErgState.INACTIVE, c.state)
        // After reset with good cadence, no stale bailout — back to ACTIVE
        val d = c.update(200, 100, 85.0, 1.0, false)
        assertEquals(ErgState.ACTIVE, d.state)
    }

    @Test
    fun recoveryBoundary_requiresSustainedThreshold() {
        val c = ErgController()
        c.update(200, 100, 30.0, 1.0, false) // enter bailout
        // 74.9 below 75 threshold -> stays
        var d = c.update(200, 100, 74.9, 1.0, false)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, d.state)
        // 3x at exactly 75 -> recovers
        c.update(200, 100, 75.0, 1.0, false)
        c.update(200, 100, 75.0, 1.0, false)
        d = c.update(200, 100, 75.0, 1.0, false)
        assertEquals(ErgState.ACTIVE, d.state)
    }

    @Test
    fun hrCap_appliesOnceOnScaledTarget() {
        val c = ErgController()
        // Base 200W at +10% intensity bias = 220W entering the controller;
        // critical-HR cap applies exactly one 10% reduction: 220 * 0.9 = 198.
        val d = c.update(220, 200, 85.0, 1.0, true)
        assertEquals(ErgState.ACTIVE, d.state)
        assertEquals(198, d.effectiveTargetWatts)
        assertTrue(d.isHrCapped)
        // Without cap the scaled target passes through untouched.
        val plain = ErgController().update(220, 200, 85.0, 1.0, false)
        assertEquals(220, plain.effectiveTargetWatts)
        // Without intensity the cap alone gives 180.
        val cappedBase = ErgController().update(200, 200, 85.0, 1.0, true)
        assertEquals(180, cappedBase.effectiveTargetWatts)
    }

    @Test
    fun freeRideToActive_reentryDoesNotCarryStaleTrim() {
        val c = ErgController()
        val free = c.update(null, 150, 85.0, 1.0, false)
        assertEquals(ErgState.FREE_RIDE, free.state)
        val active = c.update(200, 100, 85.0, 1.0, false)
        assertEquals(ErgState.ACTIVE, active.state)
        assertTrue((active.targetResistance in 1..32))
    }
}

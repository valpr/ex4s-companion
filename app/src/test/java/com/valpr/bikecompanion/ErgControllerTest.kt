package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.EchelonWattTable
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.engine.ErgState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ErgControllerTest {

    private lateinit var controller: ErgController

    @Before
    fun setUp() {
        controller = ErgController(
            kp = 0.05,
            ki = 0.01,
            cadenceFloorRpm = 60.0,
            recoveryThresholdRpm = 75.0,
            recoveryRequiredSeconds = 3,
            deadbandWatts = 5,
            recoveryResistance = 8
        )
    }

    @Test
    fun update_nominalResistance_matchesWattTableFeedforward() {
        // Target 200W at 90 RPM cadence
        val expectedBase = EchelonWattTable.resistanceFromPowerTarget(200, 90.0)

        val decision = controller.update(
            targetWatts = 200,
            actualWatts = 200,
            rawCadence = 90.0
        )

        assertEquals(ErgState.ACTIVE, decision.state)
        assertEquals(expectedBase, decision.nominalResistance)
        assertEquals(expectedBase, decision.targetResistance)
    }

    @Test
    fun update_withinDeadband_suppressesBleCommands() {
        // First cycle: establish target resistance
        val initialDecision = controller.update(
            targetWatts = 150,
            actualWatts = 150,
            rawCadence = 85.0
        )
        val initialRes = initialDecision.targetResistance

        // Second cycle: power error is 3W (within 5W deadband)
        val deadbandDecision = controller.update(
            targetWatts = 150,
            actualWatts = 147, // error = 3W <= 5W deadband
            rawCadence = 85.0
        )

        assertEquals(initialRes, deadbandDecision.targetResistance)
        assertFalse("Should suppress BLE command inside deadband", deadbandDecision.shouldSendBleCommand)
    }

    @Test
    fun update_largePowerDiscrepancy_clampsTrimToThreeLevels() {
        // Massive shortfall: Target 350W, Actual 100W (error = +250W)
        val decisionShortfall = controller.update(
            targetWatts = 350,
            actualWatts = 100,
            rawCadence = 90.0
        )

        assertEquals(3, decisionShortfall.trimOffset)
        assertEquals(decisionShortfall.nominalResistance + 3, decisionShortfall.targetResistance)

        // Reset and test massive overshoot: Target 100W, Actual 350W (error = -250W)
        controller.reset()
        val decisionOvershoot = controller.update(
            targetWatts = 100,
            actualWatts = 350,
            rawCadence = 90.0
        )

        assertEquals(-3, decisionOvershoot.trimOffset)
        assertEquals(decisionOvershoot.nominalResistance - 3, decisionOvershoot.targetResistance)
    }

    @Test
    fun update_cadenceBelowFloor_immediatelyBailsOutToLevelEight() {
        // Initialize at normal 85 RPM
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 85.0)

        // Cadence collapses to 50 RPM (below 60 RPM floor)
        val bailoutDecision = controller.update(
            targetWatts = 200,
            actualWatts = 100,
            rawCadence = 50.0
        )

        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, bailoutDecision.state)
        assertEquals(8, bailoutDecision.targetResistance)
        assertTrue(bailoutDecision.shouldSendBleCommand)
    }

    @Test
    fun update_cadenceFloorEntry_alwaysDispatchesEvenIfCacheReadsRecovery() {
        // After reset the cache already reads recoveryResistance (8). A fresh
        // cadence collapse must still dispatch (AGENTS.md §1 emergency bypass).
        controller.reset()
        val bailoutDecision = controller.update(
            targetWatts = 200,
            actualWatts = 100,
            rawCadence = 50.0
        )

        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, bailoutDecision.state)
        assertEquals(8, bailoutDecision.targetResistance)
        assertTrue("Emergency bailout must bypass de-duplication", bailoutDecision.shouldSendBleCommand)
    }

    @Test
    fun update_recoveryGate_requiresThreeConsecutiveSecondsAboveThreshold() {
        // 1. Trigger bailout
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 50.0)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, controller.state)

        // 2. Rider speeds up to 80 RPM for second 1
        val sec1 = controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 80.0)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, sec1.state)
        assertEquals(1, sec1.consecutiveRecoverySeconds)
        assertEquals(8, sec1.targetResistance)

        // 3. Rider stays at 80 RPM for second 2
        val sec2 = controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 80.0)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, sec2.state)
        assertEquals(2, sec2.consecutiveRecoverySeconds)

        // 4. Rider stumbles to 70 RPM (< 75 threshold) -> resets counter!
        val stumble = controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 70.0)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, stumble.state)
        assertEquals(0, stumble.consecutiveRecoverySeconds)

        // 5. Rider sustains 3 consecutive seconds:
        controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 80.0) // sec 1
        controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 80.0) // sec 2
        val recovery = controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 80.0) // sec 3

        assertEquals(ErgState.ACTIVE, recovery.state)
        assertEquals(0, recovery.consecutiveRecoverySeconds)
        assertTrue(recovery.targetResistance > 0)
    }

    @Test
    fun update_nullTarget_transitionsToFreeRide() {
        val decision = controller.update(
            targetWatts = null,
            actualWatts = 120,
            rawCadence = 80.0
        )

        assertEquals(ErgState.FREE_RIDE, decision.state)
        assertFalse(decision.shouldSendBleCommand)
    }

    @Test
    fun manualBailout_setsBailoutResistanceUntilResumed() {
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 85.0)

        // Manual clutch hit
        val clutchDecision = controller.suspendManually(bailoutResistance = 8)
        assertEquals(ErgState.MANUAL_BAILOUT, clutchDecision.state)
        assertEquals(8, clutchDecision.targetResistance)

        // Telemetry updates during manual bailout do not change resistance
        val inBailout = controller.update(targetWatts = 250, actualWatts = 80, rawCadence = 90.0)
        assertEquals(ErgState.MANUAL_BAILOUT, inBailout.state)
        assertEquals(8, inBailout.targetResistance)
        assertFalse(inBailout.shouldSendBleCommand)

        // Resume
        controller.resumeManually()
        val resumed = controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 90.0)
        assertEquals(ErgState.ACTIVE, resumed.state)
    }

    @Test
    fun update_dynamicHrCapping_scalesTargetDownByTenPercent() {
        // Target 200W, rider at 90 RPM
        val normalDecision = controller.update(
            targetWatts = 200,
            actualWatts = 200,
            rawCadence = 90.0,
            isCriticalHr = false
        )
        assertEquals(200, normalDecision.effectiveTargetWatts)
        assertFalse(normalDecision.isHrCapped)

        // When critical HR is flagged, target is scaled by 0.90 -> 180W
        val cappedDecision = controller.update(
            targetWatts = 200,
            actualWatts = 180,
            rawCadence = 90.0,
            isCriticalHr = true
        )
        assertEquals(180, cappedDecision.effectiveTargetWatts)
        assertTrue(cappedDecision.isHrCapped)
        val expectedBase180 = EchelonWattTable.resistanceFromPowerTarget(180, 90.0)
        assertEquals(expectedBase180, cappedDecision.nominalResistance)
    }
}

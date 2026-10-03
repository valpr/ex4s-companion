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
    fun effectiveFloor_prefersLowerOfConfiguredAndTargetMinusMargin() {
        // No prescription (Free Ride, custom segments): configured floor stands.
        assertEquals(60.0, ErgController.effectiveFloor(60.0, null), 1e-9)
        // High-cadence work: min(60, 90 − 15) = configured floor.
        assertEquals(60.0, ErgController.effectiveFloor(60.0, 90), 1e-9)
        // Boundary: min(60, 75 − 15) = configured floor.
        assertEquals(60.0, ErgController.effectiveFloor(60.0, 75), 1e-9)
        // Low-cadence climbs: min(60, 70 − 15) = 55 dip margin.
        assertEquals(55.0, ErgController.effectiveFloor(60.0, 70), 1e-9)
        // Retuned floor composes: min(50, 70 − 15) = user floor wins.
        assertEquals(50.0, ErgController.effectiveFloor(50.0, 70), 1e-9)
    }

    @Test
    fun update_singleSubFloorTick_doesNotBailOut() {
        // Initialize at normal 85 RPM
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 85.0)

        // One dropout-style sample at 50 RPM: grace tick, normal ERG continues.
        val graceDecision = controller.update(
            targetWatts = 200,
            actualWatts = 100,
            rawCadence = 50.0
        )

        assertEquals(ErgState.ACTIVE, graceDecision.state)
        assertEquals(ErgState.ACTIVE, controller.state)
    }

    @Test
    fun update_isolatedDipResetsEntryCounter() {
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 85.0)

        // Dip below floor for one tick, recover, dip again: never two in a row.
        controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 50.0)
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 85.0)
        val secondDip = controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 50.0)

        assertEquals(ErgState.ACTIVE, secondDip.state)
    }

    @Test
    fun update_cadenceBelowFloor_bailsOutAfterTwoConsecutiveTicks() {
        // Initialize at normal 85 RPM
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 85.0)

        // First sub-floor tick: grace, still ACTIVE.
        controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 50.0)
        assertEquals(ErgState.ACTIVE, controller.state)

        // Second consecutive sub-floor tick: bail out to level 8.
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
        // After reset the cache already reads recoveryResistance (8). A
        // sustained cadence collapse must still dispatch (AGENTS.md §1
        // emergency bypass) — after the entry debounce fills.
        controller.reset()
        controller.update(targetWatts = 200, actualWatts = 100, rawCadence = 50.0)
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
        // 1. Trigger bailout (two consecutive sub-floor ticks)
        controller.update(targetWatts = 200, actualWatts = 200, rawCadence = 50.0)
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
    fun freeRide_toErgSegment_resumesActiveAndDispatches() {
        controller.update(targetWatts = 150, actualWatts = 150, rawCadence = 80.0)
        val free = controller.update(targetWatts = null, actualWatts = 150, rawCadence = 80.0)
        assertEquals(ErgState.FREE_RIDE, free.state)

        // Rider under target on re-entry: fresh integral (reset in FREE_RIDE),
        // new target (lastTarget was nulled), so PI trim dispatches.
        val back = controller.update(targetWatts = 150, actualWatts = 100, rawCadence = 80.0)
        assertEquals(ErgState.ACTIVE, back.state)
        assertEquals(150, back.effectiveTargetWatts)
        assertTrue(back.shouldSendBleCommand)
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

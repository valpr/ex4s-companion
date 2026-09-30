package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import app.cash.turbine.test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Wearable/HR paths of [WorkoutSessionManager].
 *
 * See [WorkoutSessionManagerTest] for the two-clock rationale: the manager
 * runs in its own [managerScope] (separate scheduler from [runTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSessionManagerWearableTest {

    private lateinit var managerScope: TestScope
    private lateinit var telemetryFlow: MutableStateFlow<BikeTelemetry>
    private lateinit var commandedResistance: MutableList<Int>
    private lateinit var ergController: ErgController

    @Before
    fun setUp() {
        managerScope = TestScope()
        telemetryFlow = MutableStateFlow(BikeTelemetry())
        commandedResistance = mutableListOf()
        ergController = ErgController()
    }

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    private val defaultProfile = UserProfile(
        ftp = 200,
        weightKg = 75.0f,
        cadenceFloorRpm = 60,
        cadenceRecoveryRpm = 75,
        ergKp = 0.05f,
        ergKi = 0.01f,
        maxHeartRate = 190,
        criticalHeartRate = 175
    )

    private fun createSessionManager(profile: UserProfile = defaultProfile): WorkoutSessionManager {
        commandedResistance.clear()
        ergController.reset()
        return WorkoutSessionManager(
            telemetryFlow = telemetryFlow,
            onSetResistance = { commandedResistance.add(it) },
            userProfileFlow = flowOf(profile),
            ergController = ergController,
            scope = managerScope
        )
    }

    private fun settleManager() {
        managerScope.testScheduler.advanceUntilIdle()
    }

    private fun managerTime(ms: Long) {
        managerScope.testScheduler.advanceTimeBy(ms)
        managerScope.testScheduler.runCurrent()
    }

    @Test
    fun testHeartRateUpdate_updatesStateAndMetrics() = runTest {
        val manager = createSessionManager()
        settleManager()

        // Initial HR is 0
        assertEquals(0, manager.sessionState.value.currentHeartRate)
        assertFalse(manager.sessionState.value.isCriticalHrActive)

        // Update HR
        manager.updateHeartRate(145)
        assertEquals(145, manager.sessionState.value.currentHeartRate)
        assertFalse(manager.sessionState.value.isCriticalHrActive)

        // Invalid BPM (<= 0) ignored
        manager.updateHeartRate(0)
        assertEquals(145, manager.sessionState.value.currentHeartRate)

        manager.updateHeartRate(-20)
        assertEquals(145, manager.sessionState.value.currentHeartRate)
    }

    @Test
    fun testDynamicHrCapping_triggersWhenThresholdCrossed() = runTest {
        val manager = createSessionManager()
        settleManager()

        manager.hapticAlerts.test {
            // Under critical threshold (175) -> no emission
            manager.updateHeartRate(170)
            assertFalse(manager.sessionState.value.isCriticalHrActive)
            assertFalse(ergController.isCriticalHrActive)
            expectNoEvents()

            // Cross critical threshold -> activates dynamic capping & emits haptic warning
            manager.updateHeartRate(178)
            assertTrue(manager.sessionState.value.isCriticalHrActive)
            assertTrue(ergController.isCriticalHrActive)
            assertEquals(HapticAlertType.CRITICAL_HR_WARNING, awaitItem())

            // Further readings above threshold do not spam repeated haptic warnings
            manager.updateHeartRate(180)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testClutchHaptics_bailoutAndResumeEmitInOrder() = runTest {
        val manager = createSessionManager()
        settleManager()

        manager.hapticAlerts.test {
            manager.toggleClutch()
            assertEquals(HapticAlertType.BAILOUT_TRIGGERED, awaitItem())

            manager.toggleClutch()
            assertEquals(HapticAlertType.RESUME_TRIGGERED, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testCadenceFloorBailout_emitsHapticOnce() = runTest {
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 180, resistanceLevel = 14)
        val manager = createSessionManager()
        settleManager()
        val workout = Workout(
            name = "Floor",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 60, power = 1.0f))
        )
        manager.startWorkout(workout)

        manager.hapticAlerts.test {
            // Collapse cadence below floor (60): next 1s tick must bail out + buzz once.
            telemetryFlow.value = BikeTelemetry(cadenceRpm = 30, estimatedWatts = 60, resistanceLevel = 14)
            managerTime(1100L)
            assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, ergController.state)
            assertEquals(HapticAlertType.BAILOUT_TRIGGERED, awaitItem())

            // Steady-state maintenance ticks must not re-buzz.
            managerTime(1100L)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testIntensityPlusHrCap_effectiveTargetIsBaseTimesIntensityTimesCap() = runTest {
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 200, resistanceLevel = 14)
        val manager = createSessionManager()
        settleManager()
        val workout = Workout(
            name = "Cap Order",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 120, power = 1.0f))
        )
        manager.startWorkout(workout)
        manager.adjustIntensity(0.10f) // 200W base -> 220W scaled
        manager.updateHeartRate(178) // critical -> 10% cap -> 198W
        managerTime(1100L)
        // base (200) x intensity (1.10) x cap (0.90) = 198, applied once each
        assertEquals(198, manager.sessionState.value.targetWatts)
        assertTrue(manager.sessionState.value.ergDecision?.isHrCapped == true)
    }

    @Test
    fun testDynamicHrCapping_hysteresisPreventsJitter() = runTest {
        val manager = createSessionManager()
        settleManager()

        // Critical threshold is 175. Hysteresis band is 5 BPM (clears below 170).
        manager.updateHeartRate(176)
        assertTrue(manager.sessionState.value.isCriticalHrActive)

        // Small drop to 173 (within 5 BPM hysteresis) -> stays capped
        manager.updateHeartRate(173)
        assertTrue(manager.sessionState.value.isCriticalHrActive)
        assertTrue(ergController.isCriticalHrActive)

        // Drop below 170 -> clears cap
        manager.updateHeartRate(168)
        assertFalse(manager.sessionState.value.isCriticalHrActive)
        assertFalse(ergController.isCriticalHrActive)
    }

    @Test
    fun testResumeManually_reEngagesInstantly() = runTest {
        val manager = createSessionManager()
        settleManager()

        val sampleWorkout = Workout(
            name = "Test Interval",
            description = "Test",
            segments = listOf(
                WorkoutSegment.SteadyState(durationSeconds = 60, power = 1.0f)
            )
        )

        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 200, resistanceLevel = 15, speedKmh = 30.0)
        settleManager()
        manager.startWorkout(sampleWorkout)
        managerTime(3000L)

        // Suspend manually
        manager.toggleClutch()
        assertEquals(ErgState.MANUAL_BAILOUT, ergController.state)

        // Resume manually via watch resume slap
        manager.resumeManually()
        assertEquals(ErgState.ACTIVE, ergController.state)
    }

    @Test
    fun testSummary_calculatesAvgAndMaxHeartRate() = runTest {
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 200, resistanceLevel = 15, speedKmh = 30.0)
        val manager = createSessionManager()
        settleManager()

        manager.startWorkout(null) // Free ride

        manager.updateHeartRate(140)
        managerTime(1100L) // 1 second tick

        manager.updateHeartRate(160)
        managerTime(1100L) // 2 second tick

        manager.updateHeartRate(150)
        managerTime(1100L) // 3 second tick

        manager.stopWorkout()
        val summary = manager.sessionState.value.summary

        org.junit.Assert.assertNotNull(summary)
        assertEquals(150, summary!!.avgHeartRate)
        assertEquals(160, summary.maxHeartRate)
        // Spec §4: full summary invariants
        assertTrue(summary.maxWatts >= summary.avgWatts)
        assertTrue(summary.maxCadence >= summary.avgCadence)
        assertTrue(summary.totalWorkKj >= 0.0)
        assertTrue(summary.totalDistanceKm >= 0.0)
    }

    @Test
    fun testSessionLoop_ticksOnManagerClockOnly() = runTest {
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 150, resistanceLevel = 10)
        val manager = createSessionManager()
        settleManager()
        manager.startWorkout(null)
        // runTest's own clock must not drive the manager loop (separate scheduler).
        assertEquals(0, manager.sessionState.value.elapsedSeconds)
        managerTime(2100L)
        assertTrue(manager.sessionState.value.elapsedSeconds >= 2)
        assertEquals(SessionStatus.RUNNING, manager.sessionState.value.status)
    }
}

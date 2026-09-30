package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.engine.ErgState
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Session manager tests.
 *
 * Stability note: the manager owns infinite coroutines (telemetry collectors,
 * 1s session loop). It runs in its OWN [TestScope] with a SEPARATE scheduler
 * from [runTest], driven explicitly via [managerTime]. Sharing runTest's
 * scheduler hangs runTest's teardown advanceUntilIdle forever (verified via
 * thread dump: TestBuilders.kt advanceUntilIdle busy-loop).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSessionManagerTest {

    private lateinit var telemetryFlow: MutableStateFlow<BikeTelemetry>
    private lateinit var dispatchedResistance: MutableList<Int>
    private lateinit var ergController: ErgController
    private lateinit var managerScope: TestScope

    private val defaultProfile = UserProfile(ftp = 200, weightKg = 75f)

    @Before
    fun setUp() {
        telemetryFlow = MutableStateFlow(BikeTelemetry())
        dispatchedResistance = mutableListOf()
        ergController = ErgController()
        managerScope = TestScope()
    }

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    private fun createManager(profile: UserProfile = defaultProfile): WorkoutSessionManager = WorkoutSessionManager(
        telemetryFlow = telemetryFlow,
        onSetResistance = { dispatchedResistance.add(it) },
        userProfileFlow = flowOf(profile),
        ergController = ergController,
        scope = managerScope
    )

    /** Flushes manager init collectors (profile -> FTP guard) without touching runTest's clock. */
    private fun settleManager() {
        managerScope.testScheduler.advanceUntilIdle()
    }

    /** Advances the MANAGER's session loop clock (not runTest's). */
    private fun managerTime(ms: Long) {
        managerScope.testScheduler.advanceTimeBy(ms)
        managerScope.testScheduler.runCurrent()
    }

    @Test
    fun startWorkout_initializesSessionRunning() = runTest {
        val manager = createManager()
        settleManager()
        val testWorkout = Workout(
            name = "Test Ride",
            segments = listOf(
                WorkoutSegment.SteadyState(durationSeconds = 600, power = 0.85f)
            )
        )

        manager.startWorkout(testWorkout)

        val state = manager.sessionState.value
        assertEquals(SessionStatus.RUNNING, state.status)
        assertEquals("Test Ride", state.workout?.name)
        assertEquals(600, state.totalSeconds)
        assertEquals(0, state.elapsedSeconds)
    }

    @Test
    fun pauseAndResume_updatesStatusCorrectly() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(null) // Free ride
        assertEquals(SessionStatus.RUNNING, manager.sessionState.value.status)

        manager.pauseWorkout()
        assertEquals(SessionStatus.PAUSED, manager.sessionState.value.status)

        manager.resumeWorkout()
        assertEquals(SessionStatus.RUNNING, manager.sessionState.value.status)
    }

    @Test
    fun sessionLoop_advancesElapsedAndAutoCompletes() = runTest {
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 150, resistanceLevel = 10)
        val manager = createManager()
        settleManager()
        val workout = Workout(
            name = "Short",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 3, power = 0.5f))
        )
        manager.startWorkout(workout)
        managerTime(1100L)
        assertEquals(1, manager.sessionState.value.elapsedSeconds)
        managerTime(2200L)
        assertEquals(SessionStatus.COMPLETED, manager.sessionState.value.status)
        assertNotNull(manager.sessionState.value.summary)
    }

    @Test
    fun sessionLoop_pausedFreezesTicks() = runTest {
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 150, resistanceLevel = 10)
        val manager = createManager()
        settleManager()
        manager.startWorkout(null)
        managerTime(2100L)
        val elapsed = manager.sessionState.value.elapsedSeconds
        assertTrue(elapsed >= 2)
        manager.pauseWorkout()
        managerTime(3000L)
        assertEquals(elapsed, manager.sessionState.value.elapsedSeconds)
    }

    @Test
    fun adjustIntensity_clampsWithinBounds() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(null)
        assertEquals(1.0f, manager.sessionState.value.intensityScale, 0.001f)

        manager.adjustIntensity(0.10f)
        assertEquals(1.10f, manager.sessionState.value.intensityScale, 0.001f)

        manager.adjustIntensity(-0.80f) // 1.10 - 0.80 = 0.30 -> clamps to 0.50
        assertEquals(0.50f, manager.sessionState.value.intensityScale, 0.001f)

        manager.adjustIntensity(2.0f) // clamps to 1.50
        assertEquals(1.50f, manager.sessionState.value.intensityScale, 0.001f)
    }

    @Test
    fun toggleClutch_cyclesErgBailout() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(null)

        manager.toggleClutch()
        assertEquals(ErgState.MANUAL_BAILOUT, ergController.state)

        manager.toggleClutch()
        assertEquals(ErgState.ACTIVE, ergController.state)
    }

    @Test
    fun stopWorkout_transitionsToCompletedAndCreatesSummary() = runTest {
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 200, resistanceLevel = 12)
        val manager = createManager()
        settleManager()
        val workout = Workout(
            name = "Quick Spin",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 120, power = 1.0f))
        )
        manager.startWorkout(workout)
        managerTime(2100L)

        manager.stopWorkout()

        val state = manager.sessionState.value
        assertEquals(SessionStatus.COMPLETED, state.status)
        assertNotNull(state.summary)
        assertEquals("Quick Spin", state.summary?.workoutName)
        // Spec §4: summary carries peaks + work + distance + time
        val summary = state.summary!!
        assertTrue(summary.maxWatts >= summary.avgWatts)
        assertTrue(summary.maxCadence >= summary.avgCadence)
        assertTrue(summary.totalWorkKj >= 0.0)
        assertTrue(summary.totalDurationSeconds >= 2)
    }

    @Test
    fun setManualResistance_dispatchesClampedResistance() = runTest {
        val manager = createManager()
        settleManager()
        manager.setManualResistance(15)
        manager.setManualResistance(0)
        manager.setManualResistance(35)
        assertEquals(listOf(15, 1, 32), dispatchedResistance)
    }

    @Test
    fun adjustManualResistance_stepsResistanceCorrectly() = runTest {
        val manager = createManager()
        settleManager()
        telemetryFlow.value = BikeTelemetry(resistanceLevel = 10)
        settleManager()

        manager.adjustManualResistance(1)
        manager.adjustManualResistance(-2)
        assertEquals(listOf(11, 8), dispatchedResistance)
    }

    @Test
    fun toggleClutch_immediatelyUpdatesSessionDecisionAndDispatches() = runTest {
        val manager = createManager()
        settleManager()
        val workout = Workout(
            name = "FTP Test",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 300, power = 1.0f)) // 200W target
        )
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 200)
        settleManager()
        manager.startWorkout(workout)

        // Bailout
        manager.toggleClutch()
        assertEquals(ErgState.MANUAL_BAILOUT, ergController.state)
        assertEquals(ErgState.MANUAL_BAILOUT, manager.sessionState.value.ergDecision?.state)
        assertTrue(dispatchedResistance.contains(8))

        // Resume immediately
        dispatchedResistance.clear()
        manager.toggleClutch()
        assertEquals(ErgState.ACTIVE, ergController.state)
        assertEquals(ErgState.ACTIVE, manager.sessionState.value.ergDecision?.state)
        // Bike should receive the newly computed resistance immediately
        assertNotNull(manager.sessionState.value.ergDecision)
        assertTrue(dispatchedResistance.isNotEmpty())
    }

    @Test
    fun startWorkout_withoutFtp_blocksStructuredButAllowsFreeRide() = runTest {
        val noFtpManager = WorkoutSessionManager(
            telemetryFlow = telemetryFlow,
            onSetResistance = { dispatchedResistance.add(it) },
            userProfileFlow = flowOf(UserProfile(ftp = 0, weightKg = 75f)),
            ergController = ErgController(),
            scope = managerScope
        )
        settleManager()
        val structured = Workout(
            name = "Blocked",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 300, power = 0.9f))
        )
        val blocked = noFtpManager.startWorkout(structured)
        assertTrue(blocked.isFailure)
        assertEquals(SessionStatus.IDLE, noFtpManager.sessionState.value.status)

        val freeRide = noFtpManager.startWorkout(null)
        assertTrue(freeRide.isSuccess)
        assertEquals(SessionStatus.RUNNING, noFtpManager.sessionState.value.status)
    }

    @Test
    fun startWorkout_preservesLiveTelemetryAndAthleteContext() = runTest {
        val manager = createManager()
        settleManager()
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 150, resistanceLevel = 10)
        settleManager()
        manager.updateHeartRate(140)
        val workout = Workout(
            name = "Preserve",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 300, power = 0.8f))
        )
        val result = manager.startWorkout(workout)
        assertTrue(result.isSuccess)
        val state = manager.sessionState.value
        assertEquals(85, state.latestTelemetry.cadenceRpm)
        assertEquals(140, state.currentHeartRate)
        assertEquals(190, state.athleteMaxHr)
    }

    @Test
    fun startWorkout_disconnectedBike_blocksStructuredAndFreeRide() = runTest {
        val offlineManager = WorkoutSessionManager(
            telemetryFlow = telemetryFlow,
            onSetResistance = { dispatchedResistance.add(it) },
            userProfileFlow = flowOf(defaultProfile),
            ergController = ErgController(),
            scope = managerScope,
            isBikeConnected = { false }
        )
        settleManager()
        val structured = Workout(
            name = "Blocked",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 300, power = 0.9f))
        )
        assertTrue(offlineManager.startWorkout(structured).isFailure)
        assertEquals(SessionStatus.IDLE, offlineManager.sessionState.value.status)

        assertTrue(offlineManager.startWorkout(null).isFailure)
        assertEquals(SessionStatus.IDLE, offlineManager.sessionState.value.status)
        assertTrue(dispatchedResistance.isEmpty())
    }
}

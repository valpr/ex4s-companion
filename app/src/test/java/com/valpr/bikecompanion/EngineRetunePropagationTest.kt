package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Settings write-through: mid-workout DataStore retune must reach the live
 * [ErgController] gains (and HR thresholds) without restarting the session.
 * The ViewModel delegates are 3-line pass-throughs; this locks the path that
 * actually moves the bike (repo -> profile flow -> controller -> dispatch).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineRetunePropagationTest {

    private lateinit var managerScope: TestScope
    private lateinit var telemetryFlow: MutableStateFlow<BikeTelemetry>
    private lateinit var profileFlow: MutableStateFlow<UserProfile>
    private lateinit var dispatchedResistance: MutableList<Int>
    private lateinit var ergController: ErgController

    private val baseProfile = UserProfile(
        ftp = 200,
        weightKg = 75.0f,
        cadenceFloorRpm = 60,
        cadenceRecoveryRpm = 75,
        ergKp = 0.05f,
        ergKi = 0.01f,
        maxHeartRate = 190,
        criticalHeartRate = 175
    )

    @Before
    fun setUp() {
        managerScope = TestScope()
        telemetryFlow = MutableStateFlow(BikeTelemetry(cadenceRpm = 85, estimatedWatts = 180, resistanceLevel = 14))
        profileFlow = MutableStateFlow(baseProfile)
        dispatchedResistance = mutableListOf()
        ergController = ErgController()
    }

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    private fun createManager(): WorkoutSessionManager = WorkoutSessionManager(
        telemetryFlow = telemetryFlow,
        onSetResistance = { dispatchedResistance.add(it) },
        userProfileFlow = profileFlow,
        ergController = ergController,
        scope = managerScope
    )

    private fun settleManager() {
        managerScope.testScheduler.advanceUntilIdle()
    }

    private fun managerTime(ms: Long) {
        managerScope.testScheduler.advanceTimeBy(ms)
        managerScope.testScheduler.runCurrent()
    }

    private fun structuredWorkout() = Workout(
        name = "Retune",
        segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 600, power = 1.0f))
    )

    @Test
    fun midWorkoutRetune_updatesLiveGainsAndAthleteContext() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(structuredWorkout())
        managerTime(1100L)
        assertEquals(0.05, ergController.kp, 1e-9)
        assertEquals(190, manager.sessionState.value.athleteMaxHr)
        assertEquals(60, manager.sessionState.value.athleteRestingHr)
        assertEquals(false, manager.sessionState.value.useKarvonenZones)

        // Settings write-through lands mid-ride (same tick loop, no restart).
        profileFlow.value = baseProfile.copy(
            ergKp = 0.20f,
            ergKi = 0.05f,
            cadenceFloorRpm = 55,
            cadenceRecoveryRpm = 70,
            maxHeartRate = 185,
            restingHeartRate = 50,
            useKarvonenZones = true
        )
        managerScope.testScheduler.runCurrent()

        assertEquals(0.20, ergController.kp, 1e-6)
        assertEquals(0.05, ergController.ki, 1e-6)
        assertEquals(55.0, ergController.cadenceFloorRpm, 1e-9)
        assertEquals(70.0, ergController.recoveryThresholdRpm, 1e-9)
        assertEquals(185, manager.sessionState.value.athleteMaxHr)
        assertEquals(50, manager.sessionState.value.athleteRestingHr)
        assertEquals(true, manager.sessionState.value.useKarvonenZones)

        // Session keeps ticking with the new gains.
        managerTime(1100L)
        assertEquals(2, manager.sessionState.value.elapsedSeconds)
    }

    @Test
    fun retunedCriticalHr_redefinesHysteresisThreshold() = runTest {
        val manager = createManager()
        settleManager()

        // 155 BPM is fine under the stock 175 threshold.
        manager.updateHeartRate(155)
        assertEquals(false, manager.sessionState.value.isCriticalHrActive)

        // Retune critical HR to 150: the same 155 BPM reading is now critical.
        profileFlow.value = baseProfile.copy(criticalHeartRate = 150)
        managerScope.testScheduler.runCurrent()
        manager.updateHeartRate(155)
        assertTrue(manager.sessionState.value.isCriticalHrActive)
        assertTrue(ergController.isCriticalHrActive)
    }

    @Test
    fun retunedCadenceFloor_triggersBailoutDispatch() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(structuredWorkout())

        // Cadence 70 clears the stock floor of 60: session runs ERG ACTIVE.
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 70, estimatedWatts = 170, resistanceLevel = 14)
        managerScope.testScheduler.runCurrent()
        managerTime(1100L)
        assertEquals(ErgState.ACTIVE, ergController.state)

        // Retune floor to 80 mid-ride: 70 RPM now collapses -> recovery drop
        // after the two-tick entry debounce fills.
        dispatchedResistance.clear()
        profileFlow.value = baseProfile.copy(cadenceFloorRpm = 80)
        managerScope.testScheduler.runCurrent()
        managerTime(1100L)
        managerTime(1100L)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, ergController.state)
        assertTrue(
            "Retuned floor must actuate the bike immediately",
            dispatchedResistance.contains(8)
        )
    }

    @Test
    fun targetAwareFloor_givesLowCadenceTargetDipMargin() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(
            Workout(
                name = "Climb",
                segments = listOf(
                    WorkoutSegment.SteadyState(durationSeconds = 600, power = 0.82f, targetCadence = 70)
                )
            )
        )

        // Riding 2 RPM under the global floor but above the effective floor
        // (70 − 15 = 55): no bailout across sustained ticks.
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 58, estimatedWatts = 170, resistanceLevel = 14)
        managerScope.testScheduler.runCurrent()
        managerTime(1100L)
        managerTime(1100L)
        managerTime(1100L)
        assertEquals(55.0, ergController.cadenceFloorRpm, 1e-9)
        assertEquals(ErgState.ACTIVE, ergController.state)

        // Genuine collapse below the effective floor still bails out.
        telemetryFlow.value = BikeTelemetry(cadenceRpm = 50, estimatedWatts = 100, resistanceLevel = 14)
        managerTime(1100L)
        managerTime(1100L)
        assertEquals(ErgState.CADENCE_FLOOR_BAILOUT, ergController.state)
        assertTrue(dispatchedResistance.contains(8))
    }

    @Test
    fun targetAwareFloor_highCadenceTargetKeepsConfiguredFloor() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(
            Workout(
                name = "Sprint",
                segments = listOf(
                    WorkoutSegment.SteadyState(durationSeconds = 600, power = 1.0f, targetCadence = 95)
                )
            )
        )

        telemetryFlow.value = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 200, resistanceLevel = 14)
        managerScope.testScheduler.runCurrent()
        managerTime(1100L)
        // min(60, 95 − 15) = configured floor.
        assertEquals(60.0, ergController.cadenceFloorRpm, 1e-9)
        assertEquals(ErgState.ACTIVE, ergController.state)
    }
}

package com.valpr.bikecompanion

import com.valpr.bikecompanion.bike.api.BikeCapabilities
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeErgWorkoutSessionTest {

    private lateinit var telemetryFlow: MutableStateFlow<BikeTelemetry>
    private lateinit var dispatchedResistance: MutableList<Int>
    private lateinit var dispatchedPower: MutableList<Int>
    private lateinit var ergController: ErgController
    private lateinit var managerScope: TestScope
    private val defaultProfile = UserProfile(ftp = 200, weightKg = 75f)

    private val ftmsCapabilities = BikeCapabilities(
        modelName = "KICKR Core",
        resistanceRange = 1..100,
        reportsMeasuredPower = true,
        supportsNativeErg = true,
        reportsDistance = true,
        resistanceModel = null
    )

    @Before
    fun setUp() {
        telemetryFlow = MutableStateFlow(BikeTelemetry(cadenceRpm = 85, estimatedWatts = 200))
        dispatchedResistance = mutableListOf()
        dispatchedPower = mutableListOf()
        ergController = ErgController()
        managerScope = TestScope()
    }

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    private fun createManager(): WorkoutSessionManager = WorkoutSessionManager(
        telemetryFlow = telemetryFlow,
        onSetResistance = { dispatchedResistance.add(it) },
        userProfileFlow = flowOf(defaultProfile),
        ergController = ergController,
        scope = managerScope,
        capabilitiesFlow = MutableStateFlow(ftmsCapabilities),
        onSetTargetPower = { dispatchedPower.add(it) }
    )

    private fun testWorkout(): Workout = Workout(
        name = "FTP Interval",
        description = "Test workout",
        segments = listOf(
            WorkoutSegment.SteadyState(durationSeconds = 300, power = 1.0f) // 200W target
        )
    )

    @Test
    fun nativeErg_dispatchesTargetPower_insteadOfResistance() = runTest {
        val manager = createManager()
        managerScope.runCurrent()

        manager.startWorkout(testWorkout())
        // Settle init and advance session loop by 1 second
        managerScope.testScheduler.advanceTimeBy(1000L)
        managerScope.runCurrent()

        assertTrue("Should dispatch to target power port on native ERG", dispatchedPower.isNotEmpty())
        assertEquals(200, dispatchedPower.last())
        assertTrue("Should not dispatch manual resistance on native ERG", dispatchedResistance.isEmpty())
    }

    @Test
    fun nativeErg_clutchBailout_dispatchesRecoveryWatts() = runTest {
        val manager = createManager()
        managerScope.runCurrent()

        manager.startWorkout(testWorkout())
        managerScope.testScheduler.advanceTimeBy(1000L)
        managerScope.runCurrent()
        dispatchedPower.clear()

        // Toggle Clutch into bailout
        manager.toggleClutch()
        managerScope.runCurrent()

        assertEquals(ErgState.MANUAL_BAILOUT, manager.sessionState.value.ergDecision?.state)
        assertTrue(dispatchedPower.isNotEmpty())
        assertEquals(WorkoutSessionManager.RECOVERY_WATTS, dispatchedPower.last())

        // Resume manually instantly respatches full workout target watts
        dispatchedPower.clear()
        manager.toggleClutch()
        managerScope.runCurrent()

        assertEquals(ErgState.ACTIVE, manager.sessionState.value.ergDecision?.state)
        assertTrue(dispatchedPower.isNotEmpty())
        assertEquals(200, dispatchedPower.last())
    }
}

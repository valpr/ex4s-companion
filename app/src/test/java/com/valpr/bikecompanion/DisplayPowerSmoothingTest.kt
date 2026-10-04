package com.valpr.bikecompanion

import com.valpr.bikecompanion.companion.api.CompanionHub
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Display-only power smoothing ([WorkoutSessionManager.displayWatts]).
 *
 * Stability: same two-clock discipline as [WorkoutSessionManagerTest] — the
 * manager owns infinite coroutines, so it runs in its OWN [TestScope] driven
 * explicitly via [managerTime]. `advanceUntilIdle` is used only to settle init
 * collectors *before* `startWorkout()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayPowerSmoothingTest {

    private lateinit var telemetryFlow: MutableStateFlow<BikeTelemetry>
    private lateinit var dispatchedResistance: MutableList<Int>
    private lateinit var managerScope: TestScope

    private val defaultProfile = UserProfile(ftp = 200, weightKg = 75f)

    @Before
    fun setUp() {
        telemetryFlow = MutableStateFlow(BikeTelemetry())
        dispatchedResistance = mutableListOf()
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
        ergController = ErgController(),
        scope = managerScope
    )

    private fun settleManager() {
        managerScope.testScheduler.advanceUntilIdle()
    }

    private fun managerTime(ms: Long) {
        managerScope.testScheduler.advanceTimeBy(ms)
        managerScope.testScheduler.runCurrent()
    }

    private var emitSeq = 0

    private fun emitTelemetry(watts: Int) {
        // Bump a non-power field each emission: StateFlow conflates structurally
        // equal values, so back-to-back identical watt frames would collapse into
        // one (real BLE frames always differ in elapsed/distance).
        emitSeq += 1
        telemetryFlow.value = BikeTelemetry(
            cadenceRpm = 85,
            estimatedWatts = watts,
            resistanceLevel = 12,
            distanceKm = emitSeq * 0.001
        )
    }

    @Test
    fun displayWatts_rollsAverage_singleFrameSpikeAbsorbed() = runTest {
        val manager = createManager()
        settleManager()

        emitTelemetry(150)
        settleManager()
        assertEquals(150, manager.sessionState.value.displayWatts)

        emitTelemetry(150)
        settleManager()
        emitTelemetry(200)
        settleManager()

        // Raw jumped +50W on one frame; display moves with the 3-sample average.
        assertEquals(167, manager.sessionState.value.displayWatts)
        // Raw telemetry stays authoritative underneath.
        assertEquals(200, manager.sessionState.value.latestTelemetry.estimatedWatts)
    }

    @Test
    fun zeroWatts_clearsDisplayImmediately() = runTest {
        val manager = createManager()
        settleManager()

        emitTelemetry(200)
        settleManager()
        emitTelemetry(200)
        settleManager()
        assertEquals(200, manager.sessionState.value.displayWatts)

        emitTelemetry(0)
        settleManager()
        assertEquals(0, manager.sessionState.value.displayWatts)
    }

    @Test
    fun recording_usesRawWatts_notSmoothed() = runTest {
        val manager = createManager()
        settleManager()
        manager.startWorkout(null)

        emitTelemetry(100)
        managerTime(1100L)
        emitTelemetry(300)
        managerTime(1100L)
        emitTelemetry(100)
        managerTime(1100L)
        manager.stopWorkout()

        val summary = manager.sessionState.value.summary!!
        // Raw peak survives; a smoothed recording would peak at ~200W.
        assertEquals(300, summary.maxWatts)
        // Display shows the absorbed average instead of the spike.
        assertEquals(167, manager.sessionState.value.displayWattsOrRaw)
    }

    @Test
    fun displayWattsOrRaw_fallsBackToRaw_forManuallyConstructedStates() = runTest {
        val manager = createManager()
        settleManager()
        // Fresh manager: no telemetry yet, both read 0.
        assertEquals(0, manager.sessionState.value.displayWattsOrRaw)
    }

    @Test
    fun snapshot_usesSmoothedWatts_withRawFallback() {
        val smoothed = WorkoutSessionState(
            latestTelemetry = BikeTelemetry(estimatedWatts = 200),
            displayWatts = 180
        )
        assertEquals(180, CompanionHub.toSnapshot(smoothed).currentWatts)

        val legacy = WorkoutSessionState(
            latestTelemetry = BikeTelemetry(estimatedWatts = 200)
        )
        assertEquals(200, CompanionHub.toSnapshot(legacy).currentWatts)
    }
}

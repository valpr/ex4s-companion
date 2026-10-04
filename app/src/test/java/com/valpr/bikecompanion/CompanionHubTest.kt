package com.valpr.bikecompanion

import com.valpr.bikecompanion.companion.api.CompanionCapability
import com.valpr.bikecompanion.companion.api.CompanionDeviceProvider
import com.valpr.bikecompanion.companion.api.CompanionHub
import com.valpr.bikecompanion.companion.api.DeviceLinkState
import com.valpr.bikecompanion.companion.api.RemoteCommand
import com.valpr.bikecompanion.companion.api.RemoteWorkoutSnapshot
import com.valpr.bikecompanion.companion.api.WorkoutControlPort
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.engine.ErgDecision
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionHubTest {

    private lateinit var hubScope: TestScope

    @Before
    fun setup() {
        hubScope = TestScope()
    }

    @After
    fun tearDown() {
        hubScope.cancel()
    }

    private class FakeWorkoutControl : WorkoutControlPort {
        var clutchToggleCount = 0
        var resumeManuallyCount = 0
        var intensityDeltas = mutableListOf<Float>()
        var pauseCount = 0
        var resumeCount = 0
        var stopCount = 0

        override fun toggleClutch() {
            clutchToggleCount++
        }
        override fun resumeManually() {
            resumeManuallyCount++
        }
        override fun adjustIntensity(delta: Float) {
            intensityDeltas.add(delta)
        }
        override fun pauseWorkout() {
            pauseCount++
        }
        override fun resumeWorkout() {
            resumeCount++
        }
        override fun stopWorkout() {
            stopCount++
        }
    }

    private class FakeCompanionProvider(
        override val id: String = "test_provider",
        override val displayName: String = "Test Companion",
        override val capabilities: Set<CompanionCapability> = setOf(
            CompanionCapability.WORKOUT_MIRROR,
            CompanionCapability.HAPTIC_FEEDBACK,
            CompanionCapability.ROTARY_INPUT
        )
    ) : CompanionDeviceProvider {
        override val linkState = MutableStateFlow(DeviceLinkState.CONNECTED)
        override val remoteCommands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 16)
        val sentSnapshots = mutableListOf<RemoteWorkoutSnapshot>()
        val triggeredHaptics = mutableListOf<HapticAlertType>()

        override suspend fun sendSnapshot(snapshot: RemoteWorkoutSnapshot) {
            sentSnapshots.add(snapshot)
        }

        override suspend fun triggerHaptic(type: HapticAlertType) {
            triggeredHaptics.add(type)
        }
    }

    @Test
    fun remoteCommands_routeToWorkoutControlPort() = runTest {
        val control = FakeWorkoutControl()
        val provider = FakeCompanionProvider()
        val sessionState = MutableStateFlow(WorkoutSessionState())
        val haptics = MutableSharedFlow<HapticAlertType>(extraBufferCapacity = 16)

        CompanionHub(
            providers = listOf(provider),
            workoutControl = control,
            sessionState = sessionState,
            hapticAlerts = haptics,
            scope = hubScope
        )

        hubScope.runCurrent()

        provider.remoteCommands.emit(RemoteCommand.ToggleClutch)
        provider.remoteCommands.emit(RemoteCommand.ResumeManually)
        provider.remoteCommands.emit(RemoteCommand.AdjustIntensity(0.05f))
        provider.remoteCommands.emit(RemoteCommand.Pause)
        provider.remoteCommands.emit(RemoteCommand.Resume)
        provider.remoteCommands.emit(RemoteCommand.Stop)

        hubScope.runCurrent()

        assertEquals(1, control.clutchToggleCount)
        assertEquals(1, control.resumeManuallyCount)
        assertEquals(listOf(0.05f), control.intensityDeltas)
        assertEquals(1, control.pauseCount)
        assertEquals(1, control.resumeCount)
        assertEquals(1, control.stopCount)
    }

    @Test
    fun haptics_fanOutToConnectedHapticProvidersOnly() = runTest {
        val control = FakeWorkoutControl()
        val hapticProvider = FakeCompanionProvider(
            id = "p1",
            capabilities = setOf(CompanionCapability.HAPTIC_FEEDBACK)
        )
        val noHapticProvider = FakeCompanionProvider(
            id = "p2",
            capabilities = setOf(CompanionCapability.WORKOUT_MIRROR)
        )
        val disconnectedProvider = FakeCompanionProvider(
            id = "p3",
            capabilities = setOf(CompanionCapability.HAPTIC_FEEDBACK)
        ).apply { linkState.value = DeviceLinkState.DISCONNECTED }

        val sessionState = MutableStateFlow(WorkoutSessionState())
        val haptics = MutableSharedFlow<HapticAlertType>(extraBufferCapacity = 16)

        CompanionHub(
            providers = listOf(hapticProvider, noHapticProvider, disconnectedProvider),
            workoutControl = control,
            sessionState = sessionState,
            hapticAlerts = haptics,
            scope = hubScope
        )

        hubScope.runCurrent()

        haptics.emit(HapticAlertType.BAILOUT_TRIGGERED)
        hubScope.runCurrent()

        assertEquals(listOf(HapticAlertType.BAILOUT_TRIGGERED), hapticProvider.triggeredHaptics)
        assertTrue(noHapticProvider.triggeredHaptics.isEmpty())
        assertTrue(disconnectedProvider.triggeredHaptics.isEmpty())
    }

    @Test
    fun syncThrottling_throttlesSteadyState_andBurstsOnTransitions() = runTest {
        val control = FakeWorkoutControl()
        val provider = FakeCompanionProvider()
        var currentClock = 1000L
        val sessionState = MutableStateFlow(WorkoutSessionState(status = SessionStatus.RUNNING))
        val haptics = MutableSharedFlow<HapticAlertType>(extraBufferCapacity = 16)

        CompanionHub(
            providers = listOf(provider),
            workoutControl = control,
            sessionState = sessionState,
            hapticAlerts = haptics,
            scope = hubScope,
            clock = { currentClock },
            throttleWindowMs = 2000L
        )

        hubScope.runCurrent()
        assertEquals(1, provider.sentSnapshots.size) // First emission always sends

        // Steady tick at +1000ms (within 2000ms throttle window, no transition)
        currentClock += 1000L
        sessionState.value = sessionState.value.copy(
            elapsedSeconds = 1,
            latestTelemetry = BikeTelemetry(estimatedWatts = 200)
        )
        hubScope.runCurrent()
        assertEquals(1, provider.sentSnapshots.size) // Suppressed by throttle

        // Steady tick at +2100ms (exceeds 2000ms window)
        currentClock += 1100L
        sessionState.value = sessionState.value.copy(
            elapsedSeconds = 2,
            latestTelemetry = BikeTelemetry(estimatedWatts = 202)
        )
        hubScope.runCurrent()
        assertEquals(2, provider.sentSnapshots.size) // Dispatched

        // Immediate transition: Bailout triggers at +2200ms (100ms after last send)
        currentClock += 100L
        val bailoutDecision = ErgDecision(
            state = ErgState.CADENCE_FLOOR_BAILOUT,
            targetResistance = 8,
            shouldSendBleCommand = true,
            smoothedCadence = 50.0,
            nominalResistance = 8,
            trimOffset = 0,
            powerErrorWatts = 50
        )
        sessionState.value = sessionState.value.copy(
            ergDecision = bailoutDecision
        )
        hubScope.runCurrent()
        assertEquals(3, provider.sentSnapshots.size) // Burst immediately
        assertTrue(provider.sentSnapshots.last().isCadenceFloorActive)
    }
}

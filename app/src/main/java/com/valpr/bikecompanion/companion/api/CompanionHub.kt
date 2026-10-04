package com.valpr.bikecompanion.companion.api

import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Central hub coordinating companion device providers (e.g. Wear OS, Garmin, BLE remotes).
 *
 * Enforces:
 * 1. Inbound command routing from [CompanionDeviceProvider]s to [WorkoutControlPort].
 * 2. Outgoing haptic alert fan-out to providers supporting [CompanionCapability.HAPTIC_FEEDBACK].
 * 3. Outgoing state snapshot synchronization throttled to 2s steady-state, bursting
 *    immediately on safety or state transitions (AGENTS.md §5).
 */
class CompanionHub(
    val providers: List<CompanionDeviceProvider> = emptyList(),
    private val workoutControl: WorkoutControlPort,
    private val sessionState: StateFlow<WorkoutSessionState>,
    private val hapticAlerts: SharedFlow<HapticAlertType>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
    private val throttleWindowMs: Long = 2000L
) {
    private var lastSentMs: Long = 0L
    private var lastSnapshot: RemoteWorkoutSnapshot? = null
    private val providerJobs = mutableListOf<Job>()

    init {
        // Collect commands from all providers
        for (provider in providers) {
            val job = scope.launch {
                provider.remoteCommands.collect { command ->
                    dispatchRemoteCommand(command)
                }
            }
            providerJobs.add(job)

            val linkJob = scope.launch {
                provider.linkState.collect { linkState ->
                    if (linkState == DeviceLinkState.CONNECTED && sessionState.value.status != com.valpr.bikecompanion.workout.SessionStatus.IDLE) {
                        syncState(sessionState.value, force = true)
                    }
                }
            }
            providerJobs.add(linkJob)
        }

        // Fan out haptic alerts
        providerJobs.add(
            scope.launch {
                hapticAlerts.collect { alert ->
                    for (provider in providers) {
                        if (provider.capabilities.contains(CompanionCapability.HAPTIC_FEEDBACK) &&
                            provider.linkState.value == DeviceLinkState.CONNECTED
                        ) {
                            provider.triggerHaptic(alert)
                        }
                    }
                }
            }
        )

        // Throttled session state synchronization
        providerJobs.add(
            scope.launch {
                sessionState.collect { state ->
                    syncState(state)
                }
            }
        )
    }

    private suspend fun dispatchRemoteCommand(command: RemoteCommand) {
        when (command) {
            RemoteCommand.ToggleClutch -> workoutControl.toggleClutch()
            RemoteCommand.ResumeManually -> workoutControl.resumeManually()
            is RemoteCommand.AdjustIntensity -> workoutControl.adjustIntensity(command.deltaPercent)
            RemoteCommand.Pause -> workoutControl.pauseWorkout()
            RemoteCommand.Resume -> workoutControl.resumeWorkout()
            RemoteCommand.Stop -> workoutControl.stopWorkout()
            RemoteCommand.RequestSync -> syncState(sessionState.value, force = true)
        }
    }

    private suspend fun syncState(state: WorkoutSessionState, force: Boolean = false) {
        val connectedProviders = providers.filter {
            it.capabilities.contains(CompanionCapability.WORKOUT_MIRROR) &&
                it.linkState.value == DeviceLinkState.CONNECTED
        }
        if (connectedProviders.isEmpty()) {
            return
        }

        val snapshot = toSnapshot(state)
        val now = clock()
        val isTransition = force || shouldBurst(snapshot, lastSnapshot)

        if (isTransition || (now - lastSentMs) >= throttleWindowMs) {
            lastSentMs = now
            lastSnapshot = snapshot
            for (provider in connectedProviders) {
                provider.sendSnapshot(snapshot)
            }
        }
    }

    private fun shouldBurst(curr: RemoteWorkoutSnapshot, prev: RemoteWorkoutSnapshot?): Boolean {
        if (prev == null) return true
        return curr.status != prev.status ||
            curr.isBailoutActive != prev.isBailoutActive ||
            curr.isCadenceFloorActive != prev.isCadenceFloorActive ||
            curr.isHrCapped != prev.isHrCapped ||
            curr.targetWatts != prev.targetWatts ||
            curr.athleteMaxHr != prev.athleteMaxHr ||
            curr.athleteRestingHr != prev.athleteRestingHr ||
            curr.useKarvonenZones != prev.useKarvonenZones
    }

    fun onDestroy() {
        providerJobs.forEach { it.cancel() }
        providerJobs.clear()
    }

    companion object {
        fun toSnapshot(state: WorkoutSessionState): RemoteWorkoutSnapshot {
            val telem = state.latestTelemetry
            val ergDecision = state.ergDecision
            val isBailout = ergDecision?.state == ErgState.MANUAL_BAILOUT
            val isCadenceFloor = ergDecision?.state == ErgState.CADENCE_FLOOR_BAILOUT
            val isHrCapped = state.isCriticalHrActive || (ergDecision?.isHrCapped == true)

            return RemoteWorkoutSnapshot(
                status = state.status,
                elapsedSeconds = state.elapsedSeconds,
                totalSeconds = state.totalSeconds,
                currentWatts = telem.estimatedWatts,
                targetWatts = state.targetWatts,
                cadenceRpm = telem.cadenceRpm,
                targetCadenceRpm = state.targetCadence,
                resistanceLevel = telem.resistanceLevel,
                heartRateBpm = state.currentHeartRate,
                isBailoutActive = isBailout,
                isCadenceFloorActive = isCadenceFloor,
                isHrCapped = isHrCapped,
                workoutName = state.workout?.name ?: "Free Ride",
                athleteMaxHr = state.athleteMaxHr,
                athleteRestingHr = state.athleteRestingHr,
                useKarvonenZones = state.useKarvonenZones,
                intensityScale = state.intensityScale,
                activeCue = state.activeCues.firstOrNull()?.message
            )
        }
    }
}

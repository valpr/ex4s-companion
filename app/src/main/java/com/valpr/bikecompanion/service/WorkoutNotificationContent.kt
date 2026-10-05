package com.valpr.bikecompanion.service

import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.WorkoutSessionState

/**
 * Pure notification string builders extracted from [WorkoutTrackingService].
 *
 * The rider's only mid-ride readout when the phone is pocketed; kept
 * framework-free so the full title/content matrix is plain-JUnit testable.
 * Production call sites in the service delegate here unchanged.
 */
object WorkoutNotificationContent {

    fun buildTitle(session: WorkoutSessionState, connState: BleConnectionState): String {
        val telem = session.latestTelemetry
        val watts = session.displayWattsOrRaw
        return when {
            session.status == SessionStatus.STARTING -> {
                val countdown = session.countdownSeconds ?: 3
                val workoutName = session.workout?.name ?: "Free Ride"
                "$workoutName: Starting in ${countdown}s..."
            }
            session.status == SessionStatus.RUNNING -> {
                val workoutName = session.workout?.name ?: "Free Ride"
                val targetStr = session.targetWatts?.let { " (Target ${it}W)" } ?: ""
                "$workoutName: ${watts}W$targetStr"
            }
            session.status == SessionStatus.PAUSED -> {
                "Workout Paused: ${watts}W | ${telem.cadenceRpm} RPM"
            }
            connState is BleConnectionState.Connected -> {
                "EX-4S Connected: ${watts}W | ${telem.cadenceRpm} RPM"
            }
            connState is BleConnectionState.Connecting ->
                "Connecting to ${connState.deviceName}..."
            connState is BleConnectionState.Handshaking ->
                "Handshaking with EX-4S..."
            else -> "EX-4S Companion"
        }
    }

    fun ergSuffix(session: WorkoutSessionState): String = when (session.ergDecision?.state) {
        ErgState.CADENCE_FLOOR_BAILOUT -> " • BAILOUT (Spin >75)"
        ErgState.MANUAL_BAILOUT -> " • CLUTCH (Paused)"
        ErgState.ACTIVE -> " • ERG Active"
        else -> ""
    }

    /**
     * Session clock wins whenever a session exists (RUNNING/PAUSED/COMPLETED
     * all freeze or advance it deliberately). The bike telemetry clock is only
     * a fallback for IDLE, where no session time exists.
     */
    fun selectedTime(session: WorkoutSessionState): String = if (session.status == SessionStatus.IDLE || session.status == SessionStatus.STARTING) {
        session.latestTelemetry.formattedElapsedTime
    } else {
        session.formattedElapsedTime
    }

    fun buildContent(session: WorkoutSessionState): String {
        val telem = session.latestTelemetry
        val timeStr = selectedTime(session)
        val ergStateStr = ergSuffix(session)
        return "Cadence: ${telem.cadenceRpm} RPM • L${telem.resistanceLevel} • Time: $timeStr$ergStateStr"
    }
}

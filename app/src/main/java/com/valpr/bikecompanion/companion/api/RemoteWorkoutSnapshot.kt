package com.valpr.bikecompanion.companion.api

import com.valpr.bikecompanion.workout.SessionStatus

/**
 * Pure transport-agnostic snapshot of current workout telemetry and state
 * dispatched to companion devices (watches, external displays, heads-up remotes).
 */
data class RemoteWorkoutSnapshot(
    val status: SessionStatus,
    val elapsedSeconds: Int,
    val totalSeconds: Int,
    val currentWatts: Int,
    val targetWatts: Int?,
    val cadenceRpm: Int,
    val targetCadenceRpm: Int?,
    val resistanceLevel: Int,
    val heartRateBpm: Int,
    val isBailoutActive: Boolean,
    val isCadenceFloorActive: Boolean,
    val isHrCapped: Boolean,
    val workoutName: String,
    val athleteMaxHr: Int = 190,
    val athleteRestingHr: Int = 60,
    val useKarvonenZones: Boolean = false,
    val intensityScale: Float = 1.0f,
    val activeCue: String? = null
)

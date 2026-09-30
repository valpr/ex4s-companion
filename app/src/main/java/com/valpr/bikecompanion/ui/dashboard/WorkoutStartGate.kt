package com.valpr.bikecompanion.ui.dashboard

import com.valpr.bikecompanion.data.BleConnectionState

/**
 * Pure start gate: workouts and free rides require a live bike connection.
 * Framework-free so it stays plain-JUnit testable (AGENTS.md §8).
 */
object WorkoutStartGate {

    fun canStartWorkout(connectionState: BleConnectionState): Boolean = connectionState is BleConnectionState.Connected
}

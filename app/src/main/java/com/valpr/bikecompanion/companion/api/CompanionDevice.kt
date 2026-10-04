package com.valpr.bikecompanion.companion.api

import com.valpr.bikecompanion.shared.HapticAlertType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class DeviceLinkState {
    DISCONNECTED,
    SEARCHING,
    CONNECTED
}

enum class CompanionCapability {
    HEART_RATE,
    WORKOUT_MIRROR,
    ROTARY_INPUT,
    HAPTIC_FEEDBACK
}

sealed interface RemoteCommand {
    data object ToggleClutch : RemoteCommand
    data object ResumeManually : RemoteCommand
    data class AdjustIntensity(val deltaPercent: Float) : RemoteCommand
    data object Pause : RemoteCommand
    data object Resume : RemoteCommand
    data object Stop : RemoteCommand
    data object RequestSync : RemoteCommand
}

/**
 * Abstraction for an external companion device provider (e.g. Wear OS app,
 * Garmin Connect IQ app, BLE remote control).
 */
interface CompanionDeviceProvider {
    val id: String
    val displayName: String
    val linkState: StateFlow<DeviceLinkState>
    val capabilities: Set<CompanionCapability>
    val remoteCommands: SharedFlow<RemoteCommand>

    suspend fun sendSnapshot(snapshot: RemoteWorkoutSnapshot)
    suspend fun triggerHaptic(type: HapticAlertType)
}

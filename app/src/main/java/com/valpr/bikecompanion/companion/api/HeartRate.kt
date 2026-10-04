package com.valpr.bikecompanion.companion.api

import kotlinx.coroutines.flow.StateFlow

data class HrSample(
    val bpm: Int,
    val timestampEpochMs: Long,
    val deviceName: String? = null
)

enum class HrStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STALE
}

/**
 * Abstraction for any device or sensor providing athlete heart rate telemetry
 * (e.g. Wear OS Pixel Watch, BLE Heart Rate strap, Garmin Connect IQ).
 */
interface HeartRateSource {
    val id: String
    val displayName: String
    val hrSample: StateFlow<HrSample?>
    val hrStatus: StateFlow<HrStatus>
}

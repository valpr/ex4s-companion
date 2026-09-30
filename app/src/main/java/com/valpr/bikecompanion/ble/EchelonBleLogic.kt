package com.valpr.bikecompanion.ble

/**
 * Pure BLE scan-match and keep-alive logic for the EX-4S (framework-free,
 * plain-JUnit testable).
 *
 * [EchelonBleManager] delegates here. A broken matcher either bricks rides
 * (bike never auto-connects) or grabs a neighbor's device; a broken poll
 * counter wraps to 0/repeats and the bike drops the session.
 */
object EchelonBleLogic {

    const val POLL_COUNTER_MIN = 1
    const val POLL_COUNTER_MAX = 255

    /**
     * @param resolvedName post-fallback display name (never blank; "Unknown Device"
     *   or "Echelon EX-4S (Identified by UUID)" when nothing advertised).
     * @param hasEchelonServiceUuid true when the scan record carries the
     *   proprietary Echelon GATT service UUID.
     */
    fun isEchelonDevice(resolvedName: String, hasEchelonServiceUuid: Boolean): Boolean = resolvedName.contains("ECH", ignoreCase = true) ||
        resolvedName.contains("ECHELON", ignoreCase = true) ||
        resolvedName.contains("SPORT", ignoreCase = true) ||
        resolvedName.contains("EX-", ignoreCase = true) ||
        resolvedName.contains("EX4", ignoreCase = true) ||
        resolvedName.contains("EX5", ignoreCase = true) ||
        resolvedName.contains("EX3", ignoreCase = true) ||
        resolvedName.contains("BIKE", ignoreCase = true) ||
        hasEchelonServiceUuid

    /**
     * Next keep-alive counter after sending [current]. Wraps 255 -> 1;
     * out-of-range input resets to 1 so a corrupt counter can't stick at 0.
     */
    fun nextPollCounter(current: Int): Int {
        if (current < POLL_COUNTER_MIN || current >= POLL_COUNTER_MAX) return POLL_COUNTER_MIN
        return current + 1
    }
}

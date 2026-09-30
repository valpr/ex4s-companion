package com.valpr.bikecompanion.wearable

/**
 * Pure phone→watch sync-throttle decision (plain-JUnit testable).
 *
 * Steady telemetry is throttled to ~0.5Hz (2s); attention-requiring
 * transitions burst immediately. Breaking toward always-send streams the
 * radio at 1Hz (watch battery); breaking toward suppress hides bailout
 * (safety display failure).
 */
data class WearSyncKeys(
    val statusCode: Int,
    val isBailout: Boolean,
    val isCadenceFloor: Boolean,
    val isHrCapped: Boolean,
    val targetWatts: Int,
    val athleteMaxHr: Int
)

object WearSyncDecision {
    const val THROTTLE_WINDOW_MS = 2000L

    /**
     * @param last null on first send (always sync if a node is available).
     * @param nodeIdBlank true when no watch node is known → never sync.
     */
    fun shouldSync(
        nowMs: Long,
        lastSentMs: Long,
        current: WearSyncKeys,
        last: WearSyncKeys?,
        nodeIdBlank: Boolean
    ): Boolean {
        if (nodeIdBlank) return false
        if (last == null) return true
        val isTransition = current.statusCode != last.statusCode ||
            current.isBailout != last.isBailout ||
            current.isCadenceFloor != last.isCadenceFloor ||
            current.isHrCapped != last.isHrCapped ||
            current.targetWatts != last.targetWatts ||
            current.athleteMaxHr != last.athleteMaxHr
        if (isTransition) return true
        return nowMs - lastSentMs >= THROTTLE_WINDOW_MS
    }
}

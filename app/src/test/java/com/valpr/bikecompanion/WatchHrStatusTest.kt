package com.valpr.bikecompanion

import com.valpr.bikecompanion.wearable.PhoneWearableManager
import com.valpr.bikecompanion.wearable.WatchHrStatus
import com.valpr.bikecompanion.wearable.WearableWatchState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Watch HR freshness resolver: node reachability must never imply live data.
 * Plain JUnit, no Android framework (AGENTS.md §8).
 */
class WatchHrStatusTest {

    private fun state(
        connected: Boolean = true,
        bpm: Int = 100,
        timestampMs: Long = 10_000L
    ) = WearableWatchState(
        isConnected = connected,
        nodeName = "Pixel Watch 3",
        nodeId = "node-1",
        lastHeartRateBpm = bpm,
        lastHeartRateTimestampMs = timestampMs
    )

    @Test
    fun freshBatch_resolvesLive() {
        assertEquals(
            WatchHrStatus.LIVE,
            PhoneWearableManager.resolveWatchHrStatus(state(timestampMs = 10_000L), nowMs = 15_000L)
        )
    }

    @Test
    fun thresholdBoundary_isInclusive() {
        assertEquals(
            WatchHrStatus.LIVE,
            PhoneWearableManager.resolveWatchHrStatus(
                state(timestampMs = 10_000L),
                nowMs = 10_000L + PhoneWearableManager.HR_STALE_THRESHOLD_MS
            )
        )
    }

    @Test
    fun justOverThreshold_resolvesStale() {
        assertEquals(
            WatchHrStatus.STALE,
            PhoneWearableManager.resolveWatchHrStatus(
                state(timestampMs = 10_000L),
                nowMs = 10_000L + PhoneWearableManager.HR_STALE_THRESHOLD_MS + 1L
            )
        )
    }

    @Test
    fun disconnected_withFreshTimestamp_resolvesDisconnected() {
        assertEquals(
            WatchHrStatus.DISCONNECTED,
            PhoneWearableManager.resolveWatchHrStatus(
                state(connected = false, timestampMs = 10_000L),
                nowMs = 10_001L
            )
        )
    }

    @Test
    fun disconnected_withNoData_resolvesDisconnected() {
        assertEquals(
            WatchHrStatus.DISCONNECTED,
            PhoneWearableManager.resolveWatchHrStatus(
                state(connected = false, bpm = 0, timestampMs = 0L),
                nowMs = 99_999L
            )
        )
    }

    @Test
    fun connected_neverReceivedBatch_resolvesNoData() {
        assertEquals(
            WatchHrStatus.NO_DATA,
            PhoneWearableManager.resolveWatchHrStatus(
                state(bpm = 0, timestampMs = 0L),
                nowMs = 50_000L
            )
        )
    }

    @Test
    fun connected_zeroBpmWithTimestamp_resolvesNoData() {
        assertEquals(
            WatchHrStatus.NO_DATA,
            PhoneWearableManager.resolveWatchHrStatus(
                state(bpm = 0, timestampMs = 10_000L),
                nowMs = 11_000L
            )
        )
    }

    @Test
    fun futureTimestamp_clockSkew_resolvesLive() {
        assertEquals(
            WatchHrStatus.LIVE,
            PhoneWearableManager.resolveWatchHrStatus(
                state(timestampMs = 20_000L),
                nowMs = 10_000L
            )
        )
    }
}

package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.dashboard.WatchDashboardPresentation
import com.valpr.bikecompanion.ui.dashboard.WatchStatusTone
import com.valpr.bikecompanion.wearable.PhoneWearableManager
import com.valpr.bikecompanion.wearable.WearableWatchState
import com.valpr.bikecompanion.workout.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WatchDashboardPresentation].
 * Verifies standby gating, active telemetry evaluation, and stale-warning suppression.
 * Plain JUnit, no Android framework (AGENTS.md §8).
 */
class WatchDashboardPresentationTest {

    private fun state(
        connected: Boolean = true,
        nodeName: String = "Pixel Watch 3",
        bpm: Int = 142,
        timestampMs: Long = 10_000L
    ) = WearableWatchState(
        isConnected = connected,
        nodeName = nodeName,
        nodeId = "node-1",
        lastHeartRateBpm = bpm,
        lastHeartRateTimestampMs = timestampMs
    )

    @Test
    fun disconnected_showsWaitingForWatch_andDisablesTicker() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = false),
            sessionStatus = SessionStatus.IDLE,
            nowMs = 15_000L
        )

        assertEquals("Pixel Watch", model.title)
        assertEquals("Waiting for Watch", model.statusText)
        assertEquals(WatchStatusTone.MUTED, model.tone)
        assertFalse(model.needsTicker)
        assertFalse(model.isConnected)
    }

    @Test
    fun connected_idle_coldStart_showsReadyStandby() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, bpm = 0, timestampMs = 0L),
            sessionStatus = SessionStatus.IDLE,
            nowMs = 15_000L
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Ready • Standby", model.statusText)
        assertEquals(WatchStatusTone.READY, model.tone)
        assertFalse(model.needsTicker)
        assertTrue(model.isConnected)
    }

    @Test
    fun connected_idle_blankNodeName_fallsBackToPixelWatch() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, nodeName = "", bpm = 0, timestampMs = 0L),
            sessionStatus = SessionStatus.IDLE,
            nowMs = 15_000L
        )

        assertEquals("Pixel Watch", model.title)
        assertEquals("Ready • Standby", model.statusText)
    }

    @Test
    fun connected_idle_postWorkout_retainsOldHr_doesNotLeakStaleWarning() {
        // Telemetry is retained post-workout, but sensor is off: must present Standby Ready, not STALE.
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, bpm = 155, timestampMs = 10_000L),
            sessionStatus = SessionStatus.IDLE,
            nowMs = 60_000L // 50 seconds later, well past stale threshold
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Ready • Standby", model.statusText)
        assertEquals(WatchStatusTone.READY, model.tone)
        assertFalse(model.needsTicker)
        assertTrue(model.isConnected)
    }

    @Test
    fun connected_completed_showsReadyStandby() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, bpm = 160, timestampMs = 10_000L),
            sessionStatus = SessionStatus.COMPLETED,
            nowMs = 60_000L
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Ready • Standby", model.statusText)
        assertEquals(WatchStatusTone.READY, model.tone)
        assertFalse(model.needsTicker)
    }

    @Test
    fun connected_running_noHrBatchYet_showsAcquiringHr() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, bpm = 0, timestampMs = 0L),
            sessionStatus = SessionStatus.RUNNING,
            nowMs = 15_000L
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Acquiring HR…", model.statusText)
        assertEquals(WatchStatusTone.ACQUIRING, model.tone)
        assertFalse(model.needsTicker)
    }

    @Test
    fun connected_running_freshHrBatch_showsLiveBpm_andEnablesTicker() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, bpm = 142, timestampMs = 10_000L),
            sessionStatus = SessionStatus.RUNNING,
            nowMs = 10_000L + PhoneWearableManager.HR_STALE_THRESHOLD_MS // boundary inclusive
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Live HR: 142 BPM", model.statusText)
        assertEquals(WatchStatusTone.LIVE, model.tone)
        assertTrue(model.needsTicker)
    }

    @Test
    fun connected_running_staleHrBatch_showsStaleAge_andEnablesTicker() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, bpm = 142, timestampMs = 10_000L),
            sessionStatus = SessionStatus.RUNNING,
            nowMs = 25_000L // 15s ago
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("HR stale • 15s ago", model.statusText)
        assertEquals(WatchStatusTone.WARNING, model.tone)
        assertTrue(model.needsTicker)
    }

    @Test
    fun connected_paused_evaluatesFreshnessSameAsRunning() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true, bpm = 138, timestampMs = 10_000L),
            sessionStatus = SessionStatus.PAUSED,
            nowMs = 14_000L
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Live HR: 138 BPM", model.statusText)
        assertEquals(WatchStatusTone.LIVE, model.tone)
        assertTrue(model.needsTicker)
    }

    @Test
    fun connected_appMissing_showsWatchPairedAppMissing() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true).copy(isAppInstalled = false),
            sessionStatus = SessionStatus.IDLE,
            nowMs = 15_000L
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Watch Paired • App Missing", model.statusText)
        assertEquals(WatchStatusTone.WARNING, model.tone)
        assertFalse(model.needsTicker)
        assertTrue(model.isConnected)
    }

    @Test
    fun connected_pinging_showsPingingWatch() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true).copy(isPinging = true),
            sessionStatus = SessionStatus.IDLE,
            nowMs = 15_000L
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Pinging watch…", model.statusText)
        assertEquals(WatchStatusTone.ACQUIRING, model.tone)
        assertFalse(model.needsTicker)
    }

    @Test
    fun connected_pingVerified_showsVerifiedLatency() {
        val model = WatchDashboardPresentation.resolve(
            watchState = state(connected = true).copy(
                isPinging = false,
                lastPingRoundTripMs = 38L,
                pingStatusMessage = "Verified (38ms)"
            ),
            sessionStatus = SessionStatus.IDLE,
            nowMs = 15_000L
        )

        assertEquals("Pixel Watch 3", model.title)
        assertEquals("Verified (38ms)", model.statusText)
        assertEquals(WatchStatusTone.LIVE, model.tone)
        assertFalse(model.needsTicker)
    }
}

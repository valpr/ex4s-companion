package com.valpr.bikecompanion.wear

import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.service.WearWorkoutTrackingService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearWorkoutTrackingServiceTest {
    private fun buildState(status: Int) = WorkoutStateMessage(
        sessionStatus = status,
        elapsedSeconds = 120,
        targetWatts = 200,
        currentWatts = 202,
        cadenceRpm = 85,
        heartRateBpm = 145,
        isBailoutActive = false,
        isCadenceFloorActive = false,
        isHrCapped = false,
        workoutName = "Threshold Intervals"
    )

    @Test
    fun shouldStopTracking_nullState_returnsFalse() {
        // Crucial regression test: null state occurs when service starts before
        // first phone state packet is received. Must NOT trigger premature service teardown.
        assertFalse(
            "Null state must not stop tracking (uninitialized wait window)",
            WearWorkoutTrackingService.shouldStopTracking(null)
        )
    }

    @Test
    fun shouldStopTracking_runningAndPaused_returnsFalse() {
        assertFalse(
            "RUNNING state must keep tracking active",
            WearWorkoutTrackingService.shouldStopTracking(buildState(WorkoutStateMessage.STATUS_RUNNING))
        )
        assertFalse(
            "PAUSED state must keep tracking active",
            WearWorkoutTrackingService.shouldStopTracking(buildState(WorkoutStateMessage.STATUS_PAUSED))
        )
    }

    @Test
    fun shouldStopTracking_idleAndCompleted_returnsTrue() {
        assertTrue(
            "IDLE state must trigger service teardown",
            WearWorkoutTrackingService.shouldStopTracking(buildState(WorkoutStateMessage.STATUS_IDLE))
        )
        assertTrue(
            "COMPLETED state must trigger service teardown",
            WearWorkoutTrackingService.shouldStopTracking(buildState(WorkoutStateMessage.STATUS_COMPLETED))
        )
    }
}

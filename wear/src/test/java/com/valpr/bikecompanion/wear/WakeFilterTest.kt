package com.valpr.bikecompanion.wear

import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.messaging.WearMessageListenerService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeFilterTest {
    private fun state(
        status: Int = WorkoutStateMessage.STATUS_RUNNING,
        bailout: Boolean = false,
        floor: Boolean = false
    ) = WorkoutStateMessage(
        sessionStatus = status,
        elapsedSeconds = 60,
        targetWatts = 200,
        currentWatts = 195,
        cadenceRpm = 85,
        heartRateBpm = 140,
        isBailoutActive = bailout,
        isCadenceFloorActive = floor,
        isHrCapped = false,
        workoutName = "Test"
    ).toByteArray()

    @Test
    fun steadyRunningTelemetry_doesNotWake() {
        assertFalse(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state()
            )
        )
    }

    @Test
    fun bailoutFloorPausedCompleted_wake() {
        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(bailout = true)
            )
        )
        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(floor = true)
            )
        )
        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(status = WorkoutStateMessage.STATUS_PAUSED)
            )
        )
        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(status = WorkoutStateMessage.STATUS_COMPLETED)
            )
        )
    }

    @Test
    fun haptic_wakes_unknownDoesNot() {
        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_HAPTIC_TRIGGER,
                byteArrayOf(0x01)
            )
        )
        assertFalse(
            WearMessageListenerService.shouldWakeForMessage("/telemetry/hr", byteArrayOf(1, 2, 3))
        )
    }

    @Test
    fun resolveServiceAction_runningAndPaused_returnStart() {
        org.junit.Assert.assertEquals(
            WearMessageListenerService.ServiceAction.START,
            WearMessageListenerService.resolveServiceAction(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(status = WorkoutStateMessage.STATUS_RUNNING)
            )
        )
        org.junit.Assert.assertEquals(
            WearMessageListenerService.ServiceAction.START,
            WearMessageListenerService.resolveServiceAction(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(status = WorkoutStateMessage.STATUS_PAUSED)
            )
        )
    }

    @Test
    fun resolveServiceAction_idleAndCompleted_returnStop() {
        org.junit.Assert.assertEquals(
            WearMessageListenerService.ServiceAction.STOP,
            WearMessageListenerService.resolveServiceAction(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(status = WorkoutStateMessage.STATUS_IDLE)
            )
        )
        org.junit.Assert.assertEquals(
            WearMessageListenerService.ServiceAction.STOP,
            WearMessageListenerService.resolveServiceAction(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(status = WorkoutStateMessage.STATUS_COMPLETED)
            )
        )
    }

    @Test
    fun resolveServiceAction_otherPathsOrInvalidData_returnNone() {
        org.junit.Assert.assertEquals(
            WearMessageListenerService.ServiceAction.NONE,
            WearMessageListenerService.resolveServiceAction(
                WearableProtocol.PATH_HAPTIC_TRIGGER,
                byteArrayOf(0x01)
            )
        )
        org.junit.Assert.assertEquals(
            WearMessageListenerService.ServiceAction.NONE,
            WearMessageListenerService.resolveServiceAction(
                WearableProtocol.PATH_WORKOUT_STATE,
                byteArrayOf(1, 2, 3)
            )
        )
    }
}

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
    fun workoutStart_zeroElapsed_wakes() {
        val startMessage = WorkoutStateMessage(
            sessionStatus = WorkoutStateMessage.STATUS_RUNNING,
            elapsedSeconds = 0,
            targetWatts = 200,
            currentWatts = 0,
            cadenceRpm = 0,
            heartRateBpm = 0,
            isBailoutActive = false,
            isCadenceFloorActive = false,
            isHrCapped = false,
            workoutName = "New Ride"
        ).toByteArray()

        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                startMessage
            )
        )
    }

    @Test
    fun workoutStart_fromIdleState_wakes() {
        val idleState = WorkoutStateMessage(
            sessionStatus = WorkoutStateMessage.STATUS_IDLE,
            elapsedSeconds = 0,
            targetWatts = -1,
            currentWatts = 0,
            cadenceRpm = 0,
            heartRateBpm = 0,
            isBailoutActive = false,
            isCadenceFloorActive = false,
            isHrCapped = false,
            workoutName = ""
        )

        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(),
                previousState = idleState
            )
        )
    }

    @Test
    fun workoutResume_fromPaused_wakes() {
        val pausedState = WorkoutStateMessage(
            sessionStatus = WorkoutStateMessage.STATUS_PAUSED,
            elapsedSeconds = 60,
            targetWatts = 200,
            currentWatts = 0,
            cadenceRpm = 0,
            heartRateBpm = 130,
            isBailoutActive = false,
            isCadenceFloorActive = false,
            isHrCapped = false,
            workoutName = "Test"
        )

        assertTrue(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(),
                previousState = pausedState
            )
        )
    }

    @Test
    fun steadyRunningTelemetry_withRunningPreviousState_doesNotWake() {
        val runningState = WorkoutStateMessage(
            sessionStatus = WorkoutStateMessage.STATUS_RUNNING,
            elapsedSeconds = 58,
            targetWatts = 200,
            currentWatts = 195,
            cadenceRpm = 85,
            heartRateBpm = 140,
            isBailoutActive = false,
            isCadenceFloorActive = false,
            isHrCapped = false,
            workoutName = "Test"
        )

        assertFalse(
            WearMessageListenerService.shouldWakeForMessage(
                WearableProtocol.PATH_WORKOUT_STATE,
                state(),
                previousState = runningState
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

    private fun message(
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
    )

    @Test
    fun shouldPostBailoutAlert_entryOnly_notSteadyState() {
        val bailout = message(bailout = true)
        // First entry (no previous, or previous clean) posts.
        assertTrue(WearMessageListenerService.shouldPostBailoutAlert(bailout, null))
        assertTrue(
            WearMessageListenerService.shouldPostBailoutAlert(bailout, message())
        )
        // RepeatedTicks while already in bailout must not re-post.
        assertFalse(
            WearMessageListenerService.shouldPostBailoutAlert(bailout, message(bailout = true))
        )
        assertFalse(
            WearMessageListenerService.shouldPostBailoutAlert(bailout, message(floor = true))
        )
        // Clean state never posts.
        assertFalse(WearMessageListenerService.shouldPostBailoutAlert(message(), message(bailout = true)))
        assertFalse(WearMessageListenerService.shouldPostBailoutAlert(message(), null))
    }

    @Test
    fun shouldPostCompletion_entryOnly_notRepeated() {
        val completed = message(status = WorkoutStateMessage.STATUS_COMPLETED)
        assertTrue(WearMessageListenerService.shouldPostCompletion(completed, null))
        assertTrue(WearMessageListenerService.shouldPostCompletion(completed, message()))
        assertFalse(
            WearMessageListenerService.shouldPostCompletion(
                completed,
                message(status = WorkoutStateMessage.STATUS_COMPLETED)
            )
        )
        assertFalse(WearMessageListenerService.shouldPostCompletion(message(), null))
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

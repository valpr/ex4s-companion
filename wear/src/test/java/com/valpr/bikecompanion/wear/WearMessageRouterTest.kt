package com.valpr.bikecompanion.wear

import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.messaging.WearMessageRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearMessageRouterTest {
    private fun stateBytes(
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
    fun stateRoute_updates() {
        val action =
            WearMessageRouter.route(
                WearableProtocol.PATH_WORKOUT_STATE,
                stateBytes(bailout = true)
            )
        assertTrue(action is WearMessageRouter.Action.UpdateState)
        assertTrue((action as WearMessageRouter.Action.UpdateState).state.isBailoutActive)
    }

    @Test
    fun hapticRoute_plays() {
        val action =
            WearMessageRouter.route(
                WearableProtocol.PATH_HAPTIC_TRIGGER,
                HapticAlertType.BAILOUT_TRIGGERED.toByteArray()
            )
        assertEquals(
            WearMessageRouter.Action.PlayHaptic(HapticAlertType.BAILOUT_TRIGGERED),
            action
        )

        val completedAction =
            WearMessageRouter.route(
                WearableProtocol.PATH_HAPTIC_TRIGGER,
                HapticAlertType.WORKOUT_COMPLETED.toByteArray()
            )
        assertEquals(
            WearMessageRouter.Action.PlayHaptic(HapticAlertType.WORKOUT_COMPLETED),
            completedAction
        )
    }

    @Test
    fun corruptPayloads_ignored() {
        assertEquals(
            WearMessageRouter.Action.Ignore,
            WearMessageRouter.route(WearableProtocol.PATH_WORKOUT_STATE, byteArrayOf(0x00, 0x01))
        )
        assertEquals(
            WearMessageRouter.Action.Ignore,
            WearMessageRouter.route(WearableProtocol.PATH_HAPTIC_TRIGGER, byteArrayOf())
        )
        assertEquals(
            WearMessageRouter.Action.Ignore,
            WearMessageRouter.route(WearableProtocol.PATH_HAPTIC_TRIGGER, byteArrayOf(0x7F))
        )
    }

    @Test
    fun pingAndPongRoute_handled() {
        val pingBytes = com.valpr.bikecompanion.shared.PingPongMessage(98765L).toByteArray()
        val pingAction = WearMessageRouter.route(WearableProtocol.PATH_PING, pingBytes)
        assertTrue(pingAction is WearMessageRouter.Action.Ping)
        assertEquals(98765L, (pingAction as WearMessageRouter.Action.Ping).timestampMs)

        val pongAction = WearMessageRouter.route(WearableProtocol.PATH_PONG, pingBytes)
        assertTrue(pongAction is WearMessageRouter.Action.Pong)
        assertEquals(98765L, (pongAction as WearMessageRouter.Action.Pong).timestampMs)
    }

    @Test
    fun unknownPath_ignored() {
        assertEquals(
            WearMessageRouter.Action.Ignore,
            WearMessageRouter.route("/unknown/path", byteArrayOf(0x01))
        )
    }

    @Test
    fun canSend_guardsNullAndBlank() {
        assertFalse(WearMessageRouter.canSend(null))
        assertFalse(WearMessageRouter.canSend(""))
        assertFalse(WearMessageRouter.canSend("   "))
        assertTrue(WearMessageRouter.canSend("watch-node-1"))
    }

    @Test
    fun resolvePhoneLink_firstWins_emptyDisconnects() {
        val link =
            WearMessageRouter.resolvePhoneLink(
                listOf(
                    WearMessageRouter.PhoneNode("id-1", "Phone"),
                    WearMessageRouter.PhoneNode("id-2", "Other")
                )
            )
        assertTrue(link.isConnected)
        assertEquals("id-1", link.nodeId)

        val empty = WearMessageRouter.resolvePhoneLink(emptyList())
        assertFalse(empty.isConnected)
        assertEquals(null, empty.nodeId)
    }
}

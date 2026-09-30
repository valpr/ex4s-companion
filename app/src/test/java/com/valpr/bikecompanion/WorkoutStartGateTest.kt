package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.ui.dashboard.WorkoutStartGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStartGateTest {

    @Test
    fun connected_allowsStart() {
        assertTrue(
            WorkoutStartGate.canStartWorkout(
                BleConnectionState.Connected("EX-4S", "AA:BB:CC:DD:EE:FF")
            )
        )
    }

    @Test
    fun disconnected_blocksStart() {
        assertFalse(WorkoutStartGate.canStartWorkout(BleConnectionState.Disconnected))
    }

    @Test
    fun transitionalStates_blockStart() {
        assertFalse(WorkoutStartGate.canStartWorkout(BleConnectionState.Scanning))
        assertFalse(
            WorkoutStartGate.canStartWorkout(
                BleConnectionState.Connecting("EX-4S", "AA:BB:CC:DD:EE:FF")
            )
        )
        assertFalse(WorkoutStartGate.canStartWorkout(BleConnectionState.DiscoveringServices))
        assertFalse(WorkoutStartGate.canStartWorkout(BleConnectionState.Handshaking))
        assertFalse(WorkoutStartGate.canStartWorkout(BleConnectionState.Disconnecting()))
        assertFalse(WorkoutStartGate.canStartWorkout(BleConnectionState.Error("boom")))
    }
}

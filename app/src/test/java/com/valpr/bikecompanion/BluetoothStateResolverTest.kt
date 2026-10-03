package com.valpr.bikecompanion

import android.bluetooth.BluetoothAdapter
import com.valpr.bikecompanion.ble.BluetoothStateReceiver
import com.valpr.bikecompanion.ble.BluetoothStateResolver
import com.valpr.bikecompanion.data.BleConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure Bluetooth on/off mapping stays JVM-testable: no receivers, adapters,
 * or contexts here — only action strings and state-extra ints.
 */
class BluetoothStateResolverTest {

    @Test
    fun stateOnResolvesToEnabled() {
        assertEquals(
            true,
            BluetoothStateResolver.resolveEnabled(
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.STATE_ON
            )
        )
    }

    @Test
    fun stateOffAndTurningOffResolveToDisabled() {
        assertEquals(
            false,
            BluetoothStateResolver.resolveEnabled(
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.STATE_OFF
            )
        )
        assertEquals(
            false,
            BluetoothStateResolver.resolveEnabled(
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.STATE_TURNING_OFF
            )
        )
    }

    @Test
    fun transientAndUnknownExtrasResolveToNull() {
        assertNull(
            BluetoothStateResolver.resolveEnabled(
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.STATE_TURNING_ON
            )
        )
        assertNull(
            BluetoothStateResolver.resolveEnabled(
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.ERROR
            )
        )
    }

    @Test
    fun unrelatedOrNullActionsResolveToNull() {
        assertNull(
            BluetoothStateResolver.resolveEnabled("android.bluetooth.device.action.FOUND", BluetoothAdapter.STATE_ON)
        )
        assertNull(BluetoothStateResolver.resolveEnabled(null, BluetoothAdapter.STATE_ON))
    }

    @Test
    fun disabledMessageRoundTrips() {
        assertTrue(BluetoothStateResolver.isBluetoothDisabledMessage(BluetoothStateResolver.DISABLED_MESSAGE))
        assertFalse(BluetoothStateResolver.isBluetoothDisabledMessage("Scan failed: error 2"))
        assertFalse(BluetoothStateResolver.isBluetoothDisabledMessage(null))
    }

    @Test
    fun disabledErrorMatchesOnlyExactDisabledError() {
        assertTrue(
            BluetoothStateResolver.isBluetoothDisabledError(
                BleConnectionState.Error(BluetoothStateResolver.DISABLED_MESSAGE)
            )
        )
        assertFalse(
            BluetoothStateResolver.isBluetoothDisabledError(
                BleConnectionState.Error("Scan failed: error 2")
            )
        )
        assertFalse(BluetoothStateResolver.isBluetoothDisabledError(BleConnectionState.Disconnected))
        assertFalse(
            BluetoothStateResolver.isBluetoothDisabledError(
                BleConnectionState.Connected("Bike", "AA:BB:CC:DD:EE:FF")
            )
        )
    }

    @Test
    fun receiverHandlesOnlyAdapterStateChanged() {
        assertTrue(BluetoothStateReceiver.shouldHandleAction(BluetoothAdapter.ACTION_STATE_CHANGED))
        assertFalse(BluetoothStateReceiver.shouldHandleAction("android.bluetooth.device.action.FOUND"))
        assertFalse(BluetoothStateReceiver.shouldHandleAction(null))
    }
}

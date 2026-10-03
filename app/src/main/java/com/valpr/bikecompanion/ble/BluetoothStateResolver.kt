package com.valpr.bikecompanion.ble

import android.bluetooth.BluetoothAdapter
import com.valpr.bikecompanion.data.BleConnectionState

/**
 * Pure Bluetooth-adapter state helpers. Framework-free so it stays plain-JUnit
 * (see AGENTS.md §8): no receiver/adapter access here, only values.
 */
object BluetoothStateResolver {

    const val DISABLED_MESSAGE = "Bluetooth is disabled. Please turn on Bluetooth."

    /**
     * Maps an `ACTION_STATE_CHANGED` broadcast to an enabled flag.
     * Returns null for unrelated actions or transient/unknown extras
     * (TURNING_ON, ERROR) so the UI doesn't flap mid-transition.
     */
    fun resolveEnabled(action: String?, stateExtra: Int): Boolean? {
        if (action != BluetoothAdapter.ACTION_STATE_CHANGED) return null
        return when (stateExtra) {
            BluetoothAdapter.STATE_ON -> true
            BluetoothAdapter.STATE_OFF,
            BluetoothAdapter.STATE_TURNING_OFF -> false
            else -> null
        }
    }

    fun isBluetoothDisabledMessage(message: String?): Boolean = message == DISABLED_MESSAGE

    fun isBluetoothDisabledError(state: BleConnectionState): Boolean = state is BleConnectionState.Error && state.message == DISABLED_MESSAGE
}

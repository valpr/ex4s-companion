package com.valpr.bikecompanion.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Forwards phone Bluetooth on/off transitions to [onChanged].
 * Plain `workout/state`-style telemetry must not wake UI; this receiver only
 * fires on adapter state changes, which always deserve a banner update.
 */
class BluetoothStateReceiver(
    private val onChanged: (Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandleAction(intent.action)) return
        val enabled = BluetoothStateResolver.resolveEnabled(
            intent.action,
            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        ) ?: return
        onChanged(enabled)
    }

    companion object {
        fun intentFilter(): IntentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)

        /** Pure action filter (JVM-testable): only adapter state changes wake us. */
        fun shouldHandleAction(action: String?): Boolean = action == BluetoothAdapter.ACTION_STATE_CHANGED
    }
}

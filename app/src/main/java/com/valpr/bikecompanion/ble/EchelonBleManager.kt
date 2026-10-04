package com.valpr.bikecompanion.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.valpr.bikecompanion.bike.ble.BleBikeConnection
import com.valpr.bikecompanion.bike.echelon.EchelonDriver

/**
 * Concrete Echelon EX-4S BLE manager.
 * Subclasses [BleBikeConnection] configured with [EchelonDriver].
 */
class EchelonBleManager(
    context: Context,
    bluetoothAdapter: BluetoothAdapter?
) : BleBikeConnection(context, bluetoothAdapter, listOf(EchelonDriver()))

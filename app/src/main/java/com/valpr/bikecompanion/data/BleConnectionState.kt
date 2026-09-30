package com.valpr.bikecompanion.data

import android.bluetooth.BluetoothDevice

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object Scanning : BleConnectionState
    data class Connecting(val deviceName: String, val address: String) : BleConnectionState
    data object DiscoveringServices : BleConnectionState
    data object Handshaking : BleConnectionState
    data class Connected(val deviceName: String, val address: String) : BleConnectionState
    data class Disconnecting(val reason: String = "") : BleConnectionState
    data class Error(val message: String, val cause: Throwable? = null) : BleConnectionState
}

data class DiscoveredBikeDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val isEchelonDevice: Boolean = false
)

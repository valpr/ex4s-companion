package com.valpr.bikecompanion.bike.api

import com.valpr.bikecompanion.ble.PacketLogRecorder
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.data.DiscoveredBikeDevice
import kotlinx.coroutines.flow.StateFlow

interface BikeController {
    val connectionState: StateFlow<BleConnectionState>
    val telemetry: StateFlow<BikeTelemetry>
    val discoveredDevices: StateFlow<List<DiscoveredBikeDevice>>
    val capabilities: StateFlow<BikeCapabilities>
    val lastError: StateFlow<String?>
    val isBluetoothEnabled: StateFlow<Boolean>
    val hasBluetoothAdapter: Boolean
    var autoConnect: Boolean
    val packetLogRecorder: PacketLogRecorder?

    fun startScan()
    fun stopScan()
    fun connect(device: DiscoveredBikeDevice)
    fun connect(device: android.bluetooth.BluetoothDevice, driverId: String? = null)
    fun connectToMac(mac: String, driverId: String? = null): Result<Unit>
    fun disconnect()
    fun refreshBluetoothState()
    fun clearLastError()
    fun setResistance(level: Int)
    fun setTargetPower(watts: Int) {}
    fun onDestroy()
}

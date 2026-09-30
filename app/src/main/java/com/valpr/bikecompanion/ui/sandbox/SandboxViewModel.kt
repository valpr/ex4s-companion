package com.valpr.bikecompanion.ui.sandbox

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.data.DiscoveredBikeDevice
import com.valpr.bikecompanion.data.PacketLogEntry
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.valpr.bikecompanion.service.WorkoutTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SandboxViewModel(application: Application) : AndroidViewModel(application) {

    private val bikeApp = application as BikeApplication
    private val bleManager = bikeApp.bleManager

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val telemetry: StateFlow<BikeTelemetry> = bleManager.telemetry
    val discoveredDevices: StateFlow<List<DiscoveredBikeDevice>> = bleManager.discoveredDevices

    private val _packetLogs = MutableStateFlow<List<PacketLogEntry>>(emptyList())
    val packetLogs: StateFlow<List<PacketLogEntry>> = _packetLogs.asStateFlow()

    private val _isScanDialogOpen = ScanDialogController()
    val isScanDialogOpen: StateFlow<Boolean> = _isScanDialogOpen.isOpen

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    init {
        // Collect incoming packet logs from BLE manager
        viewModelScope.launch {
            bleManager.packetLog.collect { entry ->
                _packetLogs.update { current ->
                    (current + entry).takeLast(200)
                }
            }
        }
    }

    fun startAutoScan() {
        if (SandboxScanPolicy.shouldAutoStartScan(connectionState.value)) {
            bleManager.startScan()
        }
    }

    fun openScanDialog() {
        _isScanDialogOpen.open()
        startScan()
    }

    fun closeScanDialog() {
        stopScan()
        _isScanDialogOpen.close()
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connectDevice(device: DiscoveredBikeDevice) {
        closeScanDialog()
        bleManager.connect(device.device)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun setResistance(level: Int) {
        bleManager.setResistance(level)
    }

    fun clearLogs() {
        _packetLogs.value = emptyList()
    }

    fun toggleForegroundService(context: Context, onRequestPermissions: () -> Unit = {}) {
        val hasBtPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!_isServiceActive.value && !hasBtPermission) {
            Toast.makeText(
                context,
                "Please grant Bluetooth permissions before starting background tracking",
                Toast.LENGTH_SHORT
            ).show()
            onRequestPermissions()
            return
        }

        if (_isServiceActive.value) {
            WorkoutTrackingService.stopService(context)
            _isServiceActive.value = false
        } else {
            WorkoutTrackingService.startService(context)
            _isServiceActive.value = true
        }
    }
}

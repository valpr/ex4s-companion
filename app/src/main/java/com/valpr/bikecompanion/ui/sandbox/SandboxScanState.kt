package com.valpr.bikecompanion.ui.sandbox

import com.valpr.bikecompanion.data.BleConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scan-dialog open/close state (framework-free, plain-JUnit testable).
 *
 * Locks the leak guard: closing the dialog always pairs with `stopScan`,
 * and connecting always routes through close. The BLE-touching calls stay
 * in the ViewModel; this owns only the flag transitions.
 */
class ScanDialogController {

    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    fun open() {
        _isOpen.value = true
    }

    fun close() {
        _isOpen.value = false
    }
}

/**
 * Pure auto-scan gate: only scan from a fully disconnected state, never
 * while connecting/connected/scanning (avoids duplicate scan sessions).
 */
object SandboxScanPolicy {

    fun shouldAutoStartScan(connectionState: BleConnectionState): Boolean =
        connectionState is BleConnectionState.Disconnected
}

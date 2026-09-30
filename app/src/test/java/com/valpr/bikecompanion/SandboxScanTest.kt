package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.ui.sandbox.SandboxScanPolicy
import com.valpr.bikecompanion.ui.sandbox.ScanDialogController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxScanTest {

    @Test
    fun dialog_openCloseTransitions() {
        val controller = ScanDialogController()
        assertFalse(controller.isOpen.value)

        controller.open()
        assertTrue(controller.isOpen.value)

        // Idempotent open: stays open, no toggle.
        controller.open()
        assertTrue(controller.isOpen.value)

        controller.close()
        assertFalse(controller.isOpen.value)

        // Idempotent close.
        controller.close()
        assertFalse(controller.isOpen.value)
    }

    @Test
    fun dialog_reopenAfterConnectFlow() {
        // Models connectDevice -> closeScanDialog, then a fresh openScanDialog.
        val controller = ScanDialogController()
        controller.open()
        controller.close()
        assertFalse(controller.isOpen.value)
        controller.open()
        assertEquals(true, controller.isOpen.value)
    }

    @Test
    fun policy_onlyDisconnectedAutoStartsScan() {
        assertTrue(
            SandboxScanPolicy.shouldAutoStartScan(BleConnectionState.Disconnected)
        )
        assertFalse(
            SandboxScanPolicy.shouldAutoStartScan(BleConnectionState.Scanning)
        )
        assertFalse(
            SandboxScanPolicy.shouldAutoStartScan(
                BleConnectionState.Connecting("EX-4S", "AA:BB:CC:DD:EE:FF")
            )
        )
        assertFalse(
            SandboxScanPolicy.shouldAutoStartScan(BleConnectionState.Handshaking)
        )
        assertFalse(
            SandboxScanPolicy.shouldAutoStartScan(
                BleConnectionState.Connected("EX-4S", "AA:BB:CC:DD:EE:FF")
            )
        )
        assertFalse(
            SandboxScanPolicy.shouldAutoStartScan(BleConnectionState.DiscoveringServices)
        )
        assertFalse(
            SandboxScanPolicy.shouldAutoStartScan(BleConnectionState.Error("boom"))
        )
    }
}

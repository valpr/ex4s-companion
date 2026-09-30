package com.valpr.bikecompanion.ble

import java.util.UUID

object EchelonGattAttributes {
    // Echelon Proprietary 128-bit BLE GATT UUIDs
    val SERVICE_ECHELON: UUID = UUID.fromString("0bf669f1-45f2-11e7-9598-0800200c9a66")
    val CHAR_WRITE: UUID = UUID.fromString("0bf669f2-45f2-11e7-9598-0800200c9a66")
    val CHAR_NOTIFY_1: UUID = UUID.fromString("0bf669f3-45f2-11e7-9598-0800200c9a66")
    val CHAR_NOTIFY_2: UUID = UUID.fromString("0bf669f4-45f2-11e7-9598-0800200c9a66")

    // Standard BLE Client Characteristic Configuration Descriptor (CCCD)
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Device advertising prefix filter
    const val DEVICE_NAME_PREFIX = "ECH"
}

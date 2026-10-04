package com.valpr.bikecompanion

import com.valpr.bikecompanion.bike.api.ParseResult
import com.valpr.bikecompanion.bike.echelon.EchelonBikeProtocol
import com.valpr.bikecompanion.ble.EchelonGattAttributes
import com.valpr.bikecompanion.data.BikeTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchelonBikeProtocolTest {

    private val protocol = EchelonBikeProtocol()

    @Test
    fun attributesAndCapabilities_matchEx4sSpecs() {
        assertEquals(EchelonGattAttributes.SERVICE_ECHELON, protocol.serviceUuid)
        assertEquals(EchelonGattAttributes.CHAR_WRITE, protocol.writeCharacteristic)
        assertEquals(2, protocol.notifyCharacteristics.size)
        assertEquals(1..32, protocol.capabilities.resistanceRange)
        assertEquals("Echelon EX-4S", protocol.capabilities.modelName)
    }

    @Test
    fun createHandshake_returnsSevenPackets() {
        val handshake = protocol.createHandshake()
        assertEquals(7, handshake.size)
        assertTrue(handshake.all { it.characteristicUuid == protocol.writeCharacteristic })
    }

    @Test
    fun createKeepAlive_producesPollPacket() {
        val packet = protocol.createKeepAlive(5)
        assertEquals(protocol.writeCharacteristic, packet.characteristicUuid)
        assertEquals(0xF0.toByte(), packet.data[0])
        assertEquals(0xA0.toByte(), packet.data[1])
        assertEquals(0x01.toByte(), packet.data[2])
        assertEquals(5.toByte(), packet.data[3])
    }

    @Test
    fun createResistanceCommand_clampsToRange() {
        val under = protocol.createResistanceCommand(0)
        assertEquals(1.toByte(), under.data[3])

        val over = protocol.createResistanceCommand(40)
        assertEquals(32.toByte(), over.data[3])

        val valid = protocol.createResistanceCommand(15)
        assertEquals(15.toByte(), valid.data[3])
    }

    @Test
    fun parseNotification_cadenceFrame_updatesTelemetry() {
        val cadencePacket = ByteArray(13).apply {
            this[0] = 0xF0.toByte()
            this[1] = 0xD1.toByte()
            this[2] = 0x00.toByte()
            this[3] = 0x00.toByte() // elapsed high
            this[4] = 60.toByte() // elapsed low (60s)
            this[5] = 0x00.toByte()
            this[6] = 0x00.toByte()
            this[7] = 0x00.toByte() // dist high
            this[8] = 100.toByte() // dist low (100 -> 1.00 km)
            this[9] = 0x00.toByte()
            this[10] = 85.toByte() // cadence (85 RPM)
            this[11] = 0x00.toByte()
            this[12] = 0x00.toByte()
        }

        val result = protocol.parseNotification(
            EchelonGattAttributes.CHAR_NOTIFY_1,
            cadencePacket,
            BikeTelemetry(resistanceLevel = 10)
        )

        assertTrue(result is ParseResult.TelemetryUpdate)
        val update = result as ParseResult.TelemetryUpdate
        assertEquals(85, update.rawCadenceRpm)

        val updatedTelemetry = update.update(BikeTelemetry(resistanceLevel = 10))
        assertEquals(85, updatedTelemetry.cadenceRpm)
        assertEquals(1.0, updatedTelemetry.distanceKm, 0.01)
        assertEquals(0.37497622 * 85.0, updatedTelemetry.speedKmh, 0.01)
        assertEquals(60, updatedTelemetry.elapsedSeconds)
        assertTrue(updatedTelemetry.estimatedWatts > 0)
    }

    @Test
    fun parseNotification_resistanceFrame_updatesResistanceAndWatts() {
        // Frame: [F0, D2, 01, 15 (res 21), checksum]
        val resPacket = byteArrayOf(
            0xF0.toByte(),
            0xD2.toByte(),
            0x01.toByte(),
            21.toByte(),
            0x00.toByte()
        )

        val result = protocol.parseNotification(
            EchelonGattAttributes.CHAR_NOTIFY_2,
            resPacket,
            BikeTelemetry(cadenceRpm = 80)
        )

        assertTrue(result is ParseResult.TelemetryUpdate)
        val update = result as ParseResult.TelemetryUpdate
        assertEquals(21, update.rawResistance)

        val updatedTelemetry = update.update(BikeTelemetry(cadenceRpm = 80))
        assertEquals(21, updatedTelemetry.resistanceLevel)
        assertTrue(updatedTelemetry.estimatedWatts > 0)
    }

    @Test
    fun parseNotification_lockedFrame_returnsWarning() {
        val lockedPacket = byteArrayOf(
            0xF0.toByte(),
            0xE0.toByte(),
            0x01.toByte(),
            0x02.toByte(),
            0x03.toByte(),
            0x04.toByte(),
            0x05.toByte(),
            0x06.toByte()
        )
        val result = protocol.parseNotification(
            EchelonGattAttributes.CHAR_NOTIFY_1,
            lockedPacket,
            BikeTelemetry()
        )
        assertTrue(result is ParseResult.LockedWarning)
    }
}

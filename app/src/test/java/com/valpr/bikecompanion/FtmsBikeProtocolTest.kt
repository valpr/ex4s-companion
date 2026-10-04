package com.valpr.bikecompanion

import com.valpr.bikecompanion.bike.api.ParseResult
import com.valpr.bikecompanion.bike.ftms.FtmsBikeProtocol
import com.valpr.bikecompanion.data.BikeTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FtmsBikeProtocolTest {

    private val protocol = FtmsBikeProtocol()

    @Test
    fun constants_conformToFtmsSigSpecification() {
        assertEquals(UUID.fromString("00001826-0000-1000-8000-00805f9b34fb"), protocol.serviceUuid)
        assertEquals(FtmsBikeProtocol.CONTROL_POINT_UUID, protocol.writeCharacteristic)
        assertTrue(protocol.notifyCharacteristics.contains(FtmsBikeProtocol.INDOOR_BIKE_DATA_UUID))
        assertTrue(protocol.indicateCharacteristics.contains(FtmsBikeProtocol.CONTROL_POINT_UUID))
        assertTrue(protocol.capabilities.supportsNativeErg)
        assertTrue(protocol.capabilities.reportsMeasuredPower)
    }

    @Test
    fun createHandshake_containsRequestControlResetAndStart() {
        val packets = protocol.createHandshake()
        assertEquals(3, packets.size)

        assertEquals(FtmsBikeProtocol.CONTROL_POINT_UUID, packets[0].characteristicUuid)
        assertEquals(byteArrayOf(FtmsBikeProtocol.OPCODE_REQUEST_CONTROL).toList(), packets[0].data.toList())

        assertEquals(FtmsBikeProtocol.CONTROL_POINT_UUID, packets[1].characteristicUuid)
        assertEquals(byteArrayOf(FtmsBikeProtocol.OPCODE_RESET).toList(), packets[1].data.toList())

        assertEquals(FtmsBikeProtocol.CONTROL_POINT_UUID, packets[2].characteristicUuid)
        assertEquals(byteArrayOf(FtmsBikeProtocol.OPCODE_START_OR_RESUME).toList(), packets[2].data.toList())
    }

    @Test
    fun createResistanceCommand_formatsOpcode0x04WithClampedLevel() {
        val packet = protocol.createResistanceCommand(15)
        assertEquals(FtmsBikeProtocol.CONTROL_POINT_UUID, packet.characteristicUuid)
        assertEquals(listOf(0x04.toByte(), 15.toByte()), packet.data.toList())

        // Clamping to 1..100
        val clampedPacket = protocol.createResistanceCommand(150)
        assertEquals(listOf(0x04.toByte(), 100.toByte()), clampedPacket.data.toList())
    }

    @Test
    fun createTargetPowerCommand_formatsOpcode0x05LittleEndianWatts() {
        val packet = protocol.createTargetPowerCommand(250) // 0x00FA -> FA 00
        assertEquals(FtmsBikeProtocol.CONTROL_POINT_UUID, packet.characteristicUuid)
        assertEquals(listOf(0x05.toByte(), 0xFA.toByte(), 0x00.toByte()), packet.data.toList())

        val highPowerPacket = protocol.createTargetPowerCommand(400) // 0x0190 -> 90 01
        assertEquals(listOf(0x05.toByte(), 0x90.toByte(), 0x01.toByte()), highPowerPacket.data.toList())
    }

    @Test
    fun parseNotification_updatesTelemetryFromValidIndoorBikeData() {
        // Flags: 0x0064 (Speed, Cadence, Resistance, Power)
        val payload = byteArrayOf(
            0x64, 0x00,
            0xB8.toByte(), 0x0B, // Speed: 30.0 km/h
            0xB4.toByte(), 0x00, // Cadence: 90 rpm
            0x12, 0x00, // Resistance: 18
            0xF4.toByte(), 0x00 // Power: 244 W
        )

        val initial = BikeTelemetry()
        val result = protocol.parseNotification(FtmsBikeProtocol.INDOOR_BIKE_DATA_UUID, payload, initial)

        assertTrue(result is ParseResult.TelemetryUpdate)
        val update = result as ParseResult.TelemetryUpdate
        val updated = update.update(initial)

        assertEquals(90, updated.cadenceRpm)
        assertEquals(244, updated.estimatedWatts)
        assertEquals(18, updated.resistanceLevel)
        assertEquals(30.0, updated.speedKmh, 0.001)
    }

    @Test
    fun parseNotification_unknownCharacteristic_ignored() {
        val randomUuid = UUID.randomUUID()
        val result = protocol.parseNotification(randomUuid, byteArrayOf(0x00), BikeTelemetry())
        assertTrue(result is ParseResult.Ignored)
    }
}

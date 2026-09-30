package com.valpr.bikecompanion

import com.valpr.bikecompanion.ble.EchelonPacketParser
import com.valpr.bikecompanion.ble.ParsedPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchelonPacketParserTest {

    @Test
    fun testParseResistanceFrame() {
        // Opcode 0xD2: byte[3] is resistance level (e.g. 18 = 0x12)
        val packet = byteArrayOf(
            0xF0.toByte(),
            0xD2.toByte(),
            0x01.toByte(),
            18.toByte(),
            0x00.toByte()
        )

        val result = EchelonPacketParser.parse(packet)
        assertTrue(result is ParsedPacket.ResistanceFrame)
        val resistanceFrame = result as ParsedPacket.ResistanceFrame
        assertEquals(18, resistanceFrame.resistanceLevel)
    }

    @Test
    fun testParseCadenceFrame() {
        // Opcode 0xD1: 13-byte frame
        // byte[3..4] = elapsedSeconds (e.g. 125 seconds = 0x00, 0x7D)
        // byte[7..8] = distance * 100 (e.g. 2.45 km = 245 = 0x00, 0xF5)
        // byte[10] = cadence RPM (e.g. 88 RPM = 0x58)
        val packet = ByteArray(13).apply {
            this[0] = 0xF0.toByte()
            this[1] = 0xD1.toByte()
            this[2] = 0x00.toByte()
            this[3] = 0x00.toByte() // elapsed high
            this[4] = 0x7D.toByte() // elapsed low (125)
            this[5] = 0x00.toByte()
            this[6] = 0x00.toByte()
            this[7] = 0x00.toByte() // distance high
            this[8] = 0xF5.toByte() // distance low (245 -> 2.45 km)
            this[9] = 0x00.toByte()
            this[10] = 88.toByte()  // cadence (88 RPM)
            this[11] = 0x00.toByte()
            this[12] = 0x00.toByte()
        }

        val result = EchelonPacketParser.parse(packet)
        assertTrue(result is ParsedPacket.CadenceFrame)
        val cadenceFrame = result as ParsedPacket.CadenceFrame
        assertEquals(88, cadenceFrame.cadenceRpm)
        assertEquals(125, cadenceFrame.elapsedSeconds)
        assertEquals(2.45, cadenceFrame.distanceKm, 0.001)
        assertEquals(0.37497622 * 88.0, cadenceFrame.speedKmh, 0.001)
    }

    @Test
    fun testParseLockedBikeFrame() {
        val lockFrame = byteArrayOf(
            0xF0.toByte(),
            0xE0.toByte(),
            0x01.toByte(),
            0x02.toByte(),
            0x03.toByte(),
            0x04.toByte(),
            0x05.toByte(),
            0x06.toByte()
        )
        val result = EchelonPacketParser.parse(lockFrame)
        assertTrue(result is ParsedPacket.LockedBikeFrame)

        val challengeResponseFrame = byteArrayOf(
            0xF0.toByte(),
            0xA5.toByte(),
            0x01.toByte(),
            0x0E.toByte(),
            0xA4.toByte()
        )
        val result2 = EchelonPacketParser.parse(challengeResponseFrame)
        assertTrue(result2 is ParsedPacket.LockedBikeFrame)
    }

    @Test
    fun testMalformedOrEmptyPacketsHandledSafely() {
        val emptyResult = EchelonPacketParser.parse(byteArrayOf())
        assertTrue(emptyResult is ParsedPacket.UnknownFrame)

        val nonF0Result = EchelonPacketParser.parse(byteArrayOf(0x01, 0x02, 0x03))
        assertTrue(nonF0Result is ParsedPacket.UnknownFrame)
    }
}

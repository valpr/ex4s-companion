package com.valpr.bikecompanion

import com.valpr.bikecompanion.bike.ftms.FtmsIndoorBikeDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FtmsIndoorBikeDataParserTest {

    @Test
    fun parse_emptyOrTooShort_returnsNull() {
        assertNull(FtmsIndoorBikeDataParser.parse(byteArrayOf()))
        assertNull(FtmsIndoorBikeDataParser.parse(byteArrayOf(0x00)))
    }

    @Test
    fun parse_speedOnly_whenMoreDataBitIsZero() {
        // Flags: 0x0000 -> More Data is 0, so Instantaneous Speed is present (uint16, 0.01 km/h)
        // Speed: 2500 -> 25.00 km/h (0x09C4 -> C4 09)
        val data = byteArrayOf(0x00, 0x00, 0xC4.toByte(), 0x09)
        val result = FtmsIndoorBikeDataParser.parse(data)

        assertNotNull(result)
        assertEquals(25.00, result!!.speedKmh!!, 0.001)
        assertNull(result.cadenceRpm)
        assertNull(result.powerWatts)
        assertNull(result.resistanceLevel)
    }

    @Test
    fun parse_noSpeed_whenMoreDataBitIsOne() {
        // Flags: 0x0001 -> More Data is 1, so Instantaneous Speed is NOT present
        val data = byteArrayOf(0x01, 0x00)
        val result = FtmsIndoorBikeDataParser.parse(data)

        assertNotNull(result)
        assertNull(result!!.speedKmh)
    }

    @Test
    fun parse_speedCadenceResistanceAndPower() {
        // Flags:
        // Bit 0 = 0 (Speed present)
        // Bit 2 = 1 (Cadence present, 0x04)
        // Bit 5 = 1 (Resistance present, 0x20)
        // Bit 6 = 1 (Power present, 0x40)
        // Total flags = 0x0064 -> 64 00
        // Speed: 3000 -> 30.00 km/h (0x0BB8 -> B8 0B)
        // Cadence: 180 raw -> 90 RPM (0.5 RPM unit, 0x00B4 -> B4 00)
        // Resistance: 16 (0x0010 -> 10 00)
        // Power: 220 W (0x00DC -> DC 00)
        val data = byteArrayOf(
            0x64, 0x00, // Flags
            0xB8.toByte(), 0x0B, // Speed: 3000 -> 30.0 km/h
            0xB4.toByte(), 0x00, // Cadence: 180 -> 90 rpm
            0x10, 0x00, // Resistance: 16
            0xDC.toByte(), 0x00 // Power: 220W
        )

        val result = FtmsIndoorBikeDataParser.parse(data)
        assertNotNull(result)
        assertEquals(30.00, result!!.speedKmh!!, 0.001)
        assertEquals(90, result.cadenceRpm)
        assertEquals(16, result.resistanceLevel)
        assertEquals(220, result.powerWatts)
    }

    @Test
    fun parse_withDistanceAndHeartRate() {
        // Flags:
        // Bit 0 = 1 (No speed)
        // Bit 4 = 1 (Total distance present, 0x10)
        // Bit 6 = 1 (Power present, 0x40)
        // Bit 9 = 1 (Heart rate present, 0x0200 -> byte 1 bit 1)
        // Flags low byte: 0x51 (bits 0, 4, 6)
        // Flags high byte: 0x02 (bit 9)
        // Distance: 5432 meters (0x001538 -> 38 15 00)
        // Power: 250 W (0x00FA -> FA 00)
        // Heart rate: 155 bpm (0x9B)
        val data = byteArrayOf(
            0x51,
            0x02, // Flags
            0x38,
            0x15,
            0x00, // Distance: 5432m -> 5.432 km
            0xFA.toByte(),
            0x00, // Power: 250 W
            0x9B.toByte() // HR: 155 bpm
        )

        val result = FtmsIndoorBikeDataParser.parse(data)
        assertNotNull(result)
        assertNull(result!!.speedKmh)
        assertEquals(5.432, result.totalDistanceKm!!, 0.001)
        assertEquals(250, result.powerWatts)
        assertEquals(155, result.heartRateBpm)
    }

    @Test
    fun parse_truncatedPayload_gracefullyParsesAvailableFieldsWithoutCrashing() {
        // Flags declare speed and cadence, but data is truncated mid-cadence
        val truncated = byteArrayOf(
            0x04,
            0x00, // Flags (speed + cadence)
            0xB8.toByte(),
            0x0B, // Speed
            0xB4.toByte() // Only 1 byte of cadence (needs 2)
        )

        val result = FtmsIndoorBikeDataParser.parse(truncated)
        assertNotNull(result)
        assertEquals(30.00, result!!.speedKmh!!, 0.001)
        assertNull(result.cadenceRpm) // Cadence dropped due to truncation
    }
}

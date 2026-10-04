package com.valpr.bikecompanion.bike.ftms

import kotlin.math.roundToInt

data class FtmsIndoorBikeData(
    val speedKmh: Double? = null,
    val cadenceRpm: Int? = null,
    val resistanceLevel: Int? = null,
    val powerWatts: Int? = null,
    val totalDistanceKm: Double? = null,
    val heartRateBpm: Int? = null,
    val elapsedSeconds: Int? = null
)

/**
 * Parses Bluetooth SIG Fitness Machine Service (FTMS) Indoor Bike Data (UUID 0x2AD2).
 *
 * Implements dynamic offset calculation based on the 16-bit Flags field per
 * Bluetooth SIG FTMS Specification v1.0.
 */
object FtmsIndoorBikeDataParser {

    fun parse(data: ByteArray): FtmsIndoorBikeData? {
        if (data.size < 2) return null

        val flags = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        var offset = 2

        var speedKmh: Double? = null
        var cadenceRpm: Int? = null
        var resistanceLevel: Int? = null
        var powerWatts: Int? = null
        var totalDistanceKm: Double? = null
        var heartRateBpm: Int? = null
        var elapsedSeconds: Int? = null

        // Bit 0: More Data (0 = Instantaneous Speed is present, 1 = Speed not present)
        val moreData = (flags and 0x01) != 0
        if (!moreData) {
            if (offset + 2 <= data.size) {
                val rawSpeed = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                speedKmh = rawSpeed * 0.01
                offset += 2
            }
        }

        // Bit 1: Average Speed Present
        val avgSpeedPresent = (flags and (1 shl 1)) != 0
        if (avgSpeedPresent) {
            if (offset + 2 <= data.size) {
                offset += 2
            }
        }

        // Bit 2: Instantaneous Cadence Present (0.5 RPM resolution)
        val cadencePresent = (flags and (1 shl 2)) != 0
        if (cadencePresent) {
            if (offset + 2 <= data.size) {
                val rawCadence = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                cadenceRpm = (rawCadence * 0.5).roundToInt()
                offset += 2
            }
        }

        // Bit 3: Average Cadence Present
        val avgCadencePresent = (flags and (1 shl 3)) != 0
        if (avgCadencePresent) {
            if (offset + 2 <= data.size) {
                offset += 2
            }
        }

        // Bit 4: Total Distance Present (24-bit unsigned, in meters)
        val totalDistancePresent = (flags and (1 shl 4)) != 0
        if (totalDistancePresent) {
            if (offset + 3 <= data.size) {
                val meters = (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16)
                totalDistanceKm = meters / 1000.0
                offset += 3
            }
        }

        // Bit 5: Resistance Level Present (sint16)
        val resistancePresent = (flags and (1 shl 5)) != 0
        if (resistancePresent) {
            if (offset + 2 <= data.size) {
                val rawRes = (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)
                resistanceLevel = rawRes
                offset += 2
            }
        }

        // Bit 6: Instantaneous Power Present (sint16, in Watts)
        val powerPresent = (flags and (1 shl 6)) != 0
        if (powerPresent) {
            if (offset + 2 <= data.size) {
                val rawPower = (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)
                powerWatts = rawPower
                offset += 2
            }
        }

        // Bit 7: Average Power Present
        val avgPowerPresent = (flags and (1 shl 7)) != 0
        if (avgPowerPresent) {
            if (offset + 2 <= data.size) {
                offset += 2
            }
        }

        // Bit 8: Expended Energy Present (5 bytes: 2 energy, 2 energy/hr, 1 energy/min)
        val energyPresent = (flags and (1 shl 8)) != 0
        if (energyPresent) {
            if (offset + 5 <= data.size) {
                offset += 5
            }
        }

        // Bit 9: Heart Rate Present (uint8, in BPM)
        val hrPresent = (flags and (1 shl 9)) != 0
        if (hrPresent) {
            if (offset + 1 <= data.size) {
                heartRateBpm = data[offset].toInt() and 0xFF
                offset += 1
            }
        }

        // Bit 10: Metabolic Equivalent Present
        val metPresent = (flags and (1 shl 10)) != 0
        if (metPresent) {
            if (offset + 1 <= data.size) {
                offset += 1
            }
        }

        // Bit 11: Elapsed Time Present (uint16, in seconds)
        val elapsedPresent = (flags and (1 shl 11)) != 0
        if (elapsedPresent) {
            if (offset + 2 <= data.size) {
                elapsedSeconds = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                offset += 2
            }
        }

        return FtmsIndoorBikeData(
            speedKmh = speedKmh,
            cadenceRpm = cadenceRpm,
            resistanceLevel = resistanceLevel,
            powerWatts = powerWatts,
            totalDistanceKm = totalDistanceKm,
            heartRateBpm = heartRateBpm,
            elapsedSeconds = elapsedSeconds
        )
    }
}

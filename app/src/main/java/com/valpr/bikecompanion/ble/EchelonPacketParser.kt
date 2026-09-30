package com.valpr.bikecompanion.ble

sealed interface ParsedPacket {
    data class CadenceFrame(
        val cadenceRpm: Int,
        val elapsedSeconds: Int,
        val distanceKm: Double,
        val speedKmh: Double
    ) : ParsedPacket

    data class ResistanceFrame(val resistanceLevel: Int) : ParsedPacket

    data class LockedBikeFrame(val rawBytes: ByteArray) : ParsedPacket {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LockedBikeFrame
            return rawBytes.contentEquals(other.rawBytes)
        }

        override fun hashCode(): Int = rawBytes.contentHashCode()
    }

    data class UnknownFrame(val rawBytes: ByteArray, val reason: String) : ParsedPacket {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as UnknownFrame
            return rawBytes.contentEquals(other.rawBytes) && reason == other.reason
        }

        override fun hashCode(): Int {
            var result = rawBytes.contentHashCode()
            result = 31 * result + reason.hashCode()
            return result
        }
    }
}

object EchelonPacketParser {

    /**
     * Parses raw incoming notification packet from Echelon bike.
     */
    fun parse(data: ByteArray): ParsedPacket {
        if (data.isEmpty()) {
            return ParsedPacket.UnknownFrame(data, "Empty packet")
        }

        val b0 = data[0].toInt() and 0xFF

        // Echelon packets start with 0xF0
        if (b0 != 0xF0) {
            return ParsedPacket.UnknownFrame(data, "Unexpected start byte: 0x%02X".format(b0))
        }

        if (data.size < 2) {
            return ParsedPacket.UnknownFrame(data, "Truncated frame")
        }

        val b1 = data[1].toInt() and 0xFF

        // Check for locked bike firmware frames
        // (0xF0, 0xE0 ... or 0xF0, 0xA5, 0x01, 0x0E, 0xA4)
        if (data.size == 8 && b1 == 0xE0) {
            return ParsedPacket.LockedBikeFrame(data)
        }
        if (data.size == 5 && b1 == 0xA5 && (data[2].toInt() and 0xFF) == 0x01 && (data[3].toInt() and 0xFF) == 0x0E) {
            return ParsedPacket.LockedBikeFrame(data)
        }

        // Resistance frame (5 bytes, opcode 0xD2)
        if (data.size == 5 && b1 == 0xD2) {
            val resistance = data[3].toInt() and 0xFF
            return ParsedPacket.ResistanceFrame(
                resistanceLevel = resistance.coerceIn(1, 32)
            )
        }

        // Cadence & Telemetry frame (13 bytes, opcode 0xD1)
        if (data.size == 13 && b1 == 0xD1) {
            val elapsedHigh = data[3].toInt() and 0xFF
            val elapsedLow = data[4].toInt() and 0xFF
            val elapsedSeconds = (elapsedHigh shl 8) or elapsedLow

            val distHigh = data[7].toInt() and 0xFF
            val distLow = data[8].toInt() and 0xFF
            val distRaw = (distHigh shl 8) or distLow
            val distanceKm = distRaw / 100.0

            val cadence = data[10].toInt() and 0xFF
            val speedKmh = 0.37497622 * cadence.toDouble()

            return ParsedPacket.CadenceFrame(
                cadenceRpm = cadence,
                elapsedSeconds = elapsedSeconds,
                distanceKm = distanceKm,
                speedKmh = speedKmh
            )
        }

        return ParsedPacket.UnknownFrame(
            data,
            "Unrecognized opcode: 0x%02X, length: %d".format(b1, data.size)
        )
    }
}

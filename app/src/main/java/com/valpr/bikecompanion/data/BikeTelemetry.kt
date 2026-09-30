package com.valpr.bikecompanion.data

data class BikeTelemetry(
    val cadenceRpm: Int = 0,
    val resistanceLevel: Int = 1,
    val estimatedWatts: Int = 0,
    val speedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val elapsedSeconds: Int = 0,
    val lastUpdateTimestampMs: Long = 0L,
    val isLockedFirmwareDetected: Boolean = false
) {
    val formattedElapsedTime: String
        get() {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

enum class PacketDirection {
    RX, TX
}

data class PacketLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val direction: PacketDirection,
    val opcode: String,
    val rawBytes: ByteArray,
    val description: String
) {
    val hexString: String
        get() = rawBytes.joinToString(" ") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PacketLogEntry
        return timestampMs == other.timestampMs &&
                direction == other.direction &&
                rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

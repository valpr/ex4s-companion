package com.valpr.bikecompanion.ble

object EchelonProtocol {

    /**
     * Calculates the uint8 truncated checksum byte:
     * sum of all bytes in the array from index 0 to length - 2.
     */
    fun calculateChecksum(bytes: ByteArray): Byte {
        var sum = 0
        for (i in 0 until bytes.size - 1) {
            sum += (bytes[i].toInt() and 0xFF)
        }
        return (sum and 0xFF).toByte()
    }

    /**
     * Handshake Step 1: 0xF0, 0xA1, 0x00, 0x91 (sent 4 times in QZ reference)
     */
    fun createHandshakeStep1(): ByteArray = byteArrayOf(
        0xF0.toByte(),
        0xA1.toByte(),
        0x00.toByte(),
        0x91.toByte()
    )

    /**
     * Handshake Step 2: 0xF0, 0xA3, 0x00, 0x93 (sent once)
     */
    fun createHandshakeStep2(): ByteArray = byteArrayOf(
        0xF0.toByte(),
        0xA3.toByte(),
        0x00.toByte(),
        0x93.toByte()
    )

    /**
     * Handshake Step 3: 0xF0, 0xB0, 0x01, 0x01, 0xA2 (sensor activation, sent once)
     */
    fun createHandshakeStep3(): ByteArray = byteArrayOf(
        0xF0.toByte(),
        0xB0.toByte(),
        0x01.toByte(),
        0x01.toByte(),
        0xA2.toByte()
    )

    /**
     * Returns the full handshake sequence of commands in strict order.
     */
    fun getFullHandshakeSequence(): List<Pair<ByteArray, String>> = listOf(
        createHandshakeStep1() to "Handshake Step 1 (1/4)",
        createHandshakeStep1() to "Handshake Step 1 (2/4)",
        createHandshakeStep1() to "Handshake Step 1 (3/4)",
        createHandshakeStep1() to "Handshake Step 1 (4/4)",
        createHandshakeStep2() to "Handshake Step 2 (Mode)",
        createHandshakeStep1() to "Handshake Step 1 (Interlude)",
        createHandshakeStep3() to "Handshake Step 3 (Sensor Activate)"
    )

    /**
     * Keep-alive poll command sent every 2 seconds:
     * 0xF0, 0xA0, 0x01, <counter>, <checksum>
     */
    fun createPollCommand(counter: Int): ByteArray {
        val safeCounter = if (counter <= 0 || counter > 255) 1 else counter
        val packet = byteArrayOf(
            0xF0.toByte(),
            0xA0.toByte(),
            0x01.toByte(),
            safeCounter.toByte(),
            0x00.toByte()
        )
        packet[4] = calculateChecksum(packet)
        return packet
    }

    /**
     * Set resistance command (1..32):
     * 0xF0, 0xB1, 0x01, <level>, <checksum>
     */
    fun createResistanceCommand(level: Int): ByteArray {
        val safeLevel = level.coerceIn(1, 32)
        val packet = byteArrayOf(
            0xF0.toByte(),
            0xB1.toByte(),
            0x01.toByte(),
            safeLevel.toByte(),
            0x00.toByte()
        )
        packet[4] = calculateChecksum(packet)
        return packet
    }
}

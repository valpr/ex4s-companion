package com.valpr.bikecompanion

import com.valpr.bikecompanion.ble.EchelonProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EchelonProtocolTest {

    @Test
    fun testChecksumCalculation() {
        // Step 1: 0xF0 + 0xA1 + 0x00 = 0x191 -> 0x91
        val step1 = byteArrayOf(0xF0.toByte(), 0xA1.toByte(), 0x00.toByte(), 0x00.toByte())
        val checksum1 = EchelonProtocol.calculateChecksum(step1)
        assertEquals(0x91.toByte(), checksum1)

        // Step 2: 0xF0 + 0xA3 + 0x00 = 0x193 -> 0x93
        val step2 = byteArrayOf(0xF0.toByte(), 0xA3.toByte(), 0x00.toByte(), 0x00.toByte())
        val checksum2 = EchelonProtocol.calculateChecksum(step2)
        assertEquals(0x93.toByte(), checksum2)

        // Step 3: 0xF0 + 0xB0 + 0x01 + 0x01 = 0x1A2 -> 0xA2
        val step3 = byteArrayOf(0xF0.toByte(), 0xB0.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte())
        val checksum3 = EchelonProtocol.calculateChecksum(step3)
        assertEquals(0xA2.toByte(), checksum3)
    }

    @Test
    fun testHandshakeSequencePackets() {
        val step1 = EchelonProtocol.createHandshakeStep1()
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xA1.toByte(), 0x00.toByte(), 0x91.toByte()),
            step1
        )

        val step2 = EchelonProtocol.createHandshakeStep2()
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xA3.toByte(), 0x00.toByte(), 0x93.toByte()),
            step2
        )

        val step3 = EchelonProtocol.createHandshakeStep3()
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xB0.toByte(), 0x01.toByte(), 0x01.toByte(), 0xA2.toByte()),
            step3
        )

        val fullSequence = EchelonProtocol.getFullHandshakeSequence()
        assertEquals(7, fullSequence.size)
    }

    @Test
    fun testPollCommand() {
        // Counter = 1: 0xF0 + 0xA0 + 0x01 + 0x01 = 0x192 -> 0x92
        val poll1 = EchelonProtocol.createPollCommand(1)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xA0.toByte(), 0x01.toByte(), 0x01.toByte(), 0x92.toByte()),
            poll1
        )

        // Counter = 2: 0xF0 + 0xA0 + 0x01 + 0x02 = 0x193 -> 0x93
        val poll2 = EchelonProtocol.createPollCommand(2)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xA0.toByte(), 0x01.toByte(), 0x02.toByte(), 0x93.toByte()),
            poll2
        )
    }

    @Test
    fun testResistanceCommand() {
        // Resistance Level 1: 0xF0 + 0xB1 + 0x01 + 0x01 = 0x1A3 -> 0xA3
        val cmdL1 = EchelonProtocol.createResistanceCommand(1)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xB1.toByte(), 0x01.toByte(), 0x01.toByte(), 0xA3.toByte()),
            cmdL1
        )

        // Resistance Level 16 (0x10): 0xF0 + 0xB1 + 0x01 + 0x10 = 0x1B2 -> 0xB2
        val cmdL16 = EchelonProtocol.createResistanceCommand(16)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xB1.toByte(), 0x01.toByte(), 0x10.toByte(), 0xB2.toByte()),
            cmdL16
        )

        // Resistance Level 32 (0x20): 0xF0 + 0xB1 + 0x01 + 0x20 = 0x1C2 -> 0xC2
        val cmdL32 = EchelonProtocol.createResistanceCommand(32)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0xB1.toByte(), 0x01.toByte(), 0x20.toByte(), 0xC2.toByte()),
            cmdL32
        )

        // Clamping check: 0 clamped to 1, 50 clamped to 32
        assertArrayEquals(cmdL1, EchelonProtocol.createResistanceCommand(0))
        assertArrayEquals(cmdL32, EchelonProtocol.createResistanceCommand(50))
    }
}

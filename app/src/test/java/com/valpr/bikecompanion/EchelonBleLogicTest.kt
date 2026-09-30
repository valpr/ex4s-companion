package com.valpr.bikecompanion

import com.valpr.bikecompanion.ble.EchelonBleLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchelonBleLogicTest {

    @Test
    fun deviceMatch_nameVariants() {
        assertTrue(EchelonBleLogic.isEchelonDevice("ECH_0123", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("Echelon EX-4S", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("ECHELON FIT", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("Sport Bike", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("EX-4S+", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("EX4S_99", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("EX5-Bike", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("EX3", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("Bike 123", false))
        // Case-insensitive
        assertTrue(EchelonBleLogic.isEchelonDevice("echelon connect", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("ech sport", false))
    }

    @Test
    fun deviceMatch_uuidIdentifiedFallback() {
        // Long-name bug: nothing advertised, UUID carries the match.
        assertTrue(EchelonBleLogic.isEchelonDevice("Echelon EX-4S (Identified by UUID)", false))
        assertTrue(EchelonBleLogic.isEchelonDevice("Unknown Device", true))
    }

    @Test
    fun deviceMatch_rejectsNeighbors() {
        assertFalse(EchelonBleLogic.isEchelonDevice("Unknown Device", false))
        assertFalse(EchelonBleLogic.isEchelonDevice("Pixel Watch", false))
        assertFalse(EchelonBleLogic.isEchelonDevice("JBL Speaker", false))
        assertFalse(EchelonBleLogic.isEchelonDevice("", false))
    }

    @Test
    fun pollCounter_incrementsAndWraps() {
        assertEquals(2, EchelonBleLogic.nextPollCounter(1))
        assertEquals(255, EchelonBleLogic.nextPollCounter(254))
        assertEquals(1, EchelonBleLogic.nextPollCounter(255))
    }

    @Test
    fun pollCounter_outOfRangeResetsToOne() {
        assertEquals(1, EchelonBleLogic.nextPollCounter(0))
        assertEquals(1, EchelonBleLogic.nextPollCounter(-5))
        assertEquals(1, EchelonBleLogic.nextPollCounter(256))
    }

    @Test
    fun pollCounter_fullCycleStaysInRange() {
        var counter = 1
        repeat(600) {
            assertTrue(counter in 1..255)
            counter = EchelonBleLogic.nextPollCounter(counter)
        }
        // 600 steps from 1: (1 + 600) wraps -> ((1 - 1 + 600) % 255) + 1 = 91
        assertEquals(91, counter)
    }
}

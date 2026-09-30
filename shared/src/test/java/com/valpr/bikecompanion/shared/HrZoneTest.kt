package com.valpr.bikecompanion.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class HrZoneTest {
    @Test
    fun testZoneThresholds_matchWatchContract() {
        val maxHr = 200
        assertEquals(1, HrZone.zoneNumber(0, maxHr))
        assertEquals(1, HrZone.zoneNumber(100, maxHr))
        assertEquals(1, HrZone.zoneNumber(119, maxHr))
        assertEquals(2, HrZone.zoneNumber(120, maxHr))
        assertEquals(2, HrZone.zoneNumber(135, maxHr))
        assertEquals(3, HrZone.zoneNumber(140, maxHr))
        assertEquals(3, HrZone.zoneNumber(155, maxHr))
        assertEquals(4, HrZone.zoneNumber(160, maxHr))
        assertEquals(4, HrZone.zoneNumber(175, maxHr))
        assertEquals(5, HrZone.zoneNumber(180, maxHr))
        assertEquals(5, HrZone.zoneNumber(210, maxHr))
    }

    @Test
    fun testLabels() {
        assertEquals("Recovery", HrZone.label(1))
        assertEquals("Endurance", HrZone.label(2))
        assertEquals("Tempo", HrZone.label(3))
        assertEquals("Threshold", HrZone.label(4))
        assertEquals("Max Effort", HrZone.label(5))
    }
}

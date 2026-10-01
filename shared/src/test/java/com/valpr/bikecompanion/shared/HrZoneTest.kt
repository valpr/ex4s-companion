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
    fun testKarvonenThresholds_matchAthleteMetrics() {
        val maxHr = 190
        val restingHr = 50
        // hrr = 140
        // Z1: < 134 (< 60% HRR)
        assertEquals(1, HrZone.zoneNumber(40, maxHr, restingHr, useKarvonen = true))
        assertEquals(1, HrZone.zoneNumber(50, maxHr, restingHr, useKarvonen = true))
        assertEquals(1, HrZone.zoneNumber(133, maxHr, restingHr, useKarvonen = true))
        // Z2: 134..147 (60-70% HRR)
        assertEquals(2, HrZone.zoneNumber(134, maxHr, restingHr, useKarvonen = true))
        assertEquals(2, HrZone.zoneNumber(147, maxHr, restingHr, useKarvonen = true))
        // Z3: 148..161 (70-80% HRR)
        assertEquals(3, HrZone.zoneNumber(148, maxHr, restingHr, useKarvonen = true))
        assertEquals(3, HrZone.zoneNumber(161, maxHr, restingHr, useKarvonen = true))
        // Z4: 162..175 (80-90% HRR)
        assertEquals(4, HrZone.zoneNumber(162, maxHr, restingHr, useKarvonen = true))
        assertEquals(4, HrZone.zoneNumber(175, maxHr, restingHr, useKarvonen = true))
        // Z5: >= 176 (>= 90% HRR)
        assertEquals(5, HrZone.zoneNumber(176, maxHr, restingHr, useKarvonen = true))
        assertEquals(5, HrZone.zoneNumber(190, maxHr, restingHr, useKarvonen = true))
        assertEquals(5, HrZone.zoneNumber(200, maxHr, restingHr, useKarvonen = true))
    }

    @Test
    fun testKarvonenClamping_preventsZeroOrNegativeReserve() {
        // Bad athlete input where restingHr >= maxHr
        val badMax = 60
        val badRest = 70
        // Coerces max to at least rest + 10 = 80, safe calculation
        assertEquals(1, HrZone.zoneNumber(50, badMax, badRest, useKarvonen = true))
        assertEquals(5, HrZone.zoneNumber(85, badMax, badRest, useKarvonen = true))

        // Non-positive bpm or maxHr
        assertEquals(1, HrZone.zoneNumber(0, 190, 60, useKarvonen = true))
        assertEquals(1, HrZone.zoneNumber(-10, 190, 60, useKarvonen = true))
        assertEquals(1, HrZone.zoneNumber(120, 0, 60, useKarvonen = true))

        // Small inputs where maxHr < 31 would cause clampedMax - 1 < 30 without coerceAtLeast(40)
        assertEquals(1, HrZone.zoneNumber(10, 25, 15, useKarvonen = true))
        assertEquals(5, HrZone.zoneNumber(45, 25, 15, useKarvonen = true))
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

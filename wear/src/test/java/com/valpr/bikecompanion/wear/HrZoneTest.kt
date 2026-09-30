package com.valpr.bikecompanion.wear

import com.valpr.bikecompanion.wear.data.HrZone
import org.junit.Assert.assertEquals
import org.junit.Test

class HrZoneTest {
    @Test
    fun testHrZoneCalculations() {
        val maxHr = 200

        // Zero or negative
        assertEquals(HrZone.ZONE_1, HrZone.fromBpm(0, maxHr))
        assertEquals(HrZone.ZONE_1, HrZone.fromBpm(-10, maxHr))

        // Zone 1: < 60% (< 120 bpm)
        assertEquals(HrZone.ZONE_1, HrZone.fromBpm(100, maxHr))
        assertEquals(HrZone.ZONE_1, HrZone.fromBpm(119, maxHr))

        // Zone 2: 60-70% (120 - 139 bpm)
        assertEquals(HrZone.ZONE_2, HrZone.fromBpm(120, maxHr))
        assertEquals(HrZone.ZONE_2, HrZone.fromBpm(135, maxHr))

        // Zone 3: 70-80% (140 - 159 bpm)
        assertEquals(HrZone.ZONE_3, HrZone.fromBpm(140, maxHr))
        assertEquals(HrZone.ZONE_3, HrZone.fromBpm(155, maxHr))

        // Zone 4: 80-90% (160 - 179 bpm)
        assertEquals(HrZone.ZONE_4, HrZone.fromBpm(160, maxHr))
        assertEquals(HrZone.ZONE_4, HrZone.fromBpm(175, maxHr))

        // Zone 5: >= 90% (>= 180 bpm)
        assertEquals(HrZone.ZONE_5, HrZone.fromBpm(180, maxHr))
        assertEquals(HrZone.ZONE_5, HrZone.fromBpm(195, maxHr))
        assertEquals(HrZone.ZONE_5, HrZone.fromBpm(210, maxHr))
    }

    @Test
    fun testHrZoneLabelsAndNumbers() {
        assertEquals(1, HrZone.ZONE_1.zoneNumber)
        assertEquals(2, HrZone.ZONE_2.zoneNumber)
        assertEquals(3, HrZone.ZONE_3.zoneNumber)
        assertEquals(4, HrZone.ZONE_4.zoneNumber)
        assertEquals(5, HrZone.ZONE_5.zoneNumber)

        assertEquals("Recovery", HrZone.ZONE_1.label)
        assertEquals("Endurance", HrZone.ZONE_2.label)
        assertEquals("Tempo", HrZone.ZONE_3.label)
        assertEquals("Threshold", HrZone.ZONE_4.label)
        assertEquals("Max Effort", HrZone.ZONE_5.label)
    }
}

package com.valpr.bikecompanion.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AthleteMetricsTest {
    @Test
    fun testMaxHrFormulas() {
        // Age 30: Tanaka = 208 - 21 = 187
        assertEquals(187, AthleteMetrics.estimateMaxHrTanaka(30))
        // Age 30: Fox = 220 - 30 = 190
        assertEquals(190, AthleteMetrics.estimateMaxHrFox(30))
        // Age 30 Female: Gulati = 206 - (0.88 * 30) = 206 - 26.4 = 180
        assertEquals(180, AthleteMetrics.estimateMaxHrGulati(30))

        assertEquals(180, AthleteMetrics.recommendMaxHr(30, BiologicalSex.FEMALE))
        assertEquals(187, AthleteMetrics.recommendMaxHr(30, BiologicalSex.MALE))
    }

    @Test
    fun testWattsPerKgAndCategory() {
        val wkg = AthleteMetrics.calculateWattsPerKg(250, 75.0f)
        assertEquals(3.333f, wkg, 0.01f)
        assertEquals(CyclingCategory.TRAINED, CyclingCategory.fromWkg(wkg))

        assertEquals(CyclingCategory.RECREATIONAL, CyclingCategory.fromWkg(1.8f))
        assertEquals(CyclingCategory.MODERATE, CyclingCategory.fromWkg(2.4f))
        assertEquals(CyclingCategory.VERY_GOOD, CyclingCategory.fromWkg(3.8f))
        assertEquals(CyclingCategory.EXCELLENT, CyclingCategory.fromWkg(4.5f))
        assertEquals(CyclingCategory.WORLD_CLASS, CyclingCategory.fromWkg(5.2f))

        // Boundary/zero cases
        assertEquals(0.0f, AthleteMetrics.calculateWattsPerKg(0, 75f), 0.001f)
        assertEquals(0.0f, AthleteMetrics.calculateWattsPerKg(200, 0f), 0.001f)
    }

    @Test
    fun testCogganPowerZones() {
        val ftp = 200
        val zones = AthleteMetrics.calculateCogganPowerZones(ftp)
        assertEquals(7, zones.size)

        // Z1: <= 55% of 200 = 110W
        assertEquals(1, zones[0].zoneNumber)
        assertEquals(110, zones[0].maxWatts)

        // Z2: 55-75% -> 111 - 150W
        assertEquals(2, zones[1].zoneNumber)
        assertEquals(111, zones[1].minWatts)
        assertEquals(150, zones[1].maxWatts)

        // Z4: 91-105% -> 181 - 210W
        assertEquals(4, zones[3].zoneNumber)
        assertEquals(181, zones[3].minWatts)
        assertEquals(210, zones[3].maxWatts)
    }

    @Test
    fun testHeartRateZones() {
        val maxHr = 190
        val zones = AthleteMetrics.calculateMaxHrZones(maxHr)
        assertEquals(5, zones.size)
        // Z1: < 60% = 113
        assertEquals(113, zones[0].maxBpm)
        // Z5: >= 90% = 171..190
        assertEquals(171, zones[4].minBpm)
        assertEquals(190, zones[4].maxBpm)

        val karvonen = AthleteMetrics.calculateKarvonenZones(maxHr = 190, restingHr = 50)
        assertEquals(5, karvonen.size)
        assertTrue(karvonen[0].minBpm >= 50)
        assertEquals(190, karvonen[4].maxBpm)
    }

    @Test
    fun testUnitConversions() {
        val kg = 75.0f
        val lbs = AthleteMetrics.kgToLbs(kg)
        assertEquals(165.34f, lbs, 0.1f)
        assertEquals(kg, AthleteMetrics.lbsToKg(lbs), 0.01f)

        val cm = 178f
        val inches = AthleteMetrics.cmToInches(cm)
        assertEquals(70.07f, inches, 0.1f)
        assertEquals(cm, AthleteMetrics.inchesToCm(inches), 0.01f)

        assertEquals("5' 10\"", AthleteMetrics.formatHeightImperial(178f))
    }

    @Test
    fun testBmrCalculation() {
        // Male, 75kg, 178cm, 30y:
        // (10 * 75) + (6.25 * 178) - (5 * 30) + 5 = 750 + 1112.5 - 150 + 5 = 1717.5 -> 1718 kcal
        val bmrMale = AthleteMetrics.estimateBmrKcal(75f, 178f, 30, BiologicalSex.MALE)
        assertEquals(1718, bmrMale)

        // Female with same stats: 1717.5 - 5 - 161 = 1551.5 -> 1552 kcal
        val bmrFemale = AthleteMetrics.estimateBmrKcal(75f, 178f, 30, BiologicalSex.FEMALE)
        assertEquals(1552, bmrFemale)
    }
}

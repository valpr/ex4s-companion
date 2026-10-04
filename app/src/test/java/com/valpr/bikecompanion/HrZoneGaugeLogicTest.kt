package com.valpr.bikecompanion

import com.valpr.bikecompanion.shared.HrZone
import com.valpr.bikecompanion.ui.workout.HrZoneGaugeLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HrZoneGaugeLogicTest {

    @Test
    fun sweeps_sumToOne_proportionalToBpmWidth() {
        val segments = HrZoneGaugeLogic.computeSegments(maxHr = 190, restingHr = 60, useKarvonen = false)
        assertEquals(5, segments.size)
        val total = segments.sumOf { it.sweepFraction.toDouble() }
        assertEquals(1.0, total, 0.001)
        // %max thresholds: 114 / 133 / 152 / 171 over [40, 190]: Z1 dominates, Z2-Z5 ~equal.
        assertTrue(segments[0].sweepFraction > 0.45f)
        assertEquals(segments[1].sweepFraction, segments[2].sweepFraction, 0.02f)
        assertEquals(segments[3].sweepFraction, segments[4].sweepFraction, 0.02f)
    }

    @Test
    fun fill_midZoneThree_lightsLowerAndHalfActive() {
        // Z3 is [133, 152] for max 190: HR 142 fills (142-133)/(152-133) = 9/19.
        val fills = HrZoneGaugeLogic.fillFractions(bpm = 142, maxHr = 190, restingHr = 60, useKarvonen = false)
        assertEquals(1f, fills[0], 0.001f)
        assertEquals(1f, fills[1], 0.001f)
        assertEquals(9.0 / 19.0, fills[2].toDouble(), 0.001)
        assertEquals(0f, fills[3], 0.001f)
        assertEquals(0f, fills[4], 0.001f)
    }

    @Test
    fun fill_agreesWithZoneNumber_atEveryBoundary() {
        // Regression net for the rounding-vs-float mismatch: fills-implied
        // active zone (first zone with fill < 1, else 5) must equal HrZone.
        val maxHrs = listOf(182, 183, 187, 190, 200)
        for (maxHr in maxHrs) {
            for (useKarvonen in listOf(false, true)) {
                val hi = if (useKarvonen) {
                    maxHr.coerceAtLeast(60 + 10).coerceAtLeast(40)
                } else {
                    maxHr
                }
                for (bpm in 30..hi + 10) {
                    val fills = HrZoneGaugeLogic.fillFractions(
                        bpm = bpm,
                        maxHr = maxHr,
                        restingHr = 60,
                        useKarvonen = useKarvonen
                    )
                    val gaugeZone = fills.indexOfFirst { it < 1f }.let { if (it == -1) 5 else it + 1 }
                    assertEquals(
                        "maxHr=$maxHr karvonen=$useKarvonen bpm=$bpm fills=$fills",
                        HrZone.zoneNumber(bpm, maxHr, 60, useKarvonen),
                        gaugeZone
                    )
                }
            }
        }
    }

    @Test
    fun fill_clampsAtEdges() {
        assertTrue(HrZoneGaugeLogic.fillFractions(0, 190, 60, false).all { it == 0f })
        assertTrue(HrZoneGaugeLogic.fillFractions(30, 190, 60, false).all { it == 0f })
        assertTrue(HrZoneGaugeLogic.fillFractions(250, 190, 60, false).all { it == 1f })
    }

    @Test
    fun karvonen_respectsRestingFloor() {
        val min = HrZoneGaugeLogic.gaugeMin(maxHr = 190, restingHr = 55, useKarvonen = true)
        assertEquals(40f, min, 0.001f)
        val lowRest = HrZoneGaugeLogic.gaugeMin(maxHr = 190, restingHr = 32, useKarvonen = true)
        assertEquals(32f, lowRest, 0.001f)
        val fills = HrZoneGaugeLogic.fillFractions(bpm = 100, maxHr = 190, restingHr = 60, useKarvonen = true)
        // Karvonen Z1 tops out near 138: HR 100 is still deep in Z1.
        assertTrue(fills[0] > 0f && fills[0] < 1f)
        assertTrue(fills.subList(1, 5).all { it == 0f })
    }

    @Test
    fun degenerate_restingAboveMax_staysMonotonicAndSumsToOne() {
        val segments = HrZoneGaugeLogic.computeSegments(maxHr = 150, restingHr = 160, useKarvonen = true)
        assertEquals(5, segments.size)
        for (i in 1 until segments.size) {
            assertTrue("segment $i inverted: $segments", segments[i].lowBpm >= segments[i - 1].lowBpm)
        }
        assertEquals(1.0, segments.sumOf { it.sweepFraction.toDouble() }, 0.001)
    }

    @Test
    fun invalidMaxHr_fallsBackToDefault() {
        val segments = HrZoneGaugeLogic.computeSegments(maxHr = 0, restingHr = 60, useKarvonen = false)
        assertEquals(5, segments.size)
        assertEquals(1.0, segments.sumOf { it.sweepFraction.toDouble() }, 0.001)
        assertTrue(HrZoneGaugeLogic.gaugeMax(0, 60, false) > 100f)
        // Pinned: bpm<=0 yields all-zero fills (tile shows RESISTANCE instead).
        assertTrue(HrZoneGaugeLogic.fillFractions(-5, 190, 60, false).all { it == 0f })
    }
}

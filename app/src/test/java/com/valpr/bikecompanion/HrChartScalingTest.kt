package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.summary.HrChartScaling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HrChartScalingTest {

    @Test
    fun emptyOrZero_returnsNull() {
        assertNull(HrChartScaling.computeScale(emptyList()))
        assertNull(HrChartScaling.computeScale(listOf(0, 0, 0)))
    }

    @Test
    fun singleSample_enforcesMinRange() {
        val scale = HrChartScaling.computeScale(listOf(150))!!
        assertNotNull(scale)
        assertTrue(scale.range >= 10f)
        assertTrue(scale.maxHr >= 100f)
        assertEquals(1f, HrChartScaling.normalizedY(150, scale), 0.001f)
    }

    @Test
    fun normalRange_normalizes() {
        val scale = HrChartScaling.computeScale(listOf(120, 140, 160))!!
        assertEquals(60f, scale.minHr, 0.001f) // min coerced to <=60
        assertTrue(scale.maxHr >= 160f)
        assertTrue(HrChartScaling.normalizedY(120, scale) in 0f..1f)
        assertTrue(HrChartScaling.normalizedY(160, scale) in 0f..1f)
    }

    @Test
    fun stepX_singleSampleGuardsDivideByZero() {
        assertEquals(100f, HrChartScaling.stepX(100f, 1), 0.001f)
        assertEquals(50f, HrChartScaling.stepX(100f, 3), 0.001f)
    }
}

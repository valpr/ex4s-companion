package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.workout.ResistancePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResistancePresetsTest {

    @Test
    fun echelonStandardRange_preservesFamiliarPresets() {
        val presets = ResistancePresets.forRange(1..32)
        assertEquals(listOf(1, 4, 8, 12, 16, 20, 24, 28, 32), presets)
    }

    @Test
    fun singleValueRange_returnsSingleElement() {
        assertEquals(listOf(5), ResistancePresets.forRange(5..5))
    }

    @Test
    fun smallRange_returnsAllElements() {
        assertEquals(listOf(1, 2, 3, 4), ResistancePresets.forRange(1..4))
    }

    @Test
    fun arbitraryRange_boundsAndOrderingArePreserved() {
        val presets = ResistancePresets.forRange(1..100)
        assertEquals(1, presets.first())
        assertEquals(100, presets.last())
        assertTrue("Presets must be strictly increasing", presets.zipWithNext().all { it.first < it.second })
        assertTrue("All presets must be in range", presets.all { it in 1..100 })
    }

    @Test
    fun subRange_stepsCorrectly() {
        val presets = ResistancePresets.forRange(10..26)
        assertEquals(10, presets.first())
        assertEquals(26, presets.last())
        assertTrue(presets.size in 5..10)
    }
}

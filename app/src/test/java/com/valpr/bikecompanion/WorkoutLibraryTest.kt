package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutRepository
import com.valpr.bikecompanion.workout.ZwoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Class-type library invariants: every seeded sample parses, hits its
 * designed duration/TSS band, carries its type tag, and stays within ERG
 * hardware reach (peak <= 135% FTP so resistance 1..32 can deliver it).
 */
class WorkoutLibraryTest {

    private data class LibraryExpectation(
        val xml: String,
        val durationSeconds: Int,
        val tssMin: Double,
        val tssMax: Double,
        val typeTag: String,
        val maxPowerFraction: Float
    )

    private val library = listOf(
        LibraryExpectation(WorkoutRepository.SAMPLE_ENDURANCE_30, 1800, 13.0, 23.0, "Endurance", 0.65f),
        LibraryExpectation(WorkoutRepository.SAMPLE_ENDURANCE_60, 3600, 30.0, 48.0, "Endurance", 0.68f),
        LibraryExpectation(WorkoutRepository.SAMPLE_CLIMB_30, 1680, 15.0, 25.0, "Climb", 0.82f),
        LibraryExpectation(WorkoutRepository.SAMPLE_CLIMB_45, 2700, 26.0, 42.0, "Strength", 0.85f),
        LibraryExpectation(WorkoutRepository.SAMPLE_HIIT_30, 1800, 18.0, 30.0, "HIIT", 1.15f),
        LibraryExpectation(WorkoutRepository.SAMPLE_HIIT_45, 2700, 34.0, 54.0, "HIIT", 1.12f),
        LibraryExpectation(WorkoutRepository.SAMPLE_TABATA_20, 1260, 15.0, 26.0, "Tabata", 1.25f),
        LibraryExpectation(WorkoutRepository.SAMPLE_VO2_30, 1800, 21.0, 34.0, "VO2Max", 1.10f),
        LibraryExpectation(WorkoutRepository.SAMPLE_VO2_45, 2700, 30.0, 48.0, "VO2Max", 1.09f),
        LibraryExpectation(WorkoutRepository.SAMPLE_RECOVERY_20, 1200, 4.0, 10.0, "Recovery", 0.48f)
    )

    private fun parse(xml: String): Workout = ZwoParser.parse(xml)

    @Test
    fun library_parsesWithExpectedDurationAndTssBand() {
        for (entry in library) {
            val workout = parse(entry.xml)
            assertEquals(
                "duration for ${workout.name}",
                entry.durationSeconds,
                workout.totalDurationSeconds
            )
            assertTrue(
                "TSS ${workout.estimatedTss} in [${entry.tssMin}, ${entry.tssMax}] for ${workout.name}",
                workout.estimatedTss in entry.tssMin..entry.tssMax
            )
        }
    }

    @Test
    fun library_carriesTypeTagAndCoachingCues() {
        for (entry in library) {
            val workout = parse(entry.xml)
            assertTrue(
                "${workout.name} tagged ${entry.typeTag}, was ${workout.tags}",
                workout.tags.contains(entry.typeTag)
            )
            assertTrue("${workout.name} has coaching cues", workout.textEvents.isNotEmpty())
        }
    }

    @Test
    fun library_peakPowerWithinErgReach() {
        for (entry in library) {
            val workout = parse(entry.xml)
            val peak = workout.segments.maxOf { it.averageIntensityFactor }
            assertTrue(
                "${workout.name} peak $peak <= 1.35",
                peak <= 1.35f
            )
            // Spot-check absolute watts at a mid-range FTP stay sane.
            val midWatts = workout.targetWattsAt(ftp = 200, elapsedWorkoutSeconds = workout.totalDurationSeconds / 2)
            assertNotNull("${workout.name} has a mid-ride target", midWatts)
        }
    }

    @Test
    fun library_hardTypesExcludedFromBeginnerPath() {
        for (entry in library) {
            val workout = parse(entry.xml)
            assertFalse(
                "${workout.name} must not carry the Beginner tag",
                workout.tags.any { it.equals("Beginner", ignoreCase = true) }
            )
        }
    }

    @Test
    fun library_intervalBlocksFlattenToExpectedSegmentCounts() {
        // IntervalsT Repeat=N flattens to 2*N on/off segments.
        val hiit30 = parse(WorkoutRepository.SAMPLE_HIIT_30)
        // Warmup + 10x(on+off) + Cooldown = 22 segments.
        assertEquals(22, hiit30.segments.size)

        val tabata = parse(WorkoutRepository.SAMPLE_TABATA_20)
        // Warmup + 8x(on+off) + SteadyState + 8x(on+off) + Cooldown = 35.
        assertEquals(35, tabata.segments.size)

        val vo245 = parse(WorkoutRepository.SAMPLE_VO2_45)
        // Warmup + primer + 3x[5x(on+off)] + 2 mids + Cooldown = 35.
        assertEquals(35, vo245.segments.size)
    }

    @Test
    fun library_expressTierThirtyMinutesOrLess_classicTierFortyFivePlus() {
        val express = listOf(
            WorkoutRepository.SAMPLE_ENDURANCE_30,
            WorkoutRepository.SAMPLE_CLIMB_30,
            WorkoutRepository.SAMPLE_HIIT_30,
            WorkoutRepository.SAMPLE_TABATA_20,
            WorkoutRepository.SAMPLE_VO2_30,
            WorkoutRepository.SAMPLE_RECOVERY_20
        ).map { parse(it) }
        for (workout in express) {
            assertTrue(
                "${workout.name} <= 30 min, was ${workout.totalDurationSeconds}s",
                workout.totalDurationSeconds <= 1800
            )
        }

        val classic = listOf(
            WorkoutRepository.SAMPLE_ENDURANCE_60,
            WorkoutRepository.SAMPLE_CLIMB_45,
            WorkoutRepository.SAMPLE_HIIT_45,
            WorkoutRepository.SAMPLE_VO2_45
        ).map { parse(it) }
        for (workout in classic) {
            assertTrue(
                "${workout.name} >= 45 min, was ${workout.totalDurationSeconds}s",
                workout.totalDurationSeconds >= 2700
            )
        }
    }

    @Test
    fun library_classicVariantsOutrankExpress_hardOutranksRecovery() {
        fun tss(xml: String) = ZwoParser.parse(xml).estimatedTss
        // Intent-level ordering that survives formula tweaks.
        assertTrue(tss(WorkoutRepository.SAMPLE_ENDURANCE_60) > tss(WorkoutRepository.SAMPLE_ENDURANCE_30))
        assertTrue(tss(WorkoutRepository.SAMPLE_CLIMB_45) > tss(WorkoutRepository.SAMPLE_CLIMB_30))
        assertTrue(tss(WorkoutRepository.SAMPLE_HIIT_45) > tss(WorkoutRepository.SAMPLE_HIIT_30))
        assertTrue(tss(WorkoutRepository.SAMPLE_VO2_45) > tss(WorkoutRepository.SAMPLE_VO2_30))
        val recovery = tss(WorkoutRepository.SAMPLE_RECOVERY_20)
        assertTrue(tss(WorkoutRepository.SAMPLE_TABATA_20) > recovery)
        assertTrue(tss(WorkoutRepository.SAMPLE_VO2_30) > recovery)
        assertTrue(tss(WorkoutRepository.SAMPLE_HIIT_30) > recovery)
    }

    @Test
    fun library_climbWorkBlocksUseLowCadence_hiitUsesHighCadence() {
        val climb30 = parse(WorkoutRepository.SAMPLE_CLIMB_30)
        val climbWork = climb30.segments.filter { it.averageIntensityFactor > 0.8f }
        assertTrue(climbWork.isNotEmpty())
        for (segment in climbWork) {
            assertEquals(70, segment.targetCadence)
        }

        val hiit30 = parse(WorkoutRepository.SAMPLE_HIIT_30)
        val hiitWork = hiit30.segments.filter { it.averageIntensityFactor > 1.0f }
        assertTrue(hiitWork.isNotEmpty())
        for (segment in hiitWork) {
            assertEquals(95, segment.targetCadence)
        }
    }
}

package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.BeginnerPlan
import com.valpr.bikecompanion.workout.WorkoutRepository
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.ZwoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beginner Path invariants: progression order, gentle caps, and pure
 * recommendation logic (framework-free per AGENTS.md §8).
 */
class BeginnerPlanTest {

    @Test
    fun levels_areOrderedFourWithMatchingFilenames() {
        assertEquals(4, BeginnerPlan.LEVELS.size)
        BeginnerPlan.LEVELS.forEachIndexed { index, level ->
            assertEquals(index + 1, level.level)
            assertTrue(level.filename.endsWith(".zwo"))
        }
        assertEquals(
            listOf(
                "beginner_01_first_pedals.zwo",
                "beginner_02_building_rhythm.zwo",
                "beginner_03_steady_confidence.zwo",
                "beginner_04_ready_for_more.zwo"
            ),
            BeginnerPlan.LEVELS.map { it.filename }
        )
    }

    @Test
    fun recommendNext_emptySet_returnsLevelOne() {
        assertEquals(1, BeginnerPlan.recommendNext(emptySet()).level)
    }

    @Test
    fun recommendNext_returnsFirstIncompleteLevel() {
        val completed = setOf("beginner_01_first_pedals.zwo", "beginner_03_steady_confidence.zwo")
        assertEquals(2, BeginnerPlan.recommendNext(completed).level)
    }

    @Test
    fun recommendNext_allCompleted_repeatsLevelFour() {
        val completed = BeginnerPlan.LEVELS.map { it.filename }.toSet()
        assertEquals(4, BeginnerPlan.recommendNext(completed).level)
        assertTrue(BeginnerPlan.hasGraduated(completed))
    }

    @Test
    fun recommendNext_isCaseInsensitive() {
        assertEquals(2, BeginnerPlan.recommendNext(setOf("BEGINNER_01_FIRST_PEDALS.ZWO")).level)
    }

    @Test
    fun hasGraduated_partialSet_returnsFalse() {
        assertFalse(BeginnerPlan.hasGraduated(setOf(BeginnerPlan.LEVELS.first().filename)))
        assertFalse(BeginnerPlan.hasGraduated(emptySet()))
    }

    @Test
    fun levelForFilename_resolvesAllLevels_unknownReturnsNull() {
        BeginnerPlan.LEVELS.forEach { level ->
            assertEquals(level, BeginnerPlan.levelForFilename(level.filename))
            assertTrue(BeginnerPlan.isBeginnerWorkout(level.filename))
        }
        assertEquals(null, BeginnerPlan.levelForFilename("sweet_spot_intervals.zwo"))
        assertFalse(BeginnerPlan.isBeginnerWorkout("sweet_spot_intervals.zwo"))
    }

    @Test
    fun beginnerSamples_parseWithExpectedDurationsAndGentleCaps() {
        val samples = listOf(
            WorkoutRepository.SAMPLE_BEGINNER_01 to 900,
            WorkoutRepository.SAMPLE_BEGINNER_02 to 1200,
            WorkoutRepository.SAMPLE_BEGINNER_03 to 1500,
            WorkoutRepository.SAMPLE_BEGINNER_04 to 1800
        )
        val peakCaps = listOf(0.50f, 0.60f, 0.65f, 0.68f)
        samples.forEachIndexed { index, (xml, expectedSeconds) ->
            val workout = ZwoParser.parse(xml)
            assertEquals("sample ${index + 1} duration", expectedSeconds, workout.totalDurationSeconds)
            assertTrue("sample ${index + 1} must have segments", workout.segments.isNotEmpty())
            assertTrue(
                "sample ${index + 1} must have coaching cues",
                workout.textEvents.isNotEmpty()
            )
            workout.segments.forEach { segment ->
                val peak = when (segment) {
                    is WorkoutSegment.SteadyState -> segment.power
                    is WorkoutSegment.Warmup -> maxOf(segment.powerLow, segment.powerHigh)
                    is WorkoutSegment.Cooldown -> maxOf(segment.powerLow, segment.powerHigh)
                    is WorkoutSegment.Ramp -> maxOf(segment.powerLow, segment.powerHigh)
                    else -> 0f // FreeRide: no ERG target
                }
                assertTrue(
                    "sample ${index + 1} peak $peak exceeds gentle cap ${peakCaps[index]}",
                    peak <= peakCaps[index] + 0.001f
                )
            }
        }
    }

    @Test
    fun beginnerSamples_progressionGrowsInDurationAndTss() {
        val parsed = listOf(
            ZwoParser.parse(WorkoutRepository.SAMPLE_BEGINNER_01),
            ZwoParser.parse(WorkoutRepository.SAMPLE_BEGINNER_02),
            ZwoParser.parse(WorkoutRepository.SAMPLE_BEGINNER_03),
            ZwoParser.parse(WorkoutRepository.SAMPLE_BEGINNER_04)
        )
        for (i in 1 until parsed.size) {
            assertTrue(
                "level ${i + 1} duration should exceed level $i",
                parsed[i].totalDurationSeconds > parsed[i - 1].totalDurationSeconds
            )
            assertTrue(
                "level ${i + 1} TSS should exceed level $i",
                parsed[i].estimatedTss > parsed[i - 1].estimatedTss
            )
        }
    }

    @Test
    fun beginnerLevelOne_includesFreeRideForShiftingPractice() {
        val workout = ZwoParser.parse(WorkoutRepository.SAMPLE_BEGINNER_01)
        assertTrue(workout.segments.any { it is WorkoutSegment.FreeRide })
    }

    @Test
    fun pacingGuidance_isNonBlankAndMentionsFrequencyAndAdvancement() {
        assertTrue(BeginnerPlan.PACING_GUIDANCE.isNotBlank())
        assertEquals(BeginnerPlan.PACING_GUIDANCE, BeginnerPlan.pacingGuidance)
        assertTrue(
            "pacingGuidance must specify frequency",
            BeginnerPlan.PACING_GUIDANCE.contains("2–3") ||
                BeginnerPlan.PACING_GUIDANCE.contains("2-3")
        )
        assertTrue(
            "pacingGuidance must mention rest days",
            BeginnerPlan.PACING_GUIDANCE.contains("rest", ignoreCase = true)
        )
        assertTrue(
            "pacingGuidance must state the twice advancement rule",
            BeginnerPlan.PACING_GUIDANCE.contains("twice", ignoreCase = true)
        )
    }

    @Test
    fun levels_allHaveNonBlankGoalsWithAdvancementRule() {
        BeginnerPlan.LEVELS.forEach { level ->
            assertTrue("Level ${level.level} goal must not be blank", level.goal.isNotBlank())
            assertTrue("Level ${level.level} focus must not be blank", level.focus.isNotBlank())
            assertTrue(
                "Level ${level.level} goal should state advancement rule ('twice')",
                level.goal.contains("twice", ignoreCase = true)
            )
        }
    }
}

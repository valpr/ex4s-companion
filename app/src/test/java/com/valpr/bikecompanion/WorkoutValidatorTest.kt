package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutRepository
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.WorkoutValidator
import com.valpr.bikecompanion.workout.ZwoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutValidatorTest {

    private fun steadyWorkout(
        name: String = "Valid",
        segments: List<WorkoutSegment> = listOf(
            WorkoutSegment.SteadyState(durationSeconds = 600, power = 0.65f, targetCadence = 85)
        )
    ) = Workout(name = name, segments = segments)

    @Test
    fun validWorkout_noIssues() {
        assertTrue(WorkoutValidator.validate(steadyWorkout()).isEmpty())
        assertTrue(WorkoutValidator.isValid(steadyWorkout()))
    }

    @Test
    fun blankName_isError() {
        val issues = WorkoutValidator.validate(steadyWorkout(name = "  "))
        assertTrue(issues.any { it.isError && it.field == WorkoutValidator.Field.NAME })
    }

    @Test
    fun emptySegments_isError() {
        val issues = WorkoutValidator.validate(Workout(name = "Empty"))
        assertTrue(issues.any { it.isError && it.field == WorkoutValidator.Field.SEGMENTS })
        assertFalse(WorkoutValidator.isValid(Workout(name = "Empty")))
    }

    @Test
    fun shortAndLongDurations_areErrors() {
        val short = WorkoutValidator.validate(
            steadyWorkout(segments = listOf(WorkoutSegment.SteadyState(4, 0.5f)))
        )
        assertTrue(short.any { it.isError && it.segmentIndex == 0 && it.field == WorkoutValidator.Field.DURATION })

        val long = WorkoutValidator.validate(
            steadyWorkout(segments = listOf(WorkoutSegment.SteadyState(7201, 0.5f)))
        )
        assertTrue(long.any { it.isError && it.field == WorkoutValidator.Field.DURATION })
    }

    @Test
    fun zeroAndExtremePower_areErrors() {
        val zero = WorkoutValidator.validate(
            steadyWorkout(segments = listOf(WorkoutSegment.SteadyState(300, 0.0f)))
        )
        assertTrue(zero.any { it.isError && it.field == WorkoutValidator.Field.POWER })

        val extreme = WorkoutValidator.validate(
            steadyWorkout(segments = listOf(WorkoutSegment.SteadyState(300, 2.5f)))
        )
        assertTrue(extreme.any { it.isError && it.field == WorkoutValidator.Field.POWER })
    }

    @Test
    fun warmupLowAboveHigh_isError_cooldownDescending_isValid() {
        val badWarmup = WorkoutValidator.validate(
            steadyWorkout(
                segments = listOf(WorkoutSegment.Warmup(300, powerLow = 0.8f, powerHigh = 0.5f))
            )
        )
        assertTrue(badWarmup.any { it.isError && it.field == WorkoutValidator.Field.POWER_LOW })

        val cooldown = WorkoutValidator.validate(
            steadyWorkout(
                segments = listOf(WorkoutSegment.Cooldown(300, powerLow = 0.6f, powerHigh = 0.35f))
            )
        )
        assertTrue(cooldown.none { it.isError })
    }

    @Test
    fun outOfRangeCadence_isError_missingCadence_isValid() {
        val bad = WorkoutValidator.validate(
            steadyWorkout(
                segments = listOf(WorkoutSegment.SteadyState(300, 0.5f, targetCadence = 200))
            )
        )
        assertTrue(bad.any { it.isError && it.field == WorkoutValidator.Field.CADENCE })

        val none = WorkoutValidator.validate(
            steadyWorkout(
                segments = listOf(WorkoutSegment.SteadyState(300, 0.5f, targetCadence = null))
            )
        )
        assertTrue(none.none { it.isError })
    }

    @Test
    fun peakAboveErgReach_isWarningOnly() {
        val issues = WorkoutValidator.validate(
            steadyWorkout(segments = listOf(WorkoutSegment.SteadyState(300, 1.5f)))
        )
        assertTrue(issues.none { it.isError })
        assertTrue(issues.any { !it.isError })
        assertTrue(WorkoutValidator.isValid(steadyWorkout(segments = listOf(WorkoutSegment.SteadyState(300, 1.5f)))))
    }

    @Test
    fun everySeededSample_hasZeroErrors() {
        for ((filename, sampleXml) in WorkoutRepository.SAMPLE_FILES) {
            val workout = ZwoParser.parse(sampleXml)
            val errors = WorkoutValidator.validate(workout).filter { it.isError }
            assertEquals("$filename errors: $errors", 0, errors.size)
        }
    }

    @Test
    fun descendingRamp_isValid() {
        val issues = WorkoutValidator.validate(
            steadyWorkout(
                segments = listOf(WorkoutSegment.Ramp(300, powerLow = 0.9f, powerHigh = 0.5f))
            )
        )
        assertTrue(issues.none { it.isError })
    }

    @Test
    fun maxEffortPeak_noErgReachWarning() {
        val issues = WorkoutValidator.validate(
            steadyWorkout(
                segments = listOf(
                    WorkoutSegment.SteadyState(300, 0.5f),
                    WorkoutSegment.MaxEffort(30, targetCadence = 100)
                )
            )
        )
        assertTrue(issues.none { it.isError })
        assertTrue(issues.none { !it.isError })
    }

    @Test
    fun errorIndex_pointsAtOffendingSegment() {
        val issues = WorkoutValidator.validate(
            steadyWorkout(
                segments = listOf(
                    WorkoutSegment.SteadyState(300, 0.5f),
                    WorkoutSegment.SteadyState(300, 0.0f)
                )
            )
        )
        val error = issues.first { it.isError }
        assertEquals(1, error.segmentIndex)
    }
}

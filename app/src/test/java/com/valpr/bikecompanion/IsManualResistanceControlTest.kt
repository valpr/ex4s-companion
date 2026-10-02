package com.valpr.bikecompanion.ui.workout

import com.valpr.bikecompanion.workout.SegmentPosition
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.WorkoutSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsManualResistanceControlTest {

    private val ergWorkout = Workout(
        name = "Erg",
        segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 600, power = 0.65f))
    )

    private fun position(segment: WorkoutSegment) = SegmentPosition(
        segmentIndex = 0,
        segment = segment,
        segmentElapsedSeconds = 10,
        segmentRemainingSeconds = 590,
        totalSegments = 1
    )

    @Test
    fun fullFreeRide_isManual() {
        assertTrue(isManualResistanceControl(WorkoutSessionState(workout = null)))
    }

    @Test
    fun ergSegment_isNotManual() {
        val segment = ergWorkout.segments[0]
        val state = WorkoutSessionState(
            status = SessionStatus.RUNNING,
            workout = ergWorkout,
            elapsedSeconds = 10,
            currentPosition = position(segment)
        )
        assertFalse(isManualResistanceControl(state))
    }

    @Test
    fun freeRideSegment_isManual() {
        val segment = WorkoutSegment.FreeRide(durationSeconds = 120)
        val state = WorkoutSessionState(
            status = SessionStatus.RUNNING,
            workout = Workout(name = "Mixed", segments = listOf(segment)),
            elapsedSeconds = 10,
            currentPosition = position(segment)
        )
        assertTrue(isManualResistanceControl(state))
    }

    @Test
    fun preTick_fallsBackToSegmentAtElapsed() {
        val state = WorkoutSessionState(
            status = SessionStatus.RUNNING,
            workout = ergWorkout,
            elapsedSeconds = 10,
            currentPosition = null,
            ergDecision = null
        )
        assertFalse(isManualResistanceControl(state))
    }

    @Test
    fun completedWithoutDecision_isManual() {
        val state = WorkoutSessionState(
            status = SessionStatus.COMPLETED,
            workout = ergWorkout,
            elapsedSeconds = 600,
            currentPosition = null,
            ergDecision = null
        )
        assertTrue(isManualResistanceControl(state))
    }
}

package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutModelBoundaryTest {

    private val workout = Workout(
        name = "Boundary",
        segments = listOf(
            WorkoutSegment.SteadyState(durationSeconds = 60, power = 1.0f),
            WorkoutSegment.FreeRide(durationSeconds = 60)
        )
    )

    @Test
    fun getSegmentAtTime_boundaries() {
        assertEquals(0, workout.getSegmentAtTime(0)?.segmentIndex)
        assertEquals(0, workout.getSegmentAtTime(59)?.segmentIndex)
        assertEquals(1, workout.getSegmentAtTime(60)?.segmentIndex)
        assertEquals(1, workout.getSegmentAtTime(119)?.segmentIndex)
        assertNull(workout.getSegmentAtTime(120)) // == totalDuration -> ended
        assertNull(workout.getSegmentAtTime(-1))
    }

    @Test
    fun targetWattsAt_freeRideNullAndEndedNull() {
        assertEquals(200, workout.targetWattsAt(200, 10))
        assertNull(workout.targetWattsAt(200, 70)) // FreeRide -> null
        assertNull(workout.targetWattsAt(200, 120)) // ended -> null
    }

    @Test
    fun targetWattsAt_intensityScaleApplies() {
        assertEquals(180, workout.targetWattsAt(200, 10, 0.9f))
    }
}

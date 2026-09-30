package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.ZwoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZwoParserEdgeTest {

    @Test
    fun intervals_repeatZero_coercesToOneRepeat() {
        val xml = """
            <workout_file><name>R0</name><workout>
                <IntervalsT Repeat="0" OnDuration="30" OffDuration="30" OnPower="1.0" OffPower="0.5"/>
            </workout></workout_file>
        """.trimIndent()
        val workout = ZwoParser.parse(xml)
        // coerceAtLeast(1): one ON + one OFF, never zero-duration garbage
        assertEquals(2, workout.segments.size)
        assertEquals(60, workout.totalDurationSeconds)
    }

    @Test
    fun intervals_repeatOne_flattensToSingleCycle() {
        val xml = """
            <workout_file><name>R1</name><workout>
                <IntervalsT Repeat="1" OnDuration="40" OffDuration="20" OnPower="1.2" OffPower="0.6"/>
            </workout></workout_file>
        """.trimIndent()
        val workout = ZwoParser.parse(xml)
        assertEquals(2, workout.segments.size)
        assertEquals(1.2f, (workout.segments[0] as WorkoutSegment.SteadyState).power, 0.001f)
        assertEquals(0.6f, (workout.segments[1] as WorkoutSegment.SteadyState).power, 0.001f)
    }

    @Test
    fun intervals_missingPowers_defaultToZero_notNaN() {
        val xml = """
            <workout_file><name>NoPow</name><workout>
                <IntervalsT Repeat="2" OnDuration="30" OffDuration="30"/>
            </workout></workout_file>
        """.trimIndent()
        val workout = ZwoParser.parse(xml)
        assertEquals(4, workout.segments.size)
        for (seg in workout.segments) {
            val power = (seg as WorkoutSegment.SteadyState).power
            assertTrue(power.isFinite())
            assertEquals(0f, power, 0.001f)
        }
        // Zero-power target resolves to 0W, never NaN/null-crash downstream
        assertEquals(0, workout.targetWattsAt(200, 10))
    }

    @Test
    fun intervals_nullCadenceResting_offKeepsNullCadence() {
        val xml = """
            <workout_file><name>NoRestCad</name><workout>
                <IntervalsT Repeat="1" OnDuration="30" OffDuration="30" OnPower="1.0" OffPower="0.5" Cadence="95"/>
            </workout></workout_file>
        """.trimIndent()
        val workout = ZwoParser.parse(xml)
        val on = workout.segments[0] as WorkoutSegment.SteadyState
        val off = workout.segments[1] as WorkoutSegment.SteadyState
        assertEquals(95, on.targetCadence)
        assertNull(off.targetCadence)
    }

    @Test
    fun intervals_zeroDurations_emitNoSegments() {
        val xml = """
            <workout_file><name>ZeroDur</name><workout>
                <IntervalsT Repeat="3" OnDuration="0" OffDuration="0" OnPower="1.0" OffPower="0.5"/>
            </workout></workout_file>
        """.trimIndent()
        val workout = ZwoParser.parse(xml)
        assertTrue(workout.segments.isEmpty())
        assertEquals(0, workout.totalDurationSeconds)
    }
}

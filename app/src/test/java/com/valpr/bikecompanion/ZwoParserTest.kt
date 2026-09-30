package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.ZwoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZwoParserTest {

    @Test
    fun parse_standardZwiftWorkout_extractsMetadataAndSegments() {
        val xml = """
            <workout_file>
                <author>Coach Zwift</author>
                <name>Sweet Spot Intervals</name>
                <description>A classic threshold and sweet spot workout.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Intervals"/>
                    <tag name="Threshold"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.50" PowerHigh="0.75" Cadence="85"/>
                    <SteadyState Duration="600" Power="0.90" Cadence="90"/>
                    <Cooldown Duration="300" PowerLow="0.75" PowerHigh="0.45" Cadence="80"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)

        assertEquals("Sweet Spot Intervals", workout.name)
        assertEquals("Coach Zwift", workout.author)
        assertEquals("A classic threshold and sweet spot workout.", workout.description)
        assertEquals("bike", workout.sportType)
        assertEquals(listOf("Intervals", "Threshold"), workout.tags)
        assertEquals(3, workout.segments.size)
        assertEquals(1200, workout.totalDurationSeconds)

        // Warmup segment
        val warmup = workout.segments[0] as WorkoutSegment.Warmup
        assertEquals(300, warmup.durationSeconds)
        assertEquals(0.50f, warmup.powerLow, 0.001f)
        assertEquals(0.75f, warmup.powerHigh, 0.001f)
        assertEquals(85, warmup.targetCadence)

        // SteadyState segment
        val steady = workout.segments[1] as WorkoutSegment.SteadyState
        assertEquals(600, steady.durationSeconds)
        assertEquals(0.90f, steady.power, 0.001f)
        assertEquals(90, steady.targetCadence)

        // Cooldown segment
        val cooldown = workout.segments[2] as WorkoutSegment.Cooldown
        assertEquals(300, cooldown.durationSeconds)
        assertEquals(0.75f, cooldown.powerLow, 0.001f)
        assertEquals(0.45f, cooldown.powerHigh, 0.001f)
        assertEquals(80, cooldown.targetCadence)
    }

    @Test
    fun parse_intervalsT_flattensRepeatsIntoDiscreteSteps() {
        val xml = """
            <workout_file>
                <name>Microbursts</name>
                <workout>
                    <IntervalsT Repeat="3" OnDuration="40" OffDuration="20" OnPower="1.30" OffPower="0.50" Cadence="100" CadenceResting="80"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)

        // 3 repeats of (On + Off) = 6 discrete segments
        assertEquals(6, workout.segments.size)
        assertEquals(180, workout.totalDurationSeconds)

        for (i in 0 until 3) {
            val onStep = workout.segments[i * 2] as WorkoutSegment.SteadyState
            assertEquals(40, onStep.durationSeconds)
            assertEquals(1.30f, onStep.power, 0.001f)
            assertEquals(100, onStep.targetCadence)

            val offStep = workout.segments[i * 2 + 1] as WorkoutSegment.SteadyState
            assertEquals(20, offStep.durationSeconds)
            assertEquals(0.50f, offStep.power, 0.001f)
            assertEquals(80, offStep.targetCadence)
        }
    }

    @Test
    fun parse_caseInsensitiveAttributes_parsesCorrectly() {
        // Various generators use lower-case or mixed-case attributes
        val xml = """
            <workout_file>
                <name>Case Test</name>
                <workout>
                    <warmup duration="180" powerlow="0.40" powerhigh="0.70" cadence="85"/>
                    <ramp duration="120" powerLow="0.70" powerHigh="1.05"/>
                    <steadystate duration="200" power="0.88"/>
                    <freeride duration="300" flatroad="1"/>
                    <maxeffort duration="30"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)
        assertEquals(5, workout.segments.size)

        val warmup = workout.segments[0] as WorkoutSegment.Warmup
        assertEquals(180, warmup.durationSeconds)
        assertEquals(0.40f, warmup.powerLow, 0.001f)
        assertEquals(0.70f, warmup.powerHigh, 0.001f)

        val ramp = workout.segments[1] as WorkoutSegment.Ramp
        assertEquals(120, ramp.durationSeconds)
        assertEquals(0.70f, ramp.powerLow, 0.001f)
        assertEquals(1.05f, ramp.powerHigh, 0.001f)

        val steady = workout.segments[2] as WorkoutSegment.SteadyState
        assertEquals(200, steady.durationSeconds)
        assertEquals(0.88f, steady.power, 0.001f)

        val freeRide = workout.segments[3] as WorkoutSegment.FreeRide
        assertEquals(300, freeRide.durationSeconds)
        assertTrue(freeRide.flatRoad)
        assertFalse(freeRide.isErgEnabled)

        val maxEffort = workout.segments[4] as WorkoutSegment.MaxEffort
        assertEquals(30, maxEffort.durationSeconds)
        assertFalse(maxEffort.isErgEnabled)
    }

    @Test
    fun parse_textEvents_properlyScopesOffsets() {
        val xml = """
            <workout_file>
                <name>Cues Test</name>
                <workout>
                    <Warmup Duration="100" PowerLow="0.5" PowerHigh="0.8">
                        <textevent timeoffset="10" message="Warmup starts!"/>
                        <textevent timeoffset="50" message="Halfway into warmup!"/>
                    </Warmup>
                    <SteadyState Duration="200" Power="1.0">
                        <textevent timeoffset="0" message="Threshold start!"/>
                    </SteadyState>
                    <textevent timeoffset="250" message="Workout overall announcement!"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)
        assertEquals(4, workout.textEvents.size)

        // Nested in Warmup (starts at t=0)
        assertEquals(10, workout.textEvents[0].timeOffsetSeconds)
        assertEquals("Warmup starts!", workout.textEvents[0].message)

        assertEquals(50, workout.textEvents[1].timeOffsetSeconds)
        assertEquals("Halfway into warmup!", workout.textEvents[1].message)

        // Nested in SteadyState (starts at t=100) -> 100 + 0 = 100
        assertEquals(100, workout.textEvents[2].timeOffsetSeconds)
        assertEquals("Threshold start!", workout.textEvents[2].message)

        // Root textevent (at t=250)
        assertEquals(250, workout.textEvents[3].timeOffsetSeconds)
        assertEquals("Workout overall announcement!", workout.textEvents[3].message)
    }

    @Test
    fun parse_intervalst_distributesNestedCuesPerRepeat() {
        val xml = """
            <workout_file>
                <name>Repeat Cues</name>
                <workout>
                    <IntervalsT Repeat="3" OnDuration="60" OffDuration="60" OnPower="1.0" OffPower="0.5">
                        <textevent timeoffset="5" message="Go!"/>
                    </IntervalsT>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)
        assertEquals(6, workout.segments.size)
        assertEquals(3, workout.textEvents.size)
        assertEquals(5, workout.textEvents[0].timeOffsetSeconds)
        assertEquals(125, workout.textEvents[1].timeOffsetSeconds)
        assertEquals(245, workout.textEvents[2].timeOffsetSeconds)
    }

    @Test
    fun workout_targetWattsAt_calculatesCorrectFTPWattages() {
        val xml = """
            <workout_file>
                <name>Math Test</name>
                <workout>
                    <Warmup Duration="100" PowerLow="0.50" PowerHigh="1.00"/>
                    <SteadyState Duration="100" Power="0.90"/>
                    <FreeRide Duration="100"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)
        val ftp = 200 // 200W FTP

        // At t=0 (start of warmup): 0.50 * 200 = 100W
        assertEquals(100, workout.targetWattsAt(ftp, 0))

        // At t=50 (halfway through warmup): (0.50 + 0.5 * 0.50) * 200 = 0.75 * 200 = 150W
        assertEquals(150, workout.targetWattsAt(ftp, 50))

        // At t=100 (start of steady state): 0.90 * 200 = 180W
        assertEquals(180, workout.targetWattsAt(ftp, 100))

        // At t=150 (mid steady state): 180W
        assertEquals(180, workout.targetWattsAt(ftp, 150))

        // At t=200 (FreeRide segment): ERG disabled, returns null
        assertNull(workout.targetWattsAt(ftp, 200))

        // At t=350 (past workout end): returns null
        assertNull(workout.targetWattsAt(ftp, 350))
    }

    @Test
    fun workout_estimatedTss_computesReasonableScore() {
        val xml = """
            <workout_file>
                <name>Hour of Power</name>
                <workout>
                    <SteadyState Duration="3600" Power="1.00"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)
        // 1 hour at 100% FTP (IF = 1.0) must equal exactly 100 TSS
        assertEquals(100.0, workout.estimatedTss, 0.01)
    }

    @Test
    fun workout_targetWattsAt_withIntensityScale_scalesTargetPower() {
        val xml = """
            <workout_file>
                <name>Scale Test</name>
                <workout>
                    <SteadyState Duration="300" Power="1.00"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = ZwoParser.parse(xml)
        val ftp = 200

        // Standard 100% intensity
        assertEquals(200, workout.targetWattsAt(ftp, 100, intensityScale = 1.0f))

        // Dynamic HR Capping / 10% reduction -> 180W
        assertEquals(180, workout.targetWattsAt(ftp, 100, intensityScale = 0.90f))

        // +5% intensity bias -> 210W
        assertEquals(210, workout.targetWattsAt(ftp, 100, intensityScale = 1.05f))
    }

    @Test
    fun parseSafe_malformedXml_returnsFailureWithoutThrowing() {
        val corruptedXml = "<workout_file><name>Broken</name><workout><SteadyState Duration=\"300\"" // unclosed tags

        val result = ZwoParser.parseSafe(corruptedXml)
        assertTrue("Malformed XML should return failure", result.isFailure)
    }
}

package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.WorkoutRepository
import com.valpr.bikecompanion.workout.ZwoParser
import com.valpr.bikecompanion.workout.ZwoWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZwoWriterTest {

    @Test
    fun serialize_allSegmentTypes_roundTrip() {
        val xml = """
            <workout_file>
                <author>Tester</author>
                <name>All Types &amp; More</name>
                <description>Escaping &lt;test&gt;</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="HIIT"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.60" Cadence="85">
                        <textevent timeoffset="10" message="Warm up"/>
                    </Warmup>
                    <SteadyState Duration="120" Power="1.10" Cadence="95"/>
                    <Ramp Duration="180" PowerLow="0.70" PowerHigh="0.90"/>
                    <FreeRide Duration="120" Cadence="80"/>
                    <MaxEffort Duration="30" Cadence="100"/>
                    <Cooldown Duration="300" PowerLow="0.60" PowerHigh="0.35" Cadence="80"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val parsed = ZwoParser.parse(xml)
        val serialized = ZwoWriter.serialize(parsed)
        val reparsed = ZwoParser.parse(serialized)

        assertEquals(parsed.name, reparsed.name)
        assertEquals(parsed.description, reparsed.description)
        assertEquals(parsed.tags, reparsed.tags)
        assertEquals(parsed.totalDurationSeconds, reparsed.totalDurationSeconds)
        assertEquals(parsed.segments.size, reparsed.segments.size)
        assertEquals(parsed.textEvents, reparsed.textEvents)
        assertEquals(parsed.estimatedTss, reparsed.estimatedTss, 0.001)
    }

    @Test
    fun serialize_everySeededSample_roundTripsStably() {
        for ((filename, sampleXml) in WorkoutRepository.SAMPLE_FILES) {
            val parsed = ZwoParser.parse(sampleXml)
            val reparsed = ZwoParser.parse(ZwoWriter.serialize(parsed))
            assertEquals("$filename segment count", parsed.segments.size, reparsed.segments.size)
            assertEquals("$filename duration", parsed.totalDurationSeconds, reparsed.totalDurationSeconds)
            assertEquals("$filename cues", parsed.textEvents, reparsed.textEvents)
            assertEquals("$filename TSS", parsed.estimatedTss, reparsed.estimatedTss, 0.001)
        }
    }

    @Test
    fun distributeCues_clampsPastEndEventsToLastSegment() {
        val xml = """
            <workout_file>
                <name>Clamp</name>
                <workout>
                    <SteadyState Duration="60" Power="0.50"/>
                    <textevent timeoffset="999" message="Late"/>
                </workout>
            </workout_file>
        """.trimIndent()
        // Root-level textevent under <workout> with offset past the end.
        val parsed = ZwoParser.parse(xml)
        val reparsed = ZwoParser.parse(ZwoWriter.serialize(parsed))
        assertEquals(1, reparsed.textEvents.size)
        assertEquals(60, reparsed.textEvents[0].timeOffsetSeconds)
    }

    @Test
    fun serializeStructured_intervalsWriteSingleBlockAndRoundTrip() {
        val items = listOf(
            ZwoWriter.StructureItem.Intervals(
                repeat = 3,
                onDurationSeconds = 40,
                onPower = 1.12f,
                offDurationSeconds = 60,
                offPower = 0.55f,
                cadence = 95,
                restingCadence = 80,
                cues = listOf(
                    com.valpr.bikecompanion.workout.WorkoutTextEvent(5, "Go!")
                ),
                restCues = listOf(
                    com.valpr.bikecompanion.workout.WorkoutTextEvent(5, "Easy!")
                )
            )
        )
        val xml = ZwoWriter.serializeStructured(
            name = "Intervals",
            author = "Tester",
            description = "",
            sportType = "bike",
            tags = emptyList(),
            items = items
        )
        assertTrue(xml.contains("<IntervalsT"))
        // No flattened SteadyState pairs: exactly one block element.
        assertEquals(1, "<IntervalsT".toRegex().findAll(xml).count())
        val reparsed = ZwoParser.parse(xml)
        assertEquals(6, reparsed.segments.size)
        assertEquals(3 * (40 + 60), reparsed.totalDurationSeconds)
        // Work + rest cue per repeat, at matching absolute offsets.
        assertEquals(6, reparsed.textEvents.size)
        assertEquals(
            listOf(5, 45, 105, 145, 205, 245),
            reparsed.textEvents.map { it.timeOffsetSeconds }
        )
    }
}

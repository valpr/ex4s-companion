package com.valpr.bikecompanion

import com.valpr.bikecompanion.history.BeginnerFilenameMatcher
import com.valpr.bikecompanion.history.CompletedRide
import com.valpr.bikecompanion.history.StoredSample
import com.valpr.bikecompanion.history.TcxExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TcxExporterTest {

    private fun ride(
        name: String = "Sweet Spot Intervals (30 min)",
        startMs: Long = 1_700_000_000_000L,
        duration: Int = 5
    ) = CompletedRide(
        id = "ride_$startMs",
        workoutName = name,
        startTimeEpochMs = startMs,
        totalDurationSeconds = duration,
        totalDistanceKm = 0.5,
        avgWatts = 150,
        maxWatts = 200,
        avgCadence = 85,
        maxCadence = 95,
        avgHeartRate = 140,
        maxHeartRate = 160,
        totalWorkKj = 45.0,
        totalCaloriesKcal = 45,
        samples = (1..duration).map {
            StoredSample(
                elapsedSeconds = it,
                watts = 150,
                cadenceRpm = 85,
                resistance = 10,
                speedKmh = 25.0,
                heartRateBpm = 140
            )
        }
    )

    @Test
    fun export_containsRequiredTcxStructure() {
        val tcx = TcxExporter.export(ride())

        assertTrue(tcx.contains("<TrainingCenterDatabase"))
        assertTrue(tcx.contains("<Activity Sport=\"Biking\">"))
        assertTrue(tcx.contains("<TotalTimeSeconds>5</TotalTimeSeconds>"))
        assertTrue(tcx.contains("<Watts>150</Watts>"))
        assertTrue(tcx.contains("<Cadence>85</Cadence>"))
        assertTrue(tcx.contains("<HeartRateBpm><Value>140</Value></HeartRateBpm>"))
        assertTrue(tcx.contains("<AvgWatts>150</AvgWatts>"))
        assertTrue(tcx.contains("<MaxWatts>200</MaxWatts>"))
    }

    @Test
    fun export_noHr_omitsHeartRateElements() {
        val noHr = ride().copy(samples = ride().samples.map { it.copy(heartRateBpm = 0) })
        val tcx = TcxExporter.export(noHr)

        assertFalse(tcx.contains("HeartRateBpm"))
        // Power still present.
        assertTrue(tcx.contains("<Watts>150</Watts>"))
    }

    @Test
    fun export_emptySamples_emitsSingleTrackpoint() {
        val tcx = TcxExporter.export(ride().copy(samples = emptyList()))

        assertEquals(1, tcx.split("<Trackpoint>").size - 1)
    }

    @Test
    fun export_clampsSampleBeyondDuration() {
        val overrun = ride(duration = 5).copy(
            samples = ride(duration = 5).samples + StoredSample(elapsedSeconds = 5000, watts = 99)
        )
        val tcx = TcxExporter.export(overrun)

        // Out-of-window offset must not leak a far-future timestamp; count stays bounded.
        assertTrue(tcx.split("<Trackpoint>").size - 1 <= 6)
    }

    @Test
    fun export_escapesXmlInWorkoutName() {
        val tcx = TcxExporter.export(ride(name = "Ride < hard & \"fast\" >"))
        assertTrue(tcx.contains("Ride &lt; hard &amp; &quot;fast&quot; &gt;"))
    }

    @Test
    fun fileName_isSlugifiedAndDated() {
        val name = TcxExporter.fileNameFor(ride(name = "Sweet Spot Intervals (30 min)"))
        assertTrue(name.endsWith(".tcx"))
        assertTrue(name.contains("sweet-spot-intervals"))
    }

    @Test
    fun matcher_beginnerLevels_resolve() {
        assertEquals(
            "beginner_01_first_pedals.zwo",
            BeginnerFilenameMatcher.filenameFor("First Pedals (15 min) - Beginner 1/4", 900)
        )
        assertEquals(
            "beginner_04_ready_for_more.zwo",
            BeginnerFilenameMatcher.filenameFor("Ready for More (30 min) - Beginner 4/4", 1800)
        )
    }

    @Test
    fun matcher_unknown_returnsNull() {
        assertNull(BeginnerFilenameMatcher.filenameFor("My Custom Endurance Ride", 3600))
    }
}

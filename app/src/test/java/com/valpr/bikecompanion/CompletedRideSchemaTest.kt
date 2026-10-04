package com.valpr.bikecompanion

import com.valpr.bikecompanion.history.CompletedRide
import com.valpr.bikecompanion.history.RideHeader
import com.valpr.bikecompanion.workout.WorkoutMetricSample
import com.valpr.bikecompanion.workout.WorkoutSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedRideSchemaTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    private fun summary(startMs: Long = 1_700_000_000_000L) = WorkoutSummary(
        workoutName = "Schema Ride",
        totalDurationSeconds = 600,
        totalDistanceKm = 5.0,
        avgWatts = 120,
        maxWatts = 180,
        avgCadence = 80,
        maxCadence = 95,
        avgHeartRate = 130,
        maxHeartRate = 150,
        totalWorkKj = 72.0,
        totalCaloriesKcal = 72,
        samples = listOf(
            WorkoutMetricSample(1, 120, 120, 80, 85, 8, 20.0, 130)
        ),
        startTimeEpochMs = startMs
    )

    @Test
    fun fromSummary_stampsSchemaVersionAndHeaderAgrees() {
        val ride = CompletedRide.fromSummary(summary())
        assertEquals(CompletedRide.SCHEMA_VERSION, ride.schemaVersion)
        val header = ride.header()
        assertEquals(ride.id, header.id)
        assertEquals(ride.avgWatts, header.avgWatts)
        assertEquals(ride.maxWatts, header.maxWatts)
        assertEquals(ride.totalWorkKj, header.totalWorkKj, 0.0)
        assertEquals(CompletedRide.SCHEMA_VERSION, header.schemaVersion)
    }

    @Test
    fun legacyJsonWithoutVersion_decodesWithDefaults() {
        val legacy = """{"id":"ride_1","workoutName":"Old"}"""
        val header = json.decodeFromString(RideHeader.serializer(), legacy)
        assertEquals("ride_1", header.id)
        assertEquals(0, header.schemaVersion)
        val ride = json.decodeFromString(CompletedRide.serializer(), legacy)
        assertEquals("ride_1", ride.id)
        assertEquals(0, ride.schemaVersion)
    }

    @Test
    fun fallbackId_isStableAndFilesystemSafe() {
        val first = CompletedRide.fallbackIdFor(summary(startMs = 0L))
        val second = CompletedRide.fallbackIdFor(summary(startMs = 0L))
        assertEquals(first, second)
        assertTrue(first.startsWith("ride_h"))
        assertTrue(first.matches(Regex("ride_h[0-9a-f]+")))
        // Distinct summaries get distinct ids.
        val other = CompletedRide.fallbackIdFor(summary(startMs = 0L).copy(workoutName = "Other"))
        assertTrue(first != other)
    }
}

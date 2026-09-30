package com.valpr.bikecompanion

import com.valpr.bikecompanion.health.HealthConnectManager
import com.valpr.bikecompanion.workout.WorkoutMetricSample
import com.valpr.bikecompanion.workout.WorkoutSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HealthConnectManagerTest {

    private fun sample(
        elapsed: Int,
        watts: Int = 200,
        hr: Int = 140
    ) = WorkoutMetricSample(
        elapsedSeconds = elapsed,
        watts = watts,
        targetWatts = 200,
        cadenceRpm = 85,
        targetCadence = null,
        resistance = 12,
        speedKmh = 30.0,
        heartRateBpm = hr
    )

    private fun summary(
        durationSeconds: Int = 600,
        startMs: Long = 1_716_000_000_000L,
        samples: List<WorkoutMetricSample> = (1..600).map { sample(it) }
    ) = WorkoutSummary(
        workoutName = "Test Ride",
        totalDurationSeconds = durationSeconds,
        totalDistanceKm = 5.0,
        avgWatts = 200,
        maxWatts = 250,
        avgCadence = 85,
        maxCadence = 95,
        avgHeartRate = 140,
        maxHeartRate = 160,
        totalWorkKj = 120.0,
        totalCaloriesKcal = 120,
        samples = samples,
        startTimeEpochMs = startMs
    )

    @Test
    fun testActiveCalories_subtractsRestingBurn() {
        // 120 kJ over 600s at 75kg: resting = 75*600/3600 = 12.5 kcal
        val active = HealthConnectManager.activeCaloriesKcal(
            totalWorkKj = 120.0,
            weightKg = 75.0,
            durationSeconds = 600
        )
        assertTrue(abs(active - 107.5) < 0.001)
    }

    @Test
    fun testActiveCalories_floorsAtZeroForTinyWorkouts() {
        // 1 kJ over 1hr at 75kg: resting (75) exceeds total -> 0, never negative
        val active = HealthConnectManager.activeCaloriesKcal(
            totalWorkKj = 1.0,
            weightKg = 75.0,
            durationSeconds = 3600
        )
        assertEquals(0.0, active, 0.0)
    }

    @Test
    fun testPlanRecords_clampsSampleTimestampsIntoSessionWindow() {
        val startMs = 1_716_000_000_000L
        val s = summary(
            durationSeconds = 600,
            startMs = startMs,
            samples = listOf(
                sample(elapsed = 1, hr = 140),
                sample(elapsed = 599, hr = 150),
                // Way outside the window — must be clamped, never rejected
                sample(elapsed = 99999, hr = 160)
            )
        )
        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertEquals(startMs, plan.sessionStartEpochMs)
        assertEquals(startMs + 600_000L, plan.sessionEndEpochMs)
        assertEquals(3, plan.hrPoints.size)
        for (point in plan.hrPoints) {
            assertTrue(point.timeEpochMs in plan.sessionStartEpochMs..plan.sessionEndEpochMs)
        }
        assertEquals(startMs + 600_000L, plan.hrPoints.last().timeEpochMs)
        // Out-of-window sample lands in its own 30-min bucket but its timestamp
        // is clamped into the session window.
        assertEquals(2, plan.powerChunks.size)
        assertEquals(2, plan.powerChunks[0].size)
        assertEquals(1, plan.powerChunks[1].size)
    }

    @Test
    fun testPlanRecords_chunksLongSessionsForBinderLimit() {
        // 3700 one-second samples (~61 min) -> 3 chunks keyed by elapsed/POWER_CHUNK_SECONDS.
        val chunk = HealthConnectManager.POWER_CHUNK_SECONDS
        val startMs = 1_716_000_000_000L
        val samples = (1..3700).map { sample(elapsed = it) }
        val s = summary(durationSeconds = 3700, startMs = startMs, samples = samples)

        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertEquals(3, plan.powerChunks.size)
        assertEquals(chunk - 1, plan.powerChunks[0].size)
        assertEquals(chunk, plan.powerChunks[1].size)
        assertEquals(3700 - (chunk - 1) - chunk, plan.powerChunks[2].size)
        val total = plan.powerChunks.sumOf { it.size }
        assertEquals(3700, total)
    }

    @Test
    fun testPlanRecords_skipsHeartRateWhenNoHrSamples() {
        val s = summary(samples = (1..60).map { sample(elapsed = it, hr = 0) })
        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertTrue(plan.hrPoints.isEmpty())
        assertEquals(1, plan.powerChunks.size)
    }

    @Test
    fun testPlanRecords_emptySamplesStillPlansSessionAndCalories() {
        val s = summary(durationSeconds = 120, samples = emptyList())
        // Simulate a 120s coast at 0W: kJ math lives in the summary; plan must not crash
        val plan = HealthConnectManager.planRecords(
            s.copy(totalWorkKj = 0.0),
            75.0
        )

        assertTrue(plan.hrPoints.isEmpty())
        assertTrue(plan.powerChunks.isEmpty())
        assertEquals(0.0, plan.activeCaloriesKcal, 0.0)
        assertTrue(plan.sessionEndEpochMs > plan.sessionStartEpochMs)
    }

    @Test
    fun testPlanRecords_missingStartTimeFallsBackToDurationWindow() {
        val s = summary(durationSeconds = 300, startMs = 0L)
        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertEquals(300_000L, plan.sessionEndEpochMs - plan.sessionStartEpochMs)
    }

    @Test
    fun testClampRecordWindow_singletonGetsOneSecondWindow() {
        val start = 1_716_000_000_000L
        val end = start + 600_000L
        val window = HealthConnectManager.clampRecordWindow(start + 1000L, start + 1000L, start, end)
        assertTrue(window != null)
        assertEquals(1000L, window!!.second - window.first)
    }

    @Test
    fun testClampRecordWindow_dropsBucketAtSessionEnd() {
        val start = 1_716_000_000_000L
        val end = start + 600_000L
        // Fully-clamped singleton at end (out-of-window sample) must be dropped,
        // never emitted as an interval outside the parent session.
        assertEquals(null, HealthConnectManager.clampRecordWindow(end, end, start, end))
    }

    @Test
    fun testClampRecordWindow_clampsEndToSession() {
        val start = 1_716_000_000_000L
        val end = start + 600_000L
        val window = HealthConnectManager.clampRecordWindow(start + 599_000L, end + 60_000L, start, end)
        assertTrue(window != null)
        assertTrue(window!!.first >= start && window.second <= end)
        assertTrue(window.second > window.first)
    }
}

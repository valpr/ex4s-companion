package com.valpr.bikecompanion

import com.valpr.bikecompanion.health.HealthConnectManager
import com.valpr.bikecompanion.workout.WorkoutMetricSample
import com.valpr.bikecompanion.workout.WorkoutSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HealthConnectManagerTest {

    private fun sample(elapsed: Int, watts: Int = 200, hr: Int = 140) = WorkoutMetricSample(
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

    @Test
    fun testPlanRecords_includesSpeedAndCadenceChunks() {
        val startMs = 1_716_000_000_000L
        val samples = (1..100).map {
            sample(elapsed = it, watts = 200, hr = 140).copy(
                speedKmh = 32.5,
                cadenceRpm = 90
            )
        }
        val s = summary(durationSeconds = 100, startMs = startMs, samples = samples)
        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertEquals(1, plan.speedChunks.size)
        assertEquals(100, plan.speedChunks[0].size)
        assertEquals(32.5, plan.speedChunks[0][0].speedKmh, 0.001)

        assertEquals(1, plan.cadenceChunks.size)
        assertEquals(100, plan.cadenceChunks[0].size)
        assertEquals(90, plan.cadenceChunks[0][0].cadenceRpm)
    }

    @Test
    fun testPlanRecords_totalDistanceAndTotalCalories() {
        val s = summary(durationSeconds = 600).copy(
            totalDistanceKm = 8.5,
            totalWorkKj = 150.0
        )
        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertEquals(8500.0, plan.totalDistanceMeters, 0.001)
        assertEquals(150.0, plan.totalCaloriesKcal, 0.001)
    }

    @Test
    fun testPlanRecords_freeRideProducesSingleSegmentAndLap() {
        val startMs = 1_716_000_000_000L
        val s = summary(durationSeconds = 600, startMs = startMs).copy(
            totalDistanceKm = 5.0,
            workout = null
        )
        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertEquals(1, plan.segments.size)
        assertEquals(startMs, plan.segments[0].startEpochMs)
        assertEquals(startMs + 600_000L, plan.segments[0].endEpochMs)
        assertEquals(
            androidx.health.connect.client.records.ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY,
            plan.segments[0].segmentType
        )

        assertEquals(1, plan.laps.size)
        assertEquals(startMs, plan.laps[0].startEpochMs)
        assertEquals(startMs + 600_000L, plan.laps[0].endEpochMs)
        assertEquals(5000.0, plan.laps[0].distanceMeters!!, 0.001)
    }

    @Test
    fun testPlanRecords_structuredWorkoutMapsIntervalSegmentsAndLaps() {
        val startMs = 1_716_000_000_000L
        val structured = com.valpr.bikecompanion.workout.Workout(
            name = "Test Intervals",
            segments = listOf(
                com.valpr.bikecompanion.workout.WorkoutSegment.Warmup(120, 0.4f, 0.6f),
                com.valpr.bikecompanion.workout.WorkoutSegment.SteadyState(180, 0.9f),
                com.valpr.bikecompanion.workout.WorkoutSegment.SteadyState(120, 0.5f), // Recovery
                com.valpr.bikecompanion.workout.WorkoutSegment.Cooldown(60, 0.5f, 0.3f)
            )
        )
        val s = summary(durationSeconds = 480, startMs = startMs).copy(workout = structured)
        val plan = HealthConnectManager.planRecords(s, 75.0)

        assertEquals(4, plan.segments.size)
        assertEquals(4, plan.laps.size)

        // Warmup: biking stationary
        assertEquals(startMs, plan.segments[0].startEpochMs)
        assertEquals(startMs + 120_000L, plan.segments[0].endEpochMs)
        assertEquals(
            androidx.health.connect.client.records.ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY,
            plan.segments[0].segmentType
        )

        // Work: biking stationary
        assertEquals(startMs + 120_000L, plan.segments[1].startEpochMs)
        assertEquals(startMs + 300_000L, plan.segments[1].endEpochMs)
        assertEquals(
            androidx.health.connect.client.records.ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY,
            plan.segments[1].segmentType
        )

        // Recovery: rest
        assertEquals(startMs + 300_000L, plan.segments[2].startEpochMs)
        assertEquals(startMs + 420_000L, plan.segments[2].endEpochMs)
        assertEquals(
            androidx.health.connect.client.records.ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST,
            plan.segments[2].segmentType
        )

        // Cooldown: biking stationary
        assertEquals(startMs + 420_000L, plan.segments[3].startEpochMs)
        assertEquals(startMs + 480_000L, plan.segments[3].endEpochMs)
    }

    @Test
    fun testIsDuplicateSession_detectsMatchingClientIdOrWindow() {
        val existing = listOf(
            com.valpr.bikecompanion.health.SessionRecordSummary(
                clientRecordId = "ride_1716000000000",
                startTimeEpochMs = 1_716_000_000_000L,
                endTimeEpochMs = 1_716_000_600_000L,
                title = "Morning Ride"
            )
        )

        // Matches clientRecordId
        assertTrue(
            HealthConnectManager.isDuplicateSession(
                existing = existing,
                expectedClientId = "ride_1716000000000",
                expectedStartMs = 1_716_000_000_000L,
                expectedEndMs = 1_716_000_600_000L,
                expectedTitle = "Different Title"
            )
        )

        // Matches time window + title when clientRecordId differs/is null
        assertTrue(
            HealthConnectManager.isDuplicateSession(
                existing = listOf(existing[0].copy(clientRecordId = null)),
                expectedClientId = "ride_other",
                expectedStartMs = 1_716_000_000_000L,
                expectedEndMs = 1_716_000_600_000L,
                expectedTitle = "Morning Ride"
            )
        )

        // Completely different session
        org.junit.Assert.assertFalse(
            HealthConnectManager.isDuplicateSession(
                existing = existing,
                expectedClientId = "ride_999",
                expectedStartMs = 1_717_000_000_000L,
                expectedEndMs = 1_717_000_600_000L,
                expectedTitle = "Evening Ride"
            )
        )
    }

    @Test
    fun testRequiredPermissions_includesAllEnrichedTypes() {
        val perms = HealthConnectManager.requiredPermissions()
        // 8 distinct platform permissions: READ/WRITE_EXERCISE (ExerciseSessionRecord & CyclingPedalingCadenceRecord),
        // HEART_RATE, POWER, ACTIVE_CALORIES_BURNED, DISTANCE, SPEED, TOTAL_CALORIES_BURNED.
        assertEquals(8, perms.size)
        assertTrue(
            perms.contains(
                androidx.health.connect.client.permission.HealthPermission.getWritePermission(
                    androidx.health.connect.client.records.CyclingPedalingCadenceRecord::class
                )
            )
        )
        assertTrue(perms.any { it.contains("DISTANCE") })
        assertTrue(perms.any { it.contains("SPEED") })
        assertTrue(perms.any { it.contains("TOTAL_CALORIES") })
    }

    @Test
    fun testDeduplicateTimestamps_bumpsCollisions() {
        val input = listOf(
            com.valpr.bikecompanion.health.PowerPoint(1000L, 200),
            com.valpr.bikecompanion.health.PowerPoint(1000L, 210),
            com.valpr.bikecompanion.health.PowerPoint(1000L, 220)
        )
        val result = HealthConnectManager.deduplicateTimestamps(
            input,
            timeOf = { it.timeEpochMs },
            withTime = { p, t -> p.copy(timeEpochMs = t) },
            sessionEndMs = 5000L
        )
        assertEquals(3, result.size)
        assertEquals(1000L, result[0].timeEpochMs)
        assertEquals(1001L, result[1].timeEpochMs)
        assertEquals(1002L, result[2].timeEpochMs)
    }

    @Test
    fun testDeduplicateTimestamps_dropsExcessAtSessionEnd() {
        val input = listOf(
            com.valpr.bikecompanion.health.PowerPoint(999L, 200),
            com.valpr.bikecompanion.health.PowerPoint(1000L, 210),
            com.valpr.bikecompanion.health.PowerPoint(1000L, 220),
            com.valpr.bikecompanion.health.PowerPoint(1000L, 230)
        )
        // End wall at 1001 — room for only one bump
        val result = HealthConnectManager.deduplicateTimestamps(
            input,
            timeOf = { it.timeEpochMs },
            withTime = { p, t -> p.copy(timeEpochMs = t) },
            sessionEndMs = 1001L
        )
        assertEquals(3, result.size)
        assertEquals(999L, result[0].timeEpochMs)
        assertEquals(1000L, result[1].timeEpochMs)
        assertEquals(1001L, result[2].timeEpochMs)
    }

    @Test
    fun testDeduplicateTimestamps_singleItemPassesThrough() {
        val input = listOf(com.valpr.bikecompanion.health.HrPoint(5000L, 140))
        val result = HealthConnectManager.deduplicateTimestamps(
            input,
            timeOf = { it.timeEpochMs },
            withTime = { p, t -> p.copy(timeEpochMs = t) },
            sessionEndMs = 10000L
        )
        assertEquals(1, result.size)
        assertEquals(5000L, result[0].timeEpochMs)
    }

    @Test
    fun testPlanRecords_duplicateElapsedSecondsProduceMonotonicTimestamps() {
        val startMs = 1_716_000_000_000L
        // Simulate pause/resume producing duplicate elapsedSeconds=5
        val samples = listOf(
            sample(elapsed = 5, hr = 130, watts = 180),
            sample(elapsed = 5, hr = 131, watts = 181),
            sample(elapsed = 5, hr = 132, watts = 182),
            sample(elapsed = 10, hr = 140, watts = 200)
        )
        val s = summary(durationSeconds = 60, startMs = startMs, samples = samples)
        val plan = HealthConnectManager.planRecords(s, 75.0)

        // HR, power, speed, cadence — all must have strictly increasing timestamps
        for (i in 1 until plan.hrPoints.size) {
            assertTrue(
                "HR timestamps not strictly increasing at index $i",
                plan.hrPoints[i].timeEpochMs > plan.hrPoints[i - 1].timeEpochMs
            )
        }
        for (chunk in plan.powerChunks) {
            for (i in 1 until chunk.size) {
                assertTrue(
                    "Power timestamps not strictly increasing at index $i",
                    chunk[i].timeEpochMs > chunk[i - 1].timeEpochMs
                )
            }
        }
    }
}

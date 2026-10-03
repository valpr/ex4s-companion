package com.valpr.bikecompanion.history

import com.valpr.bikecompanion.workout.WorkoutMetricSample
import com.valpr.bikecompanion.workout.WorkoutSummary
import kotlinx.serialization.Serializable

/**
 * Persisted per-second sample. Mirrors [WorkoutMetricSample] in a
 * serialization-stable shape.
 */
@Serializable
data class StoredSample(
    val elapsedSeconds: Int = 0,
    val watts: Int = 0,
    val targetWatts: Int? = null,
    val cadenceRpm: Int = 0,
    val targetCadence: Int? = null,
    val resistance: Int = 0,
    val speedKmh: Double = 0.0,
    val heartRateBpm: Int = 0
) {
    fun toMetricSample() = WorkoutMetricSample(
        elapsedSeconds = elapsedSeconds,
        watts = watts,
        targetWatts = targetWatts,
        cadenceRpm = cadenceRpm,
        targetCadence = targetCadence,
        resistance = resistance,
        speedKmh = speedKmh,
        heartRateBpm = heartRateBpm
    )

    companion object {
        fun from(sample: WorkoutMetricSample) = StoredSample(
            elapsedSeconds = sample.elapsedSeconds,
            watts = sample.watts,
            targetWatts = sample.targetWatts,
            cadenceRpm = sample.cadenceRpm,
            targetCadence = sample.targetCadence,
            resistance = sample.resistance,
            speedKmh = sample.speedKmh,
            heartRateBpm = sample.heartRateBpm
        )
    }
}

/**
 * Lightweight header for history list screens. Stored in `history_index.json`
 * so listing never parses full per-second sample files.
 */
@Serializable
data class RideHeader(
    val id: String,
    val workoutName: String = "Free Ride",
    val sourceWorkoutFilename: String? = null,
    val startTimeEpochMs: Long = 0L,
    val totalDurationSeconds: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val avgWatts: Int = 0,
    val maxWatts: Int = 0,
    val avgCadence: Int = 0,
    val maxCadence: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val totalWorkKj: Double = 0.0,
    val totalCaloriesKcal: Int = 0
)

/**
 * Full persisted ride, including per-second samples for charts and TCX export.
 */
@Serializable
data class CompletedRide(
    val id: String,
    val workoutName: String = "Free Ride",
    val sourceWorkoutFilename: String? = null,
    val startTimeEpochMs: Long = 0L,
    val totalDurationSeconds: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val avgWatts: Int = 0,
    val maxWatts: Int = 0,
    val avgCadence: Int = 0,
    val maxCadence: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val totalWorkKj: Double = 0.0,
    val totalCaloriesKcal: Int = 0,
    val samples: List<StoredSample> = emptyList()
) {
    fun header() = RideHeader(
        id = id,
        workoutName = workoutName,
        sourceWorkoutFilename = sourceWorkoutFilename,
        startTimeEpochMs = startTimeEpochMs,
        totalDurationSeconds = totalDurationSeconds,
        totalDistanceKm = totalDistanceKm,
        avgWatts = avgWatts,
        maxWatts = maxWatts,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        totalWorkKj = totalWorkKj,
        totalCaloriesKcal = totalCaloriesKcal
    )

    fun toSummary() = WorkoutSummary(
        workoutName = workoutName,
        totalDurationSeconds = totalDurationSeconds,
        totalDistanceKm = totalDistanceKm,
        avgWatts = avgWatts,
        maxWatts = maxWatts,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        totalWorkKj = totalWorkKj,
        totalCaloriesKcal = totalCaloriesKcal,
        samples = samples.map { it.toMetricSample() },
        startTimeEpochMs = startTimeEpochMs
    )

    companion object {
        fun rideIdFor(startTimeEpochMs: Long): String = "ride_$startTimeEpochMs"

        /**
         * Health Connect clientRecordId namespaced per profile so two profiles
         * syncing rides in the same time window never collide on dedup checks.
         * Local history filenames stay un-namespaced (dirs already isolate).
         */
        fun healthClientRecordIdFor(profileId: String?, startTimeEpochMs: Long): String {
            val base = rideIdFor(startTimeEpochMs)
            if (profileId.isNullOrBlank()) return base
            val safe = profileId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(12)
            return "${base}_$safe"
        }

        /**
         * Stable id for sessions with no start timestamp (never happens for real
         * sessions — the manager always stamps start — but keeps re-saves of the
         * same zero-start summary idempotent instead of minting a fresh id per call).
         */
        fun fallbackIdFor(summary: WorkoutSummary): String {
            val hash = summary.workoutName.hashCode() * 31 +
                summary.totalDurationSeconds * 31 +
                summary.samples.hashCode()
            return "ride_h" + hash.toUInt().toString()
        }

        fun fromSummary(summary: WorkoutSummary, sourceWorkoutFilename: String? = null): CompletedRide {
            val rawStart = summary.startTimeEpochMs
            val id = if (rawStart > 0L) rideIdFor(rawStart) else fallbackIdFor(summary)
            // Clamp zero-length sessions to a ≥1s window (same rule as Health Connect).
            val duration = summary.totalDurationSeconds.coerceAtLeast(1)
            return CompletedRide(
                id = id,
                workoutName = summary.workoutName.ifBlank { "Free Ride" },
                sourceWorkoutFilename = sourceWorkoutFilename,
                startTimeEpochMs = rawStart.coerceAtLeast(0L),
                totalDurationSeconds = duration,
                totalDistanceKm = summary.totalDistanceKm.coerceAtLeast(0.0),
                avgWatts = summary.avgWatts,
                maxWatts = summary.maxWatts,
                avgCadence = summary.avgCadence,
                maxCadence = summary.maxCadence,
                avgHeartRate = summary.avgHeartRate,
                maxHeartRate = summary.maxHeartRate,
                totalWorkKj = summary.totalWorkKj,
                totalCaloriesKcal = summary.totalCaloriesKcal,
                samples = summary.samples.map { StoredSample.from(it) }
            )
        }
    }
}

package com.valpr.bikecompanion.history

import com.valpr.bikecompanion.data.ProfilePaths
import com.valpr.bikecompanion.workout.WorkoutMetricSample
import com.valpr.bikecompanion.workout.WorkoutSummary
import kotlinx.serialization.Serializable
import java.security.MessageDigest

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
    val totalCaloriesKcal: Int = 0,
    /** Schema version of this record. Files written before versioning decode as 0. */
    val schemaVersion: Int = 0,
    /** True once a Health Connect batch write for this ride reported Success. */
    val healthSynced: Boolean = false
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
    val samples: List<StoredSample> = emptyList(),
    /** Schema version of this record. Files written before versioning decode as 0. */
    val schemaVersion: Int = 0,
    /** True once a Health Connect batch write for this ride reported Success. */
    val healthSynced: Boolean = false
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
        totalCaloriesKcal = totalCaloriesKcal,
        schemaVersion = schemaVersion,
        healthSynced = healthSynced
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
        /** Current JSON schema version for [CompletedRide] and [RideHeader]. */
        const val SCHEMA_VERSION = 2

        fun rideIdFor(startTimeEpochMs: Long): String = "ride_$startTimeEpochMs"

        /**
         * Health Connect clientRecordId namespaced per profile so two profiles
         * syncing rides in the same time window never collide on dedup checks.
         * Local history filenames stay un-namespaced (dirs already isolate).
         */
        fun healthClientRecordIdFor(profileId: String?, startTimeEpochMs: Long): String {
            val base = rideIdFor(startTimeEpochMs)
            if (profileId.isNullOrBlank()) return base
            val safe = ProfilePaths.sanitizeForHealthRecord(profileId)
            return "${base}_$safe"
        }

        /**
         * Stable id for sessions with no start timestamp (never happens for real
         * sessions — the manager always stamps start — but keeps re-saves of the
         * same zero-start summary idempotent instead of minting a fresh id per call).
         *
         * Uses SHA-256 over a stable fingerprint (never `hashCode()`, whose 32-bit
         * space collides fast across a multi-year ride library). Legacy files
         * written with the old `hashCode()` id keep working — both share the
         * `ride_h` prefix matched by history scans.
         */
        fun fallbackIdFor(summary: WorkoutSummary): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(summary.workoutName.toByteArray(Charsets.UTF_8))
            digest.update(summary.totalDurationSeconds.toString().toByteArray(Charsets.UTF_8))
            for (s in summary.samples) {
                val sampleBytes = (
                    "${s.elapsedSeconds},${s.watts},${s.targetWatts},${s.cadenceRpm}," +
                        "${s.targetCadence},${s.resistance},${s.speedKmh},${s.heartRateBpm};"
                    ).toByteArray(Charsets.UTF_8)
                digest.update(sampleBytes)
            }
            val hex = digest.digest().take(8).joinToString("") { "%02x".format(it) }
            return "ride_h$hex"
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
                samples = summary.samples.map { StoredSample.from(it) },
                schemaVersion = SCHEMA_VERSION
            )
        }
    }
}

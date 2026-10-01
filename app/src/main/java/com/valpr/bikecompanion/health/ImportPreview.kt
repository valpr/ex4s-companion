package com.valpr.bikecompanion.health

/**
 * A metric imported from Health Connect with provenance and staleness status.
 */
data class ImportedMetric<T>(
    val value: T,
    val timestampEpochMs: Long,
    val sourceApp: String? = null,
    val isStaleComparedToProfile: Boolean = false
)

/**
 * Preview model for Health Connect athlete vitals and recovery data.
 */
data class HealthImportPreview(
    val weightKg: ImportedMetric<Float>? = null,
    val heightCm: ImportedMetric<Float>? = null,
    val restingHeartRate: ImportedMetric<Int>? = null,
    val latestSleepDurationMinutes: ImportedMetric<Long>? = null,
    val latestHrvRmssd: ImportedMetric<Double>? = null,
    val recoveryNudge: String? = null
) {
    val hasMetricsToImport: Boolean
        get() = weightKg != null || heightCm != null || restingHeartRate != null

    val allImportableStale: Boolean
        get() {
            val metrics = listOfNotNull(weightKg, heightCm, restingHeartRate)
            return metrics.isNotEmpty() && metrics.all { it.isStaleComparedToProfile }
        }
}

/**
 * Pure calculation logic for Health Connect metric import (100% JVM-unit-testable).
 */
object HealthImportCalculator {

    fun computeRecoveryNudge(
        sleepMinutes: Long?,
        hrvRmssd: Double?
    ): String? {
        if (sleepMinutes == null && hrvRmssd == null) return null
        val sleepHours = sleepMinutes?.let { it / 60.0 }

        return when {
            sleepHours != null && sleepHours < 6.0 && hrvRmssd != null && hrvRmssd < 25.0 ->
                "Low sleep (<6h) and low HRV detected. Prioritize active recovery or rest today."
            sleepHours != null && sleepHours < 6.0 ->
                "Short sleep detected (<6h). Consider a lighter endurance ride today."
            hrvRmssd != null && hrvRmssd < 25.0 ->
                "Low HRV detected (<25ms). Your body may be fatigued; monitor effort closely."
            sleepHours != null && sleepHours >= 7.0 && (hrvRmssd == null || hrvRmssd >= 40.0) ->
                "Solid sleep and recovery detected. Ready for high-intensity or threshold work!"
            else ->
                "Recovery metrics within normal baseline."
        }
    }

    fun buildPreview(
        weights: List<Pair<Long, Float>>,
        heights: List<Pair<Long, Float>>,
        restingHrs: List<Pair<Long, Int>>,
        sleepSessions: List<Pair<Long, Long>>,
        hrvs: List<Pair<Long, Double>>,
        currentProfileLastUpdatedEpochMs: Long
    ): HealthImportPreview {
        val latestWeight = weights.maxByOrNull { it.first }?.let { (time, w) ->
            ImportedMetric(
                value = w,
                timestampEpochMs = time,
                isStaleComparedToProfile = currentProfileLastUpdatedEpochMs > 0L && time < currentProfileLastUpdatedEpochMs
            )
        }
        val latestHeight = heights.maxByOrNull { it.first }?.let { (time, h) ->
            ImportedMetric(
                value = h,
                timestampEpochMs = time,
                isStaleComparedToProfile = currentProfileLastUpdatedEpochMs > 0L && time < currentProfileLastUpdatedEpochMs
            )
        }
        val latestRestingHr = restingHrs.maxByOrNull { it.first }?.let { (time, r) ->
            ImportedMetric(
                value = r,
                timestampEpochMs = time,
                isStaleComparedToProfile = currentProfileLastUpdatedEpochMs > 0L && time < currentProfileLastUpdatedEpochMs
            )
        }
        val latestSleep = sleepSessions.maxByOrNull { it.first }?.let { (time, dur) ->
            ImportedMetric(
                value = dur,
                timestampEpochMs = time,
                isStaleComparedToProfile = currentProfileLastUpdatedEpochMs > 0L && time < currentProfileLastUpdatedEpochMs
            )
        }
        val latestHrv = hrvs.maxByOrNull { it.first }?.let { (time, hrv) ->
            ImportedMetric(
                value = hrv,
                timestampEpochMs = time,
                isStaleComparedToProfile = currentProfileLastUpdatedEpochMs > 0L && time < currentProfileLastUpdatedEpochMs
            )
        }
        val nudge = computeRecoveryNudge(latestSleep?.value, latestHrv?.value)

        return HealthImportPreview(
            weightKg = latestWeight,
            heightCm = latestHeight,
            restingHeartRate = latestRestingHr,
            latestSleepDurationMinutes = latestSleep,
            latestHrvRmssd = latestHrv,
            recoveryNudge = nudge
        )
    }
}

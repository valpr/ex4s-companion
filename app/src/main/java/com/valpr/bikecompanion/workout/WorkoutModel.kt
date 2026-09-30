package com.valpr.bikecompanion.workout

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Representation of a structured workout parsed from a Zwift (.zwo) file.
 */
data class Workout(
    val name: String,
    val author: String = "",
    val description: String = "",
    val sportType: String = "bike",
    val tags: List<String> = emptyList(),
    val segments: List<WorkoutSegment> = emptyList(),
    val textEvents: List<WorkoutTextEvent> = emptyList()
) {
    /**
     * Total duration in seconds across all segments.
     */
    val totalDurationSeconds: Int = segments.sumOf { it.durationSeconds }

    /**
     * Estimated Training Stress Score (TSS) based on FTP fractions.
     * Formula: TSS = sum( (durationSec * IF^2) / 36.0 )
     * where IF (Intensity Factor) is the fractional FTP target.
     */
    val estimatedTss: Double = segments.sumOf { segment ->
        val intensityFactor = segment.averageIntensityFactor.toDouble()
        (segment.durationSeconds * intensityFactor.pow(2.0)) / 36.0
    }

    /**
     * Finds the segment at the given workout elapsed time in seconds.
     * Returns null if elapsed time is outside the workout duration.
     */
    fun getSegmentAtTime(elapsedWorkoutSeconds: Int): SegmentPosition? {
        if (segments.isEmpty() || elapsedWorkoutSeconds < 0 || elapsedWorkoutSeconds >= totalDurationSeconds) {
            return null
        }
        var accumulated = 0
        for ((index, segment) in segments.withIndex()) {
            val segmentEnd = accumulated + segment.durationSeconds
            if (elapsedWorkoutSeconds < segmentEnd) {
                return SegmentPosition(
                    segmentIndex = index,
                    segment = segment,
                    segmentElapsedSeconds = elapsedWorkoutSeconds - accumulated,
                    segmentRemainingSeconds = segmentEnd - elapsedWorkoutSeconds,
                    totalSegments = segments.size
                )
            }
            accumulated = segmentEnd
        }
        return null
    }

    /**
     * Calculates the target mechanical power in Watts at the given elapsed workout second.
     * Returns null if ERG mode is disabled for the active segment (e.g. FreeRide/MaxEffort)
     * or if the workout has ended.
     *
     * @param ftp Athlete functional threshold power in Watts.
     * @param elapsedWorkoutSeconds Seconds elapsed from workout start.
     * @param intensityScale Optional multiplier for user intensity bias or Dynamic HR Capping (e.g. 0.90 for 10% reduction).
     */
    fun targetWattsAt(ftp: Int, elapsedWorkoutSeconds: Int, intensityScale: Float = 1.0f): Int? {
        val position = getSegmentAtTime(elapsedWorkoutSeconds) ?: return null
        val baseWatts = position.segment.targetWatts(ftp, position.segmentElapsedSeconds) ?: return null
        return (baseWatts * intensityScale).roundToInt().coerceAtLeast(0)
    }

    /**
     * Target cadence in RPM at the given elapsed workout second, or null if unspecified.
     */
    fun targetCadenceAt(elapsedWorkoutSeconds: Int): Int? = getSegmentAtTime(elapsedWorkoutSeconds)?.segment?.targetCadence

    /**
     * Finds any coaching text events active at the specified second.
     */
    fun activeTextEventsAt(elapsedWorkoutSeconds: Int, displayDurationSeconds: Int = 10): List<WorkoutTextEvent> = textEvents.filter { event ->
        elapsedWorkoutSeconds >= event.timeOffsetSeconds &&
            elapsedWorkoutSeconds < (event.timeOffsetSeconds + displayDurationSeconds)
    }
}

/**
 * Playhead position within the active workout segment.
 */
data class SegmentPosition(
    val segmentIndex: Int,
    val segment: WorkoutSegment,
    val segmentElapsedSeconds: Int,
    val segmentRemainingSeconds: Int,
    val totalSegments: Int
)

/**
 * An on-screen coaching cue parsed from a <textevent> element.
 * [timeOffsetSeconds] is relative to overall workout start.
 */
data class WorkoutTextEvent(val timeOffsetSeconds: Int, val message: String)

/**
 * A discrete segment of a structured cycling workout.
 */
sealed interface WorkoutSegment {
    val durationSeconds: Int
    val targetCadence: Int?
    val isErgEnabled: Boolean get() = true
    val averageIntensityFactor: Float

    fun targetWatts(ftp: Int, elapsedSecondsInSegment: Int): Int?

    /**
     * Linear warm-up ramp from [powerLow] to [powerHigh] (% of FTP).
     */
    data class Warmup(
        override val durationSeconds: Int,
        val powerLow: Float,
        val powerHigh: Float,
        override val targetCadence: Int? = null
    ) : WorkoutSegment {
        override val averageIntensityFactor: Float = (powerLow + powerHigh) / 2f

        override fun targetWatts(ftp: Int, elapsedSecondsInSegment: Int): Int {
            if (durationSeconds <= 0) return (powerLow * ftp).roundToInt()
            val progress = (elapsedSecondsInSegment.toFloat() / durationSeconds).coerceIn(0f, 1f)
            val currentFactor = powerLow + progress * (powerHigh - powerLow)
            return (currentFactor * ftp).roundToInt()
        }
    }

    /**
     * Linear cool-down ramp from [powerLow] to [powerHigh] (% of FTP).
     */
    data class Cooldown(
        override val durationSeconds: Int,
        val powerLow: Float,
        val powerHigh: Float,
        override val targetCadence: Int? = null
    ) : WorkoutSegment {
        override val averageIntensityFactor: Float = (powerLow + powerHigh) / 2f

        override fun targetWatts(ftp: Int, elapsedSecondsInSegment: Int): Int {
            if (durationSeconds <= 0) return (powerLow * ftp).roundToInt()
            val progress = (elapsedSecondsInSegment.toFloat() / durationSeconds).coerceIn(0f, 1f)
            val currentFactor = powerLow + progress * (powerHigh - powerLow)
            return (currentFactor * ftp).roundToInt()
        }
    }

    /**
     * Constant target power block at [power] (% of FTP).
     */
    data class SteadyState(
        override val durationSeconds: Int,
        val power: Float,
        override val targetCadence: Int? = null
    ) : WorkoutSegment {
        override val averageIntensityFactor: Float = power

        override fun targetWatts(ftp: Int, elapsedSecondsInSegment: Int): Int = (power * ftp).roundToInt()
    }

    /**
     * Mid-workout ramp from [powerLow] to [powerHigh] (% of FTP).
     */
    data class Ramp(
        override val durationSeconds: Int,
        val powerLow: Float,
        val powerHigh: Float,
        override val targetCadence: Int? = null
    ) : WorkoutSegment {
        override val averageIntensityFactor: Float = (powerLow + powerHigh) / 2f

        override fun targetWatts(ftp: Int, elapsedSecondsInSegment: Int): Int {
            if (durationSeconds <= 0) return (powerLow * ftp).roundToInt()
            val progress = (elapsedSecondsInSegment.toFloat() / durationSeconds).coerceIn(0f, 1f)
            val currentFactor = powerLow + progress * (powerHigh - powerLow)
            return (currentFactor * ftp).roundToInt()
        }
    }

    /**
     * ERG mode disabled: rider controls resistance manually.
     */
    data class FreeRide(
        override val durationSeconds: Int,
        val flatRoad: Boolean = false,
        override val targetCadence: Int? = null
    ) : WorkoutSegment {
        override val isErgEnabled: Boolean get() = false
        override val averageIntensityFactor: Float = 0.65f // Estimate for TSS calculation

        override fun targetWatts(ftp: Int, elapsedSecondsInSegment: Int): Int? = null
    }

    /**
     * Max effort sprint: ERG mode disabled.
     */
    data class MaxEffort(override val durationSeconds: Int, override val targetCadence: Int? = null) : WorkoutSegment {
        override val isErgEnabled: Boolean get() = false
        override val averageIntensityFactor: Float = 1.50f // High intensity estimate for TSS calculation

        override fun targetWatts(ftp: Int, elapsedSecondsInSegment: Int): Int? = null
    }
}

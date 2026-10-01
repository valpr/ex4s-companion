package com.valpr.bikecompanion.history

import kotlin.math.roundToInt

/**
 * Framework-free ride-history aggregations (plain-JUnit testable per AGENTS.md §8).
 */
object HistoryStats {
    data class PersonalBests(
        val bestMaxPowerW: Int = 0,
        val bestAvgPowerW: Int = 0,
        val longestRideSeconds: Int = 0,
        val biggestKj: Double = 0.0
    )

    data class Totals(
        val rideCount: Int = 0,
        val totalSeconds: Int = 0,
        val totalDistanceKm: Double = 0.0,
        val totalKj: Double = 0.0
    )

    data class WeeklyVolume(
        val rideCount: Int = 0,
        val totalSeconds: Int = 0,
        val totalKj: Double = 0.0,
        val totalDistanceKm: Double = 0.0
    )

    /** Best avg power only considers rides ≥20 min so sprints don't dominate. */
    fun personalBests(headers: List<RideHeader>): PersonalBests {
        if (headers.isEmpty()) {
            return PersonalBests()
        }
        return PersonalBests(
            bestMaxPowerW = headers.maxOfOrNull { it.maxWatts } ?: 0,
            bestAvgPowerW = headers.filter { it.totalDurationSeconds >= 1200 }.maxOfOrNull { it.avgWatts } ?: 0,
            longestRideSeconds = headers.maxOfOrNull { it.totalDurationSeconds } ?: 0,
            biggestKj = headers.maxOfOrNull { it.totalWorkKj } ?: 0.0
        )
    }

    fun totals(headers: List<RideHeader>): Totals = Totals(
        rideCount = headers.size,
        totalSeconds = headers.sumOf { it.totalDurationSeconds },
        totalDistanceKm = headers.sumOf { it.totalDistanceKm },
        totalKj = headers.sumOf { it.totalWorkKj }
    )

    /** Rides with startTime in [weekStartMs, weekStartMs + 7d). */
    fun weeklyVolume(headers: List<RideHeader>, weekStartMs: Long): WeeklyVolume {
        val weekEndMs = weekStartMs + 7L * 24 * 60 * 60 * 1000
        val inWeek = headers.filter { it.startTimeEpochMs in weekStartMs until weekEndMs }
        return WeeklyVolume(
            rideCount = inWeek.size,
            totalSeconds = inWeek.sumOf { it.totalDurationSeconds },
            totalKj = inWeek.sumOf { it.totalWorkKj },
            totalDistanceKm = inWeek.sumOf { it.totalDistanceKm }
        )
    }

    /**
     * Buckets headers into 4 week windows ending at [nowMs]: index 0 is the
     * oldest week, index 3 the current week. Pure math for bar-chart rendering.
     */
    fun lastFourWeeks(headers: List<RideHeader>, nowMs: Long): List<WeeklyVolume> {
        val weekMs = 7L * 24 * 60 * 60 * 1000
        return (3 downTo 0).map { weeksAgo ->
            val start = nowMs - (weeksAgo + 1) * weekMs
            weeklyVolume(headers, start)
        }
    }

    fun formatDuration(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainder = seconds % 60
        return if (hours > 0) {
            "%dh %02dm".format(hours, minutes)
        } else {
            "%d:%02d".format(minutes, remainder)
        }
    }

    fun averageWattsPerWeek(weekly: WeeklyVolume): Int {
        if (weekly.totalSeconds <= 0) {
            return 0
        }
        return ((weekly.totalKj * 1000.0) / weekly.totalSeconds).roundToInt()
    }
}

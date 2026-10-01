package com.valpr.bikecompanion

import com.valpr.bikecompanion.history.HistoryStats
import com.valpr.bikecompanion.history.RideHeader
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStatsTest {

    private fun header(
        name: String = "Ride",
        startMs: Long = 1_700_000_000_000L,
        duration: Int = 1800,
        avgW: Int = 150,
        maxW: Int = 250,
        kj: Double = 270.0,
        km: Double = 10.0
    ) = RideHeader(
        id = "ride_$startMs",
        workoutName = name,
        startTimeEpochMs = startMs,
        totalDurationSeconds = duration,
        totalDistanceKm = km,
        avgWatts = avgW,
        maxWatts = maxW,
        totalWorkKj = kj,
        totalCaloriesKcal = kj.toInt()
    )

    @Test
    fun personalBests_empty_returnsZeroes() {
        assertEquals(HistoryStats.PersonalBests(), HistoryStats.personalBests(emptyList()))
    }

    @Test
    fun personalBests_picksMaxima() {
        val headers = listOf(
            header(startMs = 1L, duration = 1800, avgW = 150, maxW = 250, kj = 270.0),
            header(startMs = 2L, duration = 3600, avgW = 130, maxW = 300, kj = 450.0)
        )
        val bests = HistoryStats.personalBests(headers)
        assertEquals(300, bests.bestMaxPowerW)
        assertEquals(150, bests.bestAvgPowerW)
        assertEquals(3600, bests.longestRideSeconds)
        assertEquals(450.0, bests.biggestKj, 0.001)
    }

    @Test
    fun personalBests_ignoresShortRidesForBestAvg() {
        val headers = listOf(
            header(startMs = 1L, duration = 300, avgW = 400, maxW = 500, kj = 120.0),
            header(startMs = 2L, duration = 1800, avgW = 150, maxW = 250, kj = 270.0)
        )
        assertEquals(150, HistoryStats.personalBests(headers).bestAvgPowerW)
    }

    @Test
    fun totals_sumsAll() {
        val headers = listOf(
            header(startMs = 1L, duration = 1800, kj = 270.0, km = 10.0),
            header(startMs = 2L, duration = 900, kj = 100.0, km = 5.0)
        )
        val totals = HistoryStats.totals(headers)
        assertEquals(2, totals.rideCount)
        assertEquals(2700, totals.totalSeconds)
        assertEquals(15.0, totals.totalDistanceKm, 0.001)
        assertEquals(370.0, totals.totalKj, 0.001)
    }

    @Test
    fun weeklyVolume_filtersToWeekWindow() {
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val weekStart = 1_700_000_000_000L
        val headers = listOf(
            header(startMs = weekStart + 1000, duration = 1800, kj = 270.0),
            header(startMs = weekStart + weekMs + 1000, duration = 900, kj = 100.0)
        )
        val volume = HistoryStats.weeklyVolume(headers, weekStart)
        assertEquals(1, volume.rideCount)
        assertEquals(1800, volume.totalSeconds)
        assertEquals(270.0, volume.totalKj, 0.001)
    }

    @Test
    fun lastFourWeeks_returnsFourBuckets() {
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val now = 1_700_000_000_000L + 4 * weekMs
        val headers = listOf(header(startMs = now - 1000, duration = 600, kj = 90.0))
        val weeks = HistoryStats.lastFourWeeks(headers, now)
        assertEquals(4, weeks.size)
        assertEquals(1, weeks[3].rideCount)
        assertEquals(0, weeks[0].rideCount)
    }

    @Test
    fun formatDuration_hoursAndMinutes() {
        assertEquals("1h 05m", HistoryStats.formatDuration(3900))
        assertEquals("25:00", HistoryStats.formatDuration(1500))
        assertEquals("1:30", HistoryStats.formatDuration(90))
        assertEquals("0:45", HistoryStats.formatDuration(45))
    }
}

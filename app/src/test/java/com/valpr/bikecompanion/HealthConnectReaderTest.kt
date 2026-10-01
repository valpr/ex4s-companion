package com.valpr.bikecompanion

import com.valpr.bikecompanion.health.HealthConnectReader
import com.valpr.bikecompanion.health.HealthImportCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectReaderTest {

    @Test
    fun testBuildPreview_picksLatestTimestampMetrics() {
        val weights = listOf(
            1_000_000L to 75.0f,
            2_000_000L to 73.5f,
            1_500_000L to 74.0f
        )
        val heights = listOf(
            1_000_000L to 175.0f,
            2_500_000L to 176.0f
        )
        val restingHrs = listOf(
            500_000L to 65,
            1_800_000L to 58
        )
        val sleep = listOf(
            1_200_000L to 450L,
            2_200_000L to 480L
        )
        val hrvs = listOf(
            2_100_000L to 55.0
        )

        val preview = HealthImportCalculator.buildPreview(
            weights = weights,
            heights = heights,
            restingHrs = restingHrs,
            sleepSessions = sleep,
            hrvs = hrvs,
            currentProfileLastUpdatedEpochMs = 0L
        )

        assertNotNull(preview.weightKg)
        assertEquals(73.5f, preview.weightKg!!.value, 0.001f)
        assertEquals(2_000_000L, preview.weightKg!!.timestampEpochMs)
        assertFalse(preview.weightKg!!.isStaleComparedToProfile)

        assertNotNull(preview.heightCm)
        assertEquals(176.0f, preview.heightCm!!.value, 0.001f)
        assertEquals(2_500_000L, preview.heightCm!!.timestampEpochMs)

        assertNotNull(preview.restingHeartRate)
        assertEquals(58, preview.restingHeartRate!!.value)

        assertNotNull(preview.latestSleepDurationMinutes)
        assertEquals(480L, preview.latestSleepDurationMinutes!!.value)

        assertNotNull(preview.latestHrvRmssd)
        assertEquals(55.0, preview.latestHrvRmssd!!.value, 0.001)

        assertTrue(preview.hasMetricsToImport)
        assertFalse(preview.allImportableStale)
    }

    @Test
    fun testBuildPreview_flagsStaleMetricsComparedToProfile() {
        val profileLastUpdated = 2_000_000L

        val weights = listOf(
            1_500_000L to 72.0f // Older than profile
        )
        val heights = listOf(
            2_500_000L to 178.0f // Newer than profile
        )

        val preview = HealthImportCalculator.buildPreview(
            weights = weights,
            heights = heights,
            restingHrs = emptyList(),
            sleepSessions = emptyList(),
            hrvs = emptyList(),
            currentProfileLastUpdatedEpochMs = profileLastUpdated
        )

        assertTrue(preview.weightKg!!.isStaleComparedToProfile)
        assertFalse(preview.heightCm!!.isStaleComparedToProfile)
        assertFalse(preview.allImportableStale)
    }

    @Test
    fun testAllImportableStale_returnsTrueWhenAllAreOlder() {
        val profileLastUpdated = 3_000_000L

        val weights = listOf(1_000_000L to 70.0f)
        val heights = listOf(1_500_000L to 175.0f)

        val preview = HealthImportCalculator.buildPreview(
            weights = weights,
            heights = heights,
            restingHrs = emptyList(),
            sleepSessions = emptyList(),
            hrvs = emptyList(),
            currentProfileLastUpdatedEpochMs = profileLastUpdated
        )

        assertTrue(preview.allImportableStale)
    }

    @Test
    fun testComputeRecoveryNudge_shortSleepSuggestsLighterRide() {
        // 5 hours of sleep, normal HRV
        val nudge = HealthImportCalculator.computeRecoveryNudge(
            sleepMinutes = 300L,
            hrvRmssd = 45.0
        )
        assertNotNull(nudge)
        assertTrue(nudge!!.contains("Short sleep"))
    }

    @Test
    fun testComputeRecoveryNudge_lowHrvSuggestsFatigue() {
        // 8 hours of sleep, but HRV is 20 ms
        val nudge = HealthImportCalculator.computeRecoveryNudge(
            sleepMinutes = 480L,
            hrvRmssd = 20.0
        )
        assertNotNull(nudge)
        assertTrue(nudge!!.contains("Low HRV"))
    }

    @Test
    fun testComputeRecoveryNudge_shortSleepAndLowHrv() {
        // 5 hours of sleep and HRV 18 ms
        val nudge = HealthImportCalculator.computeRecoveryNudge(
            sleepMinutes = 300L,
            hrvRmssd = 18.0
        )
        assertNotNull(nudge)
        assertTrue(nudge!!.contains("Low sleep (<6h) and low HRV"))
    }

    @Test
    fun testComputeRecoveryNudge_solidSleepAndHrvReadyForWork() {
        val nudge = HealthImportCalculator.computeRecoveryNudge(
            sleepMinutes = 480L,
            hrvRmssd = 55.0
        )
        assertNotNull(nudge)
        assertTrue(nudge!!.contains("Solid sleep and recovery"))
    }

    @Test
    fun testComputeRecoveryNudge_nullInputsReturnsNull() {
        val nudge = HealthImportCalculator.computeRecoveryNudge(
            sleepMinutes = null,
            hrvRmssd = null
        )
        assertNull(nudge)
    }

    @Test
    fun testReadPermissions_containsAllExpectedTypes() {
        val perms = HealthConnectReader.readPermissions()
        assertEquals(5, perms.size)
        assertTrue(perms.any { it.contains("WEIGHT") })
        assertTrue(perms.any { it.contains("HEIGHT") })
        assertTrue(perms.any { it.contains("RESTING_HEART_RATE") })
        assertTrue(perms.any { it.contains("SLEEP") })
        assertTrue(perms.any { it.contains("HEART_RATE_VARIABILITY") })
    }
}

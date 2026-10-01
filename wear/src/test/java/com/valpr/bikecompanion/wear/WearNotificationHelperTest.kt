package com.valpr.bikecompanion.wear

import com.valpr.bikecompanion.wear.service.WearNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class WearNotificationHelperTest {
    @Test
    fun formatContent_zeroValues_returnsTrackingPlaceholder() {
        val result = WearNotificationHelper.formatContent(elapsedSeconds = 0, heartRateBpm = 0)
        assertEquals("Tracking heart rate...", result)
    }

    @Test
    fun formatContent_positiveValues_formatsTimeAndBpm() {
        val result = WearNotificationHelper.formatContent(elapsedSeconds = 65, heartRateBpm = 142)
        assertEquals("01:05 • 142 BPM", result)
    }

    @Test
    fun formatContent_onlyHr_formatsBpmOnly() {
        val result = WearNotificationHelper.formatContent(elapsedSeconds = 0, heartRateBpm = 150)
        assertEquals("150 BPM", result)
    }

    @Test
    fun formatContent_onlyTime_formatsTimeOnly() {
        val result = WearNotificationHelper.formatContent(elapsedSeconds = 3600, heartRateBpm = 0)
        assertEquals("60:00", result)
    }

    @Test
    fun formatContent_bailoutActive_prefixesErgSuspended() {
        val result = WearNotificationHelper.formatContent(
            elapsedSeconds = 65,
            heartRateBpm = 142,
            isBailoutActive = true
        )
        assertEquals("ERG SUSPENDED • 01:05 • 142 BPM", result)
    }

    @Test
    fun formatContent_cadenceFloorActive_prefixesCadenceBailout() {
        val result = WearNotificationHelper.formatContent(
            elapsedSeconds = 120,
            heartRateBpm = 160,
            isCadenceFloorActive = true
        )
        assertEquals("CADENCE BAILOUT • 02:00 • 160 BPM", result)
    }

    @Test
    fun formatBailoutTitleAndContent_manualBailout() {
        assertEquals("ERG Suspended", WearNotificationHelper.formatBailoutTitle(isCadenceFloor = false))
        assertEquals(
            "Resistance dropped to recovery. Tap to resume.",
            WearNotificationHelper.formatBailoutContent(isCadenceFloor = false)
        )
    }

    @Test
    fun formatBailoutTitleAndContent_cadenceFloorBailout() {
        assertEquals("Cadence Floor Bailout", WearNotificationHelper.formatBailoutTitle(isCadenceFloor = true))
        assertEquals(
            "Cadence dropped below floor. Spin up and tap to resume.",
            WearNotificationHelper.formatBailoutContent(isCadenceFloor = true)
        )
    }

    @Test
    fun formatCompletionTitleAndContent() {
        assertEquals("Workout Complete!", WearNotificationHelper.formatCompletionTitle())
        assertEquals(
            "Sweet Spot Finished • 45:12",
            WearNotificationHelper.formatCompletionContent(workoutName = "Sweet Spot", elapsedSeconds = 2712)
        )
        assertEquals(
            "Ride Finished • 30:00",
            WearNotificationHelper.formatCompletionContent(workoutName = null, elapsedSeconds = 1800)
        )
        assertEquals(
            "Ride Finished • 25:00",
            WearNotificationHelper.formatCompletionContent(workoutName = "Free Ride", elapsedSeconds = 1500)
        )
    }
}

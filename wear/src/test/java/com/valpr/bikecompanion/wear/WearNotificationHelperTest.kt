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
}

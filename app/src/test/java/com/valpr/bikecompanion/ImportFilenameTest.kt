package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.dashboard.DashboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFilenameTest {

    @Test
    fun displayNameWinsAndZwoPasses() {
        val r = DashboardViewModel.resolveImportFilename("ride.ZWO", "garbled123")
        assertTrue(r.isSuccess)
        assertEquals("ride.ZWO", r.getOrThrow())
    }

    @Test
    fun nonZwoRejected() {
        assertTrue(DashboardViewModel.resolveImportFilename("notes.txt", null).isFailure)
        assertTrue(DashboardViewModel.resolveImportFilename(null, "doc.pdf").isFailure)
    }

    @Test
    fun fallbackSegmentUsedWhenNoDisplayName() {
        val r = DashboardViewModel.resolveImportFilename(null, "my_ride.zwo")
        assertTrue(r.isSuccess)
        assertEquals("my_ride.zwo", r.getOrThrow())
    }

    @Test
    fun blankFallsBackToDefault() {
        val r = DashboardViewModel.resolveImportFilename(null, null)
        assertTrue(r.isSuccess)
        assertEquals("imported_workout.zwo", r.getOrThrow())
    }
}

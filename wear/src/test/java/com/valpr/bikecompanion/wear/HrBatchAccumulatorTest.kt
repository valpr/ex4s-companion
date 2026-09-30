package com.valpr.bikecompanion.wear

import com.valpr.bikecompanion.wear.health.HrBatchAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrBatchAccumulatorTest {

    @Test
    fun intervalSelection_activeVsAmbient() {
        assertEquals(2500L, HrBatchAccumulator.intervalFor(false))
        assertEquals(6000L, HrBatchAccumulator.intervalFor(true))
    }

    @Test
    fun recordSample_acceptsPositive_dropsNonPositive() {
        val acc = HrBatchAccumulator()
        assertTrue(acc.recordSample(140))
        assertTrue(acc.recordSample(1))
        assertFalse(acc.recordSample(0))
        assertFalse(acc.recordSample(-20))
        assertEquals(2, acc.size)
    }

    @Test
    fun drain_returnsSnapshotAndClears() {
        val acc = HrBatchAccumulator()
        acc.recordSample(140)
        acc.recordSample(152)

        val first = acc.drain()
        assertEquals(listOf(140, 152), first)
        assertEquals(0, acc.size)

        // Second drain without new samples is empty -> manager must not emit a batch.
        assertTrue(acc.drain().isEmpty())
    }

    @Test
    fun drain_isSnapshot_notLiveView() {
        val acc = HrBatchAccumulator()
        acc.recordSample(140)
        val snap = acc.drain()
        acc.recordSample(150)
        assertEquals(listOf(140), snap)
        assertEquals(listOf(150), acc.drain())
    }

    @Test
    fun clear_discardsBufferedSamples() {
        val acc = HrBatchAccumulator()
        acc.recordSample(140)
        acc.clear()
        assertEquals(0, acc.size)
        assertTrue(acc.drain().isEmpty())
    }

    @Test
    fun startStopCycle_stopClearsStaleSamples() {
        // Models stopHeartRateTracking(): stale pre-stop samples must not
        // leak into the next tracking session's first batch.
        val acc = HrBatchAccumulator()
        acc.recordSample(140)
        acc.clear() // stop
        acc.recordSample(150) // new session
        assertEquals(listOf(150), acc.drain())
    }
}

package com.valpr.bikecompanion.wear.health

/**
 * Pure heart-rate batch accumulator for the watch (framework-free, plain-JUnit testable).
 *
 * [HealthServicesManager] delegates here for buffer + drain + interval selection.
 * Batching discipline is a battery invariant: 2.5s in active display mode,
 * 6s in ambient mode (lets the AP sleep). A leaked batch loop or wrong
 * interval drains the watch; an empty drain must never emit a batch.
 */
class HrBatchAccumulator {
    companion object {
        const val ACTIVE_INTERVAL_MS = 2500L
        const val AMBIENT_INTERVAL_MS = 6000L

        /** Active: 2-3s; Ambient: 5-10s (AP sleep). */
        fun intervalFor(isAmbient: Boolean): Long = if (isAmbient) AMBIENT_INTERVAL_MS else ACTIVE_INTERVAL_MS
    }

    private val lock = Any()
    private val buffer = mutableListOf<Int>()

    val size: Int
        get() = synchronized(lock) { buffer.size }

    /**
     * Ingests one instantaneous sample. Returns false (dropped) for [bpm] <= 0,
     * mirroring the manager's invalid-sample guard.
     */
    fun recordSample(bpm: Int): Boolean {
        if (bpm <= 0) return false
        synchronized(lock) { buffer.add(bpm) }
        return true
    }

    /** Atomically snapshots buffered samples and clears the buffer. */
    fun drain(): List<Int> = synchronized(lock) {
        val out = buffer.toList()
        buffer.clear()
        out
    }

    fun clear() = synchronized(lock) { buffer.clear() }
}

package com.valpr.bikecompanion.shared

import kotlin.math.roundToInt

/**
 * Display-only power smoothing (Zwift / TrainerRoad convention).
 *
 * Applies a rolling average over the last [windowSize] samples to the live
 * power readout so single-frame table-quantization jumps (+10-20W on the
 * EX-4S watt table) don't flicker the tile on every gear/intensity shift.
 *
 * Display only: recording (samples, summary, TCX/Health Connect) and the ERG
 * control loop must keep using raw instantaneous watts. Smoothing the control
 * input adds lag and spiral risk; smoothing the record destroys data fidelity.
 *
 * Framework-free so it stays plain-JUnit testable on both phone and watch.
 *
 * Assumes telemetry arrives at ~1Hz, so a sample-count window approximates a
 * time window (default 3 samples ≈ Zwift's 3-second average).
 */
class PowerSmoother(windowSize: Int = DEFAULT_WINDOW) {
    private val effectiveWindow: Int = windowSize.coerceIn(1, MAX_WINDOW)
    private val samples: ArrayDeque<Int> = ArrayDeque()

    /** Current smoothed value (0 when empty/reset). */
    var value: Int = 0
        private set

    /**
     * Feeds one raw watt sample, returns the new smoothed display value.
     *
     * A zero/negative sample (stopped pedaling, dropout) clears the window and
     * returns 0 immediately so the tile drops instead of lingering at 200W.
     */
    fun update(rawWatts: Int): Int {
        if (rawWatts <= 0) {
            reset()
            return 0
        }
        samples.addLast(rawWatts)
        while (samples.size > effectiveWindow) {
            samples.removeFirst()
        }
        value = samples.average().roundToInt()
        return value
    }

    fun reset() {
        samples.clear()
        value = 0
    }

    companion object {
        const val DEFAULT_WINDOW = 3
        const val MAX_WINDOW = 30
    }
}

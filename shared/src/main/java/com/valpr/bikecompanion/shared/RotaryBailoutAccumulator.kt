package com.valpr.bikecompanion.shared

/**
 * Pure rotary-crown bailout accumulator (JVM-testable).
 * Backward flick only: accumulate scroll pixels, fire at <= -threshold,
 * forward scroll decays/resets so normal list scrolling never bails out.
 */
class RotaryBailoutAccumulator(
    private val thresholdPx: Float = 40f
) {
    private var accumulator: Float = 0f

    /** Returns true when a bailout should fire (and resets). */
    fun onScroll(verticalScrollPixels: Float): Boolean {
        accumulator += verticalScrollPixels
        if (accumulator <= -thresholdPx) {
            accumulator = 0f
            return true
        }
        if (verticalScrollPixels > 0f || accumulator > thresholdPx) {
            accumulator = 0f
        }
        return false
    }

    fun reset() { accumulator = 0f }

    fun current(): Float = accumulator
}

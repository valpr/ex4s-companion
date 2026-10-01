package com.valpr.bikecompanion.shared

import kotlin.math.roundToInt

/**
 * Single source of truth for heart-rate zone thresholds, shared by the phone and
 * the watch so both UIs color the same BPM identically for a given max HR.
 *
 * Zones:
 * - Standard % of Max HR: Z1 < 60%, Z2 60–70%, Z3 70–80%, Z4 80–90%, Z5 ≥ 90%.
 * - Karvonen / Heart Rate Reserve (HRR): Target = RHR + ((MaxHR - RHR) * %intensity).
 *
 * Pure Kotlin (no Compose dependency) so `:shared` stays UI-free; each module maps
 * the zone number to its own color palette.
 */
object HrZone {
    const val DEFAULT_MAX_HR = 190
    const val DEFAULT_RESTING_HR = 60

    fun zoneNumber(
        bpm: Int,
        maxHr: Int = DEFAULT_MAX_HR,
        restingHr: Int = DEFAULT_RESTING_HR,
        useKarvonen: Boolean = false
    ): Int {
        if (bpm <= 0 || maxHr <= 0) return 1
        val (max, rest) = if (useKarvonen) {
            val clampedMax = maxHr.coerceAtLeast(restingHr + 10).coerceAtLeast(40)
            val clampedRest = restingHr.coerceIn(30, clampedMax - 1)
            clampedMax to clampedRest
        } else {
            maxHr.coerceAtLeast(1) to 0
        }
        val reserve = max - rest
        if (bpm <= rest) return 1

        fun threshold(pct: Float): Int = (rest + reserve * pct).roundToInt()

        return when {
            bpm < threshold(0.60f) -> 1
            bpm < threshold(0.70f) -> 2
            bpm < threshold(0.80f) -> 3
            bpm < threshold(0.90f) -> 4
            else -> 5
        }
    }

    fun label(zoneNumber: Int): String = when (zoneNumber) {
        1 -> "Recovery"
        2 -> "Endurance"
        3 -> "Tempo"
        4 -> "Threshold"
        else -> "Max Effort"
    }

    fun labelForBpm(
        bpm: Int,
        maxHr: Int = DEFAULT_MAX_HR,
        restingHr: Int = DEFAULT_RESTING_HR,
        useKarvonen: Boolean = false
    ): String = label(zoneNumber(bpm, maxHr, restingHr, useKarvonen))
}

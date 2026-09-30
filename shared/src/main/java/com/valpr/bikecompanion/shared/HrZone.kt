package com.valpr.bikecompanion.shared

/**
 * Single source of truth for heart-rate zone thresholds, shared by the phone and
 * the watch so both UIs color the same BPM identically for a given max HR.
 *
 * Zones (fraction of max HR): Z1 < 60%, Z2 60–70%, Z3 70–80%, Z4 80–90%, Z5 ≥ 90%.
 * Pure Kotlin (no Compose dependency) so `:shared` stays UI-free; each module maps
 * the zone number to its own color palette.
 */
object HrZone {
    const val DEFAULT_MAX_HR = 190

    fun zoneNumber(bpm: Int, maxHr: Int = DEFAULT_MAX_HR): Int {
        if (bpm <= 0 || maxHr <= 0) return 1
        val pct = bpm.toFloat() / maxHr
        return when {
            pct < 0.60f -> 1
            pct < 0.70f -> 2
            pct < 0.80f -> 3
            pct < 0.90f -> 4
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

    fun labelForBpm(bpm: Int, maxHr: Int = DEFAULT_MAX_HR): String =
        label(zoneNumber(bpm, maxHr))
}

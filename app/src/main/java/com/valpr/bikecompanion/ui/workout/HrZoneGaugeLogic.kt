package com.valpr.bikecompanion.ui.workout

import com.valpr.bikecompanion.shared.HrZone
import kotlin.math.roundToInt

/**
 * Pure semicircle-gauge math for the personal HR zone display.
 *
 * Framework-free so it stays plain-JUnit (AGENTS.md §8, same pattern as
 * `ui/summary/HrChartScaling`). Canvas rendering lives in [HrZoneGauge].
 *
 * Thresholds use the same rounded integers as [HrZone.zoneNumber]
 * (`(rest + reserve * pct).roundToInt()`), so the lit gauge segment always
 * agrees with the zone-derived HR number color — never off by one at
 * fractional thresholds (e.g. 0.7 * 182 = 127.4 → 127 on both sides).
 * (The integer `CalculatedHrZone` display ranges leave 1-BPM gaps by design;
 * the gauge stays continuous by treating each rounded threshold as the
 * shared boundary between adjacent arcs.)
 *
 * The gauge domain is truncated at the low end ([FLOOR_BPM], or the resting
 * rate when lower under Karvonen) so Z1 — which spans 0–60% — does not eat
 * the whole semicircle. Arc widths are proportional to real BPM span.
 */
object HrZoneGaugeLogic {
    const val FLOOR_BPM = 40f

    data class Segment(
        val lowBpm: Float,
        val highBpm: Float,
        val sweepFraction: Float
    )

    fun gaugeMax(maxHr: Int, restingHr: Int, useKarvonen: Boolean): Float {
        val (max, _) = resolveMaxRest(maxHr, restingHr, useKarvonen)
        return max.toFloat()
    }

    fun gaugeMin(maxHr: Int, restingHr: Int, useKarvonen: Boolean): Float {
        val (_, rest) = resolveMaxRest(maxHr, restingHr, useKarvonen)
        return if (useKarvonen) minOf(FLOOR_BPM, rest.toFloat()) else FLOOR_BPM
    }

    /** Five continuous zone boundaries as (low, high) pairs, low→high. */
    fun computeSegments(maxHr: Int, restingHr: Int, useKarvonen: Boolean): List<Segment> {
        val (max, rest) = resolveMaxRest(maxHr, restingHr, useKarvonen)
        val lo = if (useKarvonen) minOf(FLOOR_BPM, rest.toFloat()) else FLOOR_BPM
        val hi = max.toFloat().coerceAtLeast(lo + 10f)
        val reserve = max - rest

        // Same rounding as HrZone.zoneNumber: the gauge boundary and the
        // number-color boundary are the identical integer.
        fun threshold(pct: Float): Float = (rest + reserve * pct).roundToInt().toFloat()
        val bounds = floatArrayOf(lo, threshold(0.60f), threshold(0.70f), threshold(0.80f), threshold(0.90f), hi)
        // Clamp monotonic: a degenerate config (tiny reserve) must never invert arcs.
        for (i in 1 until bounds.size) {
            if (bounds[i] < bounds[i - 1]) bounds[i] = bounds[i - 1]
        }
        val total = (bounds.last() - bounds.first()).coerceAtLeast(1f)
        return (0 until 5).map { i ->
            val low = bounds[i].coerceIn(bounds.first(), bounds.last())
            val high = bounds[i + 1].coerceIn(bounds.first(), bounds.last())
            Segment(lowBpm = low, highBpm = high, sweepFraction = ((high - low) / total).coerceIn(0f, 1f))
        }
    }

    /**
     * Per-zone lit fraction for [bpm]: zones fully below [bpm] → 1,
     * the active zone → partial 0..1, zones above → 0.
     */
    fun fillFractions(bpm: Int, maxHr: Int, restingHr: Int, useKarvonen: Boolean): List<Float> {
        if (bpm <= 0) return List(5) { 0f }
        return computeSegments(maxHr, restingHr, useKarvonen).map { seg ->
            val width = seg.highBpm - seg.lowBpm
            if (width <= 0f) {
                if (bpm >= seg.highBpm) 1f else 0f
            } else {
                ((bpm - seg.lowBpm) / width).coerceIn(0f, 1f)
            }
        }
    }

    private fun resolveMaxRest(maxHr: Int, restingHr: Int, useKarvonen: Boolean): Pair<Int, Int> {
        val safeMaxHr = if (maxHr <= 0) HrZone.DEFAULT_MAX_HR else maxHr
        return if (useKarvonen) {
            val clampedMax = safeMaxHr.coerceAtLeast(restingHr + 10).coerceAtLeast(40)
            val clampedRest = restingHr.coerceIn(30, clampedMax - 1)
            clampedMax to clampedRest
        } else {
            safeMaxHr.coerceAtLeast(1) to 0
        }
    }
}

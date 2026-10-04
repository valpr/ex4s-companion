package com.valpr.bikecompanion.ui.workout

/**
 * Pure helper to compute discrete quick-tap preset resistance levels
 * for a given bike's resistance range.
 */
object ResistancePresets {
    fun forRange(range: IntRange): List<Int> {
        val span = range.last - range.first
        if (span <= 0) return listOf(range.first)
        if (span <= 4) return range.toList()

        // For Echelon 1..32, preserve the familiar 4-step increments
        if (range.first == 1 && range.last == 32) {
            return listOf(1, 4, 8, 12, 16, 20, 24, 28, 32)
        }

        // For arbitrary ranges, distribute ~5 to 9 presets across the range
        val step = when {
            span <= 8 -> 1
            span <= 16 -> 2
            span <= 32 -> 4
            span <= 64 -> 8
            else -> (span / 8).coerceAtLeast(1)
        }
        val list = mutableListOf<Int>()
        list.add(range.first)
        var current = ((range.first + step - 1) / step) * step
        if (current <= range.first) current += step
        while (current < range.last) {
            list.add(current)
            current += step
        }
        if (list.last() != range.last) {
            list.add(range.last)
        }
        return list.distinct()
    }
}

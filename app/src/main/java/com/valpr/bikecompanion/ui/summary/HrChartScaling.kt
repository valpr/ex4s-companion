package com.valpr.bikecompanion.ui.summary

/** Pure HR chart scaling math (JVM-testable; Canvas rendering stays in the composable). */
object HrChartScaling {
    data class Scale(val minHr: Float, val maxHr: Float, val range: Float)

    fun computeScale(samples: List<Int>): Scale? {
        val hr = samples.filter { it > 0 }
        if (hr.isEmpty()) return null
        val max = hr.maxOrNull()!!.toFloat().coerceAtLeast(100f)
        val min = hr.minOrNull()!!.toFloat().coerceAtMost(60f)
        val range = (max - min).coerceAtLeast(10f)
        return Scale(minHr = min, maxHr = max, range = range)
    }

    fun normalizedY(bpm: Int, scale: Scale): Float = ((bpm - scale.minHr) / scale.range).coerceIn(0f, 1f)

    fun stepX(canvasWidth: Float, sampleCount: Int): Float = canvasWidth / (sampleCount - 1).coerceAtLeast(1)
}

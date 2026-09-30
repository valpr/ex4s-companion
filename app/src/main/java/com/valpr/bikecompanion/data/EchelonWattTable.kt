package com.valpr.bikecompanion.data

import kotlin.math.roundToInt

/**
 * Authoritative Echelon EX-4S Watt Table extracted from QZ (qdomyos-zwift).
 * Maps (resistance: 0..32, cadence: 0..100+ RPM in 10 RPM increments) to mechanical power (watts).
 * Uses linear interpolation across 10-RPM cadence buckets and linear extrapolation above 100 RPM.
 */
object EchelonWattTable {

    private const val EPSILON = 4.94065645841247E-324
    const val MIN_RESISTANCE = 1
    const val MAX_RESISTANCE = 32

    private val WATT_TABLE = arrayOf(
        doubleArrayOf(EPSILON, 1.0, 2.2, 4.8, 9.5, 13.6, 16.7, 22.6, 26.3, 29.2, 47.0), // 0
        doubleArrayOf(EPSILON, 1.0, 2.2, 4.8, 9.5, 13.6, 16.7, 22.6, 26.3, 29.2, 47.0), // 1
        doubleArrayOf(EPSILON, 1.3, 3.0, 5.4, 10.4, 14.5, 18.5, 24.6, 27.6, 33.5, 49.5), // 2
        doubleArrayOf(EPSILON, 1.5, 3.7, 6.7, 11.7, 15.9, 19.6, 26.1, 30.8, 35.2, 51.2), // 3
        doubleArrayOf(EPSILON, 1.6, 4.7, 7.5, 13.7, 17.6, 22.6, 29.0, 36.9, 42.6, 57.2), // 4
        doubleArrayOf(EPSILON, 1.8, 5.2, 8.0, 14.8, 19.1, 23.5, 32.5, 37.5, 50.8, 61.8), // 5
        doubleArrayOf(EPSILON, 1.9, 5.7, 8.7, 15.6, 20.2, 25.5, 33.5, 39.6, 52.1, 65.3), // 6
        doubleArrayOf(EPSILON, 2.0, 6.2, 9.5, 16.8, 21.8, 28.1, 37.0, 42.8, 57.8, 68.4), // 7
        doubleArrayOf(EPSILON, 2.1, 6.8, 10.8, 18.2, 23.6, 29.5, 40.0, 47.6, 60.5, 72.1), // 8
        doubleArrayOf(EPSILON, 2.2, 7.3, 11.5, 19.3, 26.3, 33.5, 45.3, 51.8, 66.7, 76.8), // 9
        doubleArrayOf(EPSILON, 2.4, 7.9, 12.7, 20.8, 29.8, 37.6, 52.2, 56.2, 73.5, 83.6), // 10
        doubleArrayOf(EPSILON, 2.6, 8.5, 13.5, 23.5, 33.6, 41.9, 55.1, 59.0, 78.6, 89.7), // 11
        doubleArrayOf(EPSILON, 2.7, 9.1, 14.2, 25.6, 35.4, 45.3, 57.3, 62.8, 81.3, 95.0), // 12
        doubleArrayOf(EPSILON, 2.9, 9.6, 16.8, 29.1, 37.5, 49.6, 62.5, 69.0, 84.7, 99.3), // 13
        doubleArrayOf(EPSILON, 3.0, 10.0, 22.3, 31.2, 40.3, 51.8, 65.0, 70.0, 92.6, 108.2), // 14
        doubleArrayOf(EPSILON, 3.2, 10.4, 24.0, 36.6, 42.5, 56.3, 74.0, 85.0, 98.2, 123.5), // 15
        doubleArrayOf(EPSILON, 3.5, 10.9, 25.1, 38.5, 47.6, 65.4, 83.0, 93.0, 114.8, 136.8), // 16
        doubleArrayOf(EPSILON, 3.7, 11.5, 26.0, 41.0, 53.2, 71.6, 90.0, 100.0, 121.7, 149.2), // 17
        doubleArrayOf(EPSILON, 4.0, 12.1, 27.5, 43.6, 56.0, 82.3, 101.0, 113.6, 143.0, 162.8), // 18
        doubleArrayOf(EPSILON, 4.2, 12.7, 29.7, 46.7, 64.2, 87.9, 109.2, 128.9, 154.0, 172.3), // 19
        doubleArrayOf(EPSILON, 4.5, 13.7, 32.0, 50.0, 71.8, 95.6, 113.8, 135.6, 165.0, 185.0), // 20
        doubleArrayOf(EPSILON, 4.7, 14.9, 34.5, 54.2, 77.0, 100.7, 127.0, 147.6, 180.0, 200.0), // 21
        doubleArrayOf(EPSILON, 5.0, 15.8, 36.5, 58.3, 83.4, 110.1, 136.0, 168.1, 196.0, 213.5), // 22
        doubleArrayOf(EPSILON, 5.6, 17.0, 39.5, 64.3, 88.8, 123.4, 154.0, 182.0, 210.0, 235.0), // 23
        doubleArrayOf(EPSILON, 6.1, 18.2, 44.0, 70.7, 99.9, 133.3, 166.0, 198.0, 230.0, 253.5), // 24
        doubleArrayOf(EPSILON, 6.8, 19.4, 49.0, 79.0, 108.8, 147.2, 185.0, 217.0, 255.2, 278.0), // 25
        doubleArrayOf(EPSILON, 7.6, 22.0, 54.8, 88.0, 127.0, 167.0, 212.0, 244.0, 287.0, 305.0), // 26
        doubleArrayOf(EPSILON, 8.7, 26.0, 62.0, 100.0, 145.0, 190.0, 242.0, 281.0, 315.1, 350.0), // 27
        doubleArrayOf(EPSILON, 9.2, 30.0, 71.0, 114.4, 161.6, 215.1, 275.1, 317.0, 358.5, 390.0), // 28
        doubleArrayOf(EPSILON, 9.8, 36.0, 82.5, 134.5, 195.3, 252.5, 313.7, 360.0, 420.3, 460.0), // 29
        doubleArrayOf(EPSILON, 10.5, 43.0, 95.0, 157.1, 228.4, 300.1, 374.1, 403.8, 487.8, 540.0), // 30
        doubleArrayOf(EPSILON, 12.5, 48.0, 99.3, 162.2, 232.9, 310.4, 400.3, 435.5, 530.5, 589.0), // 31
        doubleArrayOf(EPSILON, 13.0, 53.0, 102.0, 170.3, 242.0, 320.0, 427.9, 475.2, 570.0, 625.0) // 32
    )

    /**
     * Calculates estimated power output in watts based on resistance level (1..32) and cadence (RPM).
     */
    fun calculateWatts(resistance: Int, cadenceRpm: Double): Double {
        if (cadenceRpm <= 0.0) return 0.0

        val level = resistance.coerceIn(0, WATT_TABLE.lastIndex)
        val wattsOfLevel = WATT_TABLE[level]

        val wattStep = (cadenceRpm / 10.0).toInt()
        val maxBucketIndex = wattsOfLevel.lastIndex // 10 (representing 100 RPM)

        if (wattStep >= maxBucketIndex) {
            // Extrapolation above 100 RPM: (cadence / 100.0) * wattsAt100Rpm
            return (cadenceRpm / 100.0) * wattsOfLevel[maxBucketIndex]
        }

        val wattBase = wattsOfLevel[wattStep]
        val wattNext = wattsOfLevel[wattStep + 1]
        val stepProgress = (cadenceRpm % 10.0)

        return wattBase + ((wattNext - wattBase) / 10.0) * stepProgress
    }

    /**
     * Convenience method returning rounded integer watts.
     */
    fun calculateWattsInt(resistance: Int, cadenceRpm: Double): Int = calculateWatts(resistance, cadenceRpm).roundToInt().coerceAtLeast(0)

    /**
     * Finds the closest resistance level (1..32) to hit a target power at the given cadence.
     */
    fun resistanceFromPowerTarget(targetWatts: Int, cadenceRpm: Double): Int {
        if (cadenceRpm <= 0.0) return MIN_RESISTANCE

        var bestResistance = MIN_RESISTANCE
        var smallestDiff = Double.MAX_VALUE

        for (res in MIN_RESISTANCE..MAX_RESISTANCE) {
            val estimated = calculateWatts(res, cadenceRpm)
            val diff = kotlin.math.abs(estimated - targetWatts)
            if (diff < smallestDiff) {
                smallestDiff = diff
                bestResistance = res
            }
        }
        return bestResistance
    }
}

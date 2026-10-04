package com.valpr.bikecompanion.bike.api

/**
 * Pure power model for bikes that do not report measured power directly or that
 * require feedforward resistance calculation for ERG mode.
 */
interface ResistanceModel {
    val range: IntRange
    fun watts(level: Int, cadenceRpm: Double): Double
    fun levelForWatts(targetWatts: Int, cadenceRpm: Double): Int
}

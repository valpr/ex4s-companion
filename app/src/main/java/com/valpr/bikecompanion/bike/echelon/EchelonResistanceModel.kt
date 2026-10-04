package com.valpr.bikecompanion.bike.echelon

import com.valpr.bikecompanion.bike.api.ResistanceModel
import com.valpr.bikecompanion.data.EchelonWattTable

object EchelonResistanceModel : ResistanceModel {
    override val range: IntRange = EchelonWattTable.MIN_RESISTANCE..EchelonWattTable.MAX_RESISTANCE

    override fun watts(level: Int, cadenceRpm: Double): Double = EchelonWattTable.calculateWatts(level, cadenceRpm)

    override fun levelForWatts(targetWatts: Int, cadenceRpm: Double): Int = EchelonWattTable.resistanceFromPowerTarget(targetWatts, cadenceRpm)
}

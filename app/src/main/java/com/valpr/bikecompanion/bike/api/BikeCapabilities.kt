package com.valpr.bikecompanion.bike.api

/**
 * Declares the hardware capabilities and constraints of a connected or supported bike.
 */
data class BikeCapabilities(
    val modelName: String = "Echelon EX-4S",
    val resistanceRange: IntRange = 1..32,
    val reportsMeasuredPower: Boolean = false,
    val supportsNativeErg: Boolean = false,
    val reportsDistance: Boolean = true
) {
    companion object {
        val DEFAULT_ECHELON = BikeCapabilities()
    }
}

package com.valpr.bikecompanion.bike.api

import java.util.UUID

enum class MatchScore {
    NONE,
    WEAK,
    STRONG
}

data class ScanAdvert(
    val name: String?,
    val address: String,
    val serviceUuids: List<UUID>,
    val rssi: Int
)

interface BikeDriver {
    val id: String
    val displayName: String
    fun match(advert: ScanAdvert): MatchScore
    fun createProtocol(): BikeProtocol
}

package com.valpr.bikecompanion.bike.ftms

import com.valpr.bikecompanion.bike.api.BikeDriver
import com.valpr.bikecompanion.bike.api.BikeProtocol
import com.valpr.bikecompanion.bike.api.MatchScore
import com.valpr.bikecompanion.bike.api.ScanAdvert

/**
 * Bike driver matching Bluetooth SIG Fitness Machine Service (FTMS) devices.
 *
 * Matches devices advertising service UUID 0x1826 (STRONG) or devices whose advertised names
 * match known smart bike/trainer brands (WEAK).
 */
class FtmsDriver : BikeDriver {
    override val id: String = "ftms"
    override val displayName: String = "Standard FTMS Bike / Smart Trainer"

    override fun match(advert: ScanAdvert): MatchScore {
        // 1. Direct match on FTMS 0x1826 service UUID in advertisement
        if (advert.serviceUuids.contains(FtmsBikeProtocol.FTMS_SERVICE_UUID)) {
            return MatchScore.STRONG
        }

        // 2. Name-based match for common FTMS trainers/bikes
        val name = advert.name?.uppercase() ?: return MatchScore.NONE
        val knownFtmsKeywords = listOf(
            "FTMS",
            "KICKR",
            "TACX",
            "ZWIFT",
            "WAHOO",
            "SARIS",
            "ELITE",
            "NEO",
            "FLUX",
            "SUITO",
            "DIRETO"
        )
        if (knownFtmsKeywords.any { name.contains(it) }) {
            return MatchScore.WEAK
        }
        return MatchScore.NONE
    }

    override fun createProtocol(): BikeProtocol = FtmsBikeProtocol()
}

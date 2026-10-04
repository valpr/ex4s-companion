package com.valpr.bikecompanion.bike.echelon

import com.valpr.bikecompanion.bike.api.BikeDriver
import com.valpr.bikecompanion.bike.api.BikeProtocol
import com.valpr.bikecompanion.bike.api.MatchScore
import com.valpr.bikecompanion.bike.api.ScanAdvert
import com.valpr.bikecompanion.ble.EchelonBleLogic
import com.valpr.bikecompanion.ble.EchelonGattAttributes

class EchelonDriver : BikeDriver {
    override val id: String = "echelon"
    override val displayName: String = "Echelon EX-4S"

    override fun match(advert: ScanAdvert): MatchScore {
        if (advert.serviceUuids.contains(EchelonGattAttributes.SERVICE_ECHELON)) {
            return MatchScore.STRONG
        }
        val name = advert.name ?: ""
        if (EchelonBleLogic.isEchelonDevice(name, hasEchelonServiceUuid = false)) {
            return MatchScore.WEAK
        }
        return MatchScore.NONE
    }

    override fun createProtocol(): BikeProtocol = EchelonBikeProtocol()
}

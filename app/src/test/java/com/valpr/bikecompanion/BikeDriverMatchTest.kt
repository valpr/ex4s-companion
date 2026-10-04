package com.valpr.bikecompanion

import com.valpr.bikecompanion.bike.api.MatchScore
import com.valpr.bikecompanion.bike.api.ScanAdvert
import com.valpr.bikecompanion.bike.echelon.EchelonDriver
import com.valpr.bikecompanion.ble.EchelonGattAttributes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class BikeDriverMatchTest {

    private val driver = EchelonDriver()

    @Test
    fun match_withEchelonServiceUuid_returnsStrong() {
        val advert = ScanAdvert(
            name = "Some Random Device",
            address = "AA:BB:CC:DD:EE:FF",
            serviceUuids = listOf(EchelonGattAttributes.SERVICE_ECHELON),
            rssi = -60
        )
        assertEquals(MatchScore.STRONG, driver.match(advert))
    }

    @Test
    fun match_withEchelonNameHeuristic_returnsWeak() {
        val names = listOf(
            "ECH-EX4S-1234",
            "Echelon EX-3",
            "SPORT BIKE",
            "EX-5S BIKE"
        )
        for (name in names) {
            val advert = ScanAdvert(
                name = name,
                address = "AA:BB:CC:DD:EE:FF",
                serviceUuids = emptyList(),
                rssi = -60
            )
            assertEquals("Expected WEAK match for '$name'", MatchScore.WEAK, driver.match(advert))
        }
    }

    @Test
    fun match_unrelatedDevice_returnsNone() {
        val advert = ScanAdvert(
            name = "Smart TV",
            address = "11:22:33:44:55:66",
            serviceUuids = listOf(UUID.randomUUID()),
            rssi = -80
        )
        assertEquals(MatchScore.NONE, driver.match(advert))
    }
}

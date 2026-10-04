package com.valpr.bikecompanion

import com.valpr.bikecompanion.bike.api.MatchScore
import com.valpr.bikecompanion.bike.api.ScanAdvert
import com.valpr.bikecompanion.bike.ftms.FtmsBikeProtocol
import com.valpr.bikecompanion.bike.ftms.FtmsDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FtmsDriverTest {

    private val driver = FtmsDriver()

    @Test
    fun match_matchesServiceUuid0x1826_asStrong() {
        val advert = ScanAdvert(
            name = "Smart Trainer",
            address = "AA:BB:CC:DD:EE:FF",
            serviceUuids = listOf(FtmsBikeProtocol.FTMS_SERVICE_UUID),
            rssi = -60
        )
        assertEquals(MatchScore.STRONG, driver.match(advert))
    }

    @Test
    fun match_matchesKnownFtmsKeywordsInName_asWeak() {
        val wahoo = ScanAdvert(name = "WAHOO KICKR 4821", address = "00:11:22:33:44:55", serviceUuids = emptyList(), rssi = -65)
        val tacx = ScanAdvert(name = "Tacx NEO 2T", address = "00:11:22:33:44:56", serviceUuids = emptyList(), rssi = -65)
        val zwift = ScanAdvert(name = "Zwift Hub", address = "00:11:22:33:44:57", serviceUuids = emptyList(), rssi = -65)
        val elite = ScanAdvert(name = "ELITE Suito-T", address = "00:11:22:33:44:58", serviceUuids = emptyList(), rssi = -65)

        assertEquals(MatchScore.WEAK, driver.match(wahoo))
        assertEquals(MatchScore.WEAK, driver.match(tacx))
        assertEquals(MatchScore.WEAK, driver.match(zwift))
        assertEquals(MatchScore.WEAK, driver.match(elite))
    }

    @Test
    fun match_doesNotMatchEchelonOrUnknownDevices() {
        val echelon = ScanAdvert(
            name = "ECH-EX4S-1234",
            address = "00:11:22:33:44:59",
            serviceUuids = listOf(UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")),
            rssi = -55
        )
        val headphone = ScanAdvert(name = "Sony WH-1000XM4", address = "00:11:22:33:44:60", serviceUuids = emptyList(), rssi = -70)

        assertEquals(MatchScore.NONE, driver.match(echelon))
        assertEquals(MatchScore.NONE, driver.match(headphone))
    }

    @Test
    fun createProtocol_setsCapabilities() {
        val protocol = driver.createProtocol()

        assertEquals("FTMS Bike", protocol.capabilities.modelName)
        assertTrue(protocol.capabilities.supportsNativeErg)
        assertTrue(protocol.capabilities.reportsMeasuredPower)
    }
}

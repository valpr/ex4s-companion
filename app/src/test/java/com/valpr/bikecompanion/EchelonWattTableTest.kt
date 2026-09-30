package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.EchelonWattTable
import org.junit.Assert.assertEquals
import org.junit.Test

class EchelonWattTableTest {

    @Test
    fun testCadenceZeroReturnsZeroWatts() {
        assertEquals(0.0, EchelonWattTable.calculateWatts(1, 0.0), 0.001)
        assertEquals(0.0, EchelonWattTable.calculateWatts(16, 0.0), 0.001)
        assertEquals(0.0, EchelonWattTable.calculateWatts(32, 0.0), 0.001)
        assertEquals(0, EchelonWattTable.calculateWattsInt(16, 0.0))
    }

    @Test
    fun testExactBucketValues() {
        // Level 16 at 70 RPM (bucket 7) -> 83.0 W
        val wattsAt70 = EchelonWattTable.calculateWatts(16, 70.0)
        assertEquals(83.0, wattsAt70, 0.001)

        // Level 16 at 80 RPM (bucket 8) -> 93.0 W
        val wattsAt80 = EchelonWattTable.calculateWatts(16, 80.0)
        assertEquals(93.0, wattsAt80, 0.001)

        // Level 32 at 100 RPM (bucket 10) -> 625.0 W
        val wattsAt100 = EchelonWattTable.calculateWatts(32, 100.0)
        assertEquals(625.0, wattsAt100, 0.001)
    }

    @Test
    fun testLinearInterpolation() {
        // Level 16 at 75 RPM (midway between 70 RPM [83.0W] and 80 RPM [93.0W]) -> 88.0 W
        val wattsAt75 = EchelonWattTable.calculateWatts(16, 75.0)
        assertEquals(88.0, wattsAt75, 0.001)

        // Level 16 at 72 RPM: 83.0 + ((93.0 - 83.0) / 10.0) * 2 = 85.0 W
        val wattsAt72 = EchelonWattTable.calculateWatts(16, 72.0)
        assertEquals(85.0, wattsAt72, 0.001)
    }

    @Test
    fun testExtrapolationAbove100Rpm() {
        // Level 16 at 100 RPM = 136.8 W
        // At 110 RPM: (110 / 100.0) * 136.8 = 150.48 W
        val wattsAt110 = EchelonWattTable.calculateWatts(16, 110.0)
        assertEquals(150.48, wattsAt110, 0.001)
    }

    @Test
    fun testReverseResistanceLookup() {
        // At 70 RPM, Level 16 produces 83.0 W
        val foundResistance = EchelonWattTable.resistanceFromPowerTarget(targetWatts = 83, cadenceRpm = 70.0)
        assertEquals(16, foundResistance)
    }
}

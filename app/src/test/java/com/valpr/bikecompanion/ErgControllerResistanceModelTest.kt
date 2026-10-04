package com.valpr.bikecompanion

import com.valpr.bikecompanion.bike.api.ResistanceModel
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.engine.ErgState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErgControllerResistanceModelTest {

    private val customModel = object : ResistanceModel {
        override val range: IntRange = 1..10
        override fun watts(level: Int, cadenceRpm: Double): Double = level * 20.0
        override fun levelForWatts(targetWatts: Int, cadenceRpm: Double): Int = (targetWatts / 20).coerceIn(range)
    }

    @Test
    fun customResistanceModel_boundsNominalAndTargetResistance() {
        val controller = ErgController(
            resistanceModel = customModel,
            recoveryResistance = 2
        )

        assertEquals(1, controller.minResistance)
        assertEquals(10, controller.maxResistance)

        // Target 100W -> levelForWatts returns 5
        val decision = controller.update(
            targetWatts = 100,
            actualWatts = 100,
            rawCadence = 80.0
        )

        assertEquals(ErgState.ACTIVE, decision.state)
        assertEquals(5, decision.nominalResistance)
        assertEquals(5, decision.targetResistance)
    }

    @Test
    fun customResistanceModel_extremeTarget_clampsToMaxResistance() {
        val controller = ErgController(
            resistanceModel = customModel,
            recoveryResistance = 2
        )

        // Target 500W -> levelForWatts returns 10 (coerced)
        val decision = controller.update(
            targetWatts = 500,
            actualWatts = 200,
            rawCadence = 80.0
        )

        assertTrue(decision.targetResistance <= 10)
    }

    @Test
    fun customResistanceModel_integralTrimClampedToModelRange() {
        val controller = ErgController(
            resistanceModel = customModel,
            recoveryResistance = 2
        )

        // Nominal is 10, trim tries to push beyond 10
        for (i in 1..10) {
            controller.update(
                targetWatts = 300,
                actualWatts = 50,
                rawCadence = 80.0,
                dtSeconds = 1.0
            )
        }

        val decision = controller.update(
            targetWatts = 300,
            actualWatts = 50,
            rawCadence = 80.0,
            dtSeconds = 1.0
        )

        assertEquals(10, decision.targetResistance)
    }
}

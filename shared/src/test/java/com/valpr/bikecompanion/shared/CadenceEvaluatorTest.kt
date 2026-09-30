package com.valpr.bikecompanion.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class CadenceEvaluatorTest {

    @Test
    fun evaluate_stopped_structuredReturnsTargetAndMuted() {
        val result = CadenceEvaluator.evaluate(actualCadence = 0, targetCadence = 80)
        assertEquals(CadenceState.STOPPED, result.state)
        assertEquals("TARGET 80", result.displayLabel)
        assertEquals("MUTED", result.colorToken)
    }

    @Test
    fun evaluate_stopped_freeRideReturnsIdealAndMuted() {
        val result = CadenceEvaluator.evaluate(actualCadence = 0, targetCadence = null, preferredCadence = 85)
        assertEquals(CadenceState.STOPPED, result.state)
        assertEquals("IDEAL 80–90", result.displayLabel)
        assertEquals("MUTED", result.colorToken)
    }

    @Test
    fun evaluate_structured_tooSlow_triggersSpinUpAmber() {
        // Target is 80; tolerance low is 5, so < 75 is TOO_SLOW
        val result = CadenceEvaluator.evaluate(actualCadence = 72, targetCadence = 80)
        assertEquals(CadenceState.TOO_SLOW, result.state)
        assertEquals("▲ SPIN UP (80)", result.displayLabel)
        assertEquals("AMBER", result.colorToken)
    }

    @Test
    fun evaluate_structured_onTarget_withinTolerance_returnsGreen() {
        // Target 80; in range [75, 88]
        val at75 = CadenceEvaluator.evaluate(actualCadence = 75, targetCadence = 80)
        assertEquals(CadenceState.ON_TARGET, at75.state)
        assertEquals("TARGET 80", at75.displayLabel)
        assertEquals("GREEN", at75.colorToken)

        val at80 = CadenceEvaluator.evaluate(actualCadence = 80, targetCadence = 80)
        assertEquals(CadenceState.ON_TARGET, at80.state)
        assertEquals("TARGET 80", at80.displayLabel)
        assertEquals("GREEN", at80.colorToken)

        val at88 = CadenceEvaluator.evaluate(actualCadence = 88, targetCadence = 80)
        assertEquals(CadenceState.ON_TARGET, at88.state)
        assertEquals("TARGET 80", at88.displayLabel)
        assertEquals("GREEN", at88.colorToken)
    }

    @Test
    fun evaluate_structured_tooFast_triggersEaseUpCyan() {
        // Target 80; tolerance high is 8, so > 88 is TOO_FAST
        val result = CadenceEvaluator.evaluate(actualCadence = 92, targetCadence = 80)
        assertEquals(CadenceState.TOO_FAST, result.state)
        assertEquals("▼ EASE UP (80)", result.displayLabel)
        assertEquals("CYAN", result.colorToken)
    }

    @Test
    fun evaluate_freeRide_tooSlow_triggersSpinUpAmber() {
        // Preferred 85; ideal is [75, 95]
        val result = CadenceEvaluator.evaluate(actualCadence = 68, targetCadence = null, preferredCadence = 85)
        assertEquals(CadenceState.TOO_SLOW, result.state)
        assertEquals("▲ SPIN UP (~85)", result.displayLabel)
        assertEquals("AMBER", result.colorToken)
    }

    @Test
    fun evaluate_freeRide_inOptimalRange_returnsGreen() {
        val result = CadenceEvaluator.evaluate(actualCadence = 85, targetCadence = null, preferredCadence = 85)
        assertEquals(CadenceState.ON_TARGET, result.state)
        assertEquals("IDEAL 80–90", result.displayLabel)
        assertEquals("GREEN", result.colorToken)
    }

    @Test
    fun evaluate_freeRide_tooFast_triggersFastCyan() {
        val result = CadenceEvaluator.evaluate(actualCadence = 102, targetCadence = null, preferredCadence = 85)
        assertEquals(CadenceState.TOO_FAST, result.state)
        assertEquals("▼ FAST (~85)", result.displayLabel)
        assertEquals("CYAN", result.colorToken)
    }

    @Test
    fun evaluate_freeRide_customPreferredCadence_adaptsLabel() {
        val result = CadenceEvaluator.evaluate(actualCadence = 95, targetCadence = null, preferredCadence = 95)
        assertEquals(CadenceState.ON_TARGET, result.state)
        assertEquals("IDEAL ~95", result.displayLabel)
        assertEquals("GREEN", result.colorToken)
    }
}

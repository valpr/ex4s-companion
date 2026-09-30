package com.valpr.bikecompanion

import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wearable.WearSyncDecision
import com.valpr.bikecompanion.wearable.WearSyncKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSyncDecisionTest {

    private fun keys(
        status: Int = WorkoutStateMessage.STATUS_RUNNING,
        bailout: Boolean = false,
        floor: Boolean = false,
        capped: Boolean = false,
        target: Int = 200,
        maxHr: Int = 190
    ) = WearSyncKeys(status, bailout, floor, capped, target, maxHr)

    @Test
    fun blankNode_neverSyncs_evenOnTransition() {
        assertFalse(WearSyncDecision.shouldSync(10_000L, 0L, keys(bailout = true), keys(), true))
        assertFalse(WearSyncDecision.shouldSync(10_000L, 0L, keys(), null, true))
    }

    @Test
    fun firstSend_syncs() {
        assertTrue(WearSyncDecision.shouldSync(1_000L, 0L, keys(), null, false))
    }

    @Test
    fun steadyWithinWindow_suppressed_afterWindow_syncs() {
        val k = keys()
        assertFalse(WearSyncDecision.shouldSync(1_000L, 0L, k, k, false))
        assertFalse(WearSyncDecision.shouldSync(1_999L, 0L, k, k, false))
        assertTrue(WearSyncDecision.shouldSync(2_000L, 0L, k, k, false))
        assertTrue(WearSyncDecision.shouldSync(5_000L, 0L, k, k, false))
    }

    @Test
    fun eachTransitionKey_burstsImmediately() {
        val base = keys()
        val now = 1_000L
        // status
        assertTrue(WearSyncDecision.shouldSync(now, now, base.copy(statusCode = WorkoutStateMessage.STATUS_PAUSED), base, false))
        // bailout
        assertTrue(WearSyncDecision.shouldSync(now, now, base.copy(isBailout = true), base, false))
        // cadence floor
        assertTrue(WearSyncDecision.shouldSync(now, now, base.copy(isCadenceFloor = true), base, false))
        // HR cap
        assertTrue(WearSyncDecision.shouldSync(now, now, base.copy(isHrCapped = true), base, false))
        // target watts
        assertTrue(WearSyncDecision.shouldSync(now, now, base.copy(targetWatts = 180), base, false))
        // maxHr
        assertTrue(WearSyncDecision.shouldSync(now, now, base.copy(athleteMaxHr = 185), base, false))
    }
}

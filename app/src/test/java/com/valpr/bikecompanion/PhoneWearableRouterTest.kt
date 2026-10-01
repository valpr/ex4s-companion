package com.valpr.bikecompanion

import com.valpr.bikecompanion.shared.HeartRateBatch
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.wearable.PhoneWearableRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearableRouterTest {

    @Test
    fun hrBatch_valid_forwardsLatest() {
        val batch = HeartRateBatch(timestampMs = 9_999L, bpmSamples = listOf(140, 152))
        val action = PhoneWearableRouter.route(WearableProtocol.PATH_HEART_RATE, batch.toByteArray())
        assertTrue(action is PhoneWearableRouter.Action.ForwardHeartRate)
        action as PhoneWearableRouter.Action.ForwardHeartRate
        assertEquals(152, action.bpm)
        assertEquals(9_999L, action.timestampMs)
    }

    @Test
    fun hrBatch_corruptOrEmpty_ignored() {
        assertEquals(
            PhoneWearableRouter.Action.Ignore,
            PhoneWearableRouter.route(WearableProtocol.PATH_HEART_RATE, byteArrayOf(0x00, 0x01))
        )
        val empty = HeartRateBatch(timestampMs = 1L, bpmSamples = emptyList())
        assertEquals(
            PhoneWearableRouter.Action.Ignore,
            PhoneWearableRouter.route(WearableProtocol.PATH_HEART_RATE, empty.toByteArray())
        )
        assertNull(PhoneWearableRouter.extractHeartRateBpm(byteArrayOf(0x01)))
        assertNull(PhoneWearableRouter.extractHeartRateBpm(empty.toByteArray()))
    }

    @Test
    fun bailoutAndResume_classified() {
        assertEquals(
            PhoneWearableRouter.Action.Clutch,
            PhoneWearableRouter.route(WearableProtocol.PATH_ROTARY_BAILOUT, byteArrayOf(0x01))
        )
        assertEquals(
            PhoneWearableRouter.Action.Resume,
            PhoneWearableRouter.route(WearableProtocol.PATH_RESUME_SLAP, byteArrayOf(0x01))
        )
        assertEquals(
            PhoneWearableRouter.Action.RequestWorkoutState,
            PhoneWearableRouter.route(WearableProtocol.PATH_REQUEST_STATE, byteArrayOf(0x01))
        )
        assertEquals(
            PhoneWearableRouter.Action.Ignore,
            PhoneWearableRouter.route("/unknown", byteArrayOf(0x01))
        )
    }
}

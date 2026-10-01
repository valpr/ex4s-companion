package com.valpr.bikecompanion.wearable

import com.valpr.bikecompanion.shared.HeartRateBatch
import com.valpr.bikecompanion.shared.PingPongMessage
import com.valpr.bikecompanion.shared.WearableProtocol

/**
 * Pure inbound router for phone-side watch messages (plain-JUnit testable).
 * [PhoneWearableManager.onMessageReceived] delegates here for decisions;
 * side effects (session updates) stay in the manager.
 */
object PhoneWearableRouter {

    sealed interface Action {
        data class ForwardHeartRate(val bpm: Int, val timestampMs: Long) : Action
        data object Clutch : Action
        data object Resume : Action
        data object Pause : Action
        data object RequestWorkoutState : Action
        data class Pong(val timestampMs: Long) : Action
        data class Ping(val timestampMs: Long) : Action
        data object Ignore : Action
    }

    fun route(path: String, data: ByteArray): Action = when (path) {
        WearableProtocol.PATH_HEART_RATE -> {
            val batch = HeartRateBatch.fromByteArray(data)
            if (batch != null && batch.latestBpm > 0) {
                Action.ForwardHeartRate(batch.latestBpm, batch.timestampMs)
            } else {
                Action.Ignore
            }
        }
        WearableProtocol.PATH_ROTARY_BAILOUT -> Action.Clutch
        WearableProtocol.PATH_RESUME_SLAP -> Action.Resume
        WearableProtocol.PATH_PAUSE_SESSION -> Action.Pause
        WearableProtocol.PATH_REQUEST_STATE -> Action.RequestWorkoutState
        WearableProtocol.PATH_PONG -> {
            val pong = PingPongMessage.fromByteArray(data)
            if (pong != null) Action.Pong(pong.timestampMs) else Action.Ignore
        }
        WearableProtocol.PATH_PING -> {
            val ping = PingPongMessage.fromByteArray(data)
            if (ping != null) Action.Ping(ping.timestampMs) else Action.Ignore
        }
        else -> Action.Ignore
    }

    /** Returns a forwardable BPM, or null when the payload must be dropped. */
    fun extractHeartRateBpm(data: ByteArray): Int? {
        val batch = HeartRateBatch.fromByteArray(data) ?: return null
        return if (batch.latestBpm > 0) batch.latestBpm else null
    }
}

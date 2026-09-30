package com.valpr.bikecompanion.wear.messaging

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.MainActivity
import com.valpr.bikecompanion.wear.WearBikeApplication
import com.valpr.bikecompanion.wear.service.WearWorkoutTrackingService

/**
 * Background listener service on Wear OS to wake the companion app or update state
 * when messages arrive from the phone.
 *
 * Low-power invariant: plain 1Hz workout-state telemetry must NOT wake the AP every
 * second. Only wake when user attention is required (bailout / resume / haptics /
 * pause / completion). Active telemetry is received by [WearMessageManager] while
 * the app is already open.
 */
class WearMessageListenerService : WearableListenerService() {
    enum class ServiceAction {
        START,
        STOP,
        NONE
    }

    companion object {
        private const val TAG = "WearMsgListenerSvc"

        /** Pure wake-filter (JVM-testable): steady telemetry must not wake the AP. */
        fun shouldWakeForMessage(path: String, data: ByteArray): Boolean = when (path) {
            WearableProtocol.PATH_HAPTIC_TRIGGER -> true
            WearableProtocol.PATH_WORKOUT_STATE -> {
                val state = WorkoutStateMessage.fromByteArray(data)
                state?.isBailoutActive == true ||
                    state?.isCadenceFloorActive == true ||
                    state?.isPaused == true ||
                    state?.isCompleted == true
            }
            else -> false
        }

        /** Pure service action resolver (JVM-testable). */
        fun resolveServiceAction(path: String, data: ByteArray): ServiceAction {
            if (path != WearableProtocol.PATH_WORKOUT_STATE) return ServiceAction.NONE
            val state = WorkoutStateMessage.fromByteArray(data) ?: return ServiceAction.NONE
            return when {
                state.isRunning || state.isPaused -> ServiceAction.START
                state.isIdle || state.isCompleted -> ServiceAction.STOP
                else -> ServiceAction.NONE
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        val app = application as? WearBikeApplication

        // 1. Play haptics directly even if MainActivity is not in foreground
        if (messageEvent.path == WearableProtocol.PATH_HAPTIC_TRIGGER) {
            val alert = HapticAlertType.fromByteArray(messageEvent.data)
            if (alert != null) {
                app?.hapticManager?.playAlert(alert)
            }
        }

        // 2. Immediately forward incoming workout state to WearMessageManager
        if (messageEvent.path == WearableProtocol.PATH_WORKOUT_STATE) {
            val state = WorkoutStateMessage.fromByteArray(messageEvent.data)
            if (state != null) {
                app?.messageManager?.updateWorkoutState(state)
            }
        }

        // 3. Start or stop foreground service on workout state transitions
        when (resolveServiceAction(messageEvent.path, messageEvent.data)) {
            ServiceAction.START -> WearWorkoutTrackingService.start(this)
            ServiceAction.STOP -> WearWorkoutTrackingService.stop(this)
            ServiceAction.NONE -> Unit
        }

        // 4. Wake UI only for attention-requiring events
        val shouldWake = shouldWakeForMessage(messageEvent.path, messageEvent.data)
        if (!shouldWake) return

        Log.d(TAG, "Waking watch UI for path: ${messageEvent.path}")
        val intent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start MainActivity from listener: ${e.message}")
        }
    }
}

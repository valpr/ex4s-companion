package com.valpr.bikecompanion.wear.messaging

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
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
 * second. Only wake when user attention is required (workout start / bailout / resume /
 * haptics / pause / completion). Active telemetry is received by [WearMessageManager]
 * while the app is already open.
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
        fun shouldWakeForMessage(
            path: String,
            data: ByteArray,
            previousState: WorkoutStateMessage? = null
        ): Boolean = when (path) {
            WearableProtocol.PATH_HAPTIC_TRIGGER -> true
            WearableProtocol.PATH_WORKOUT_STATE -> {
                val state = WorkoutStateMessage.fromByteArray(data) ?: return false
                val isWorkoutStart = state.isRunning &&
                    (previousState?.isIdle == true || (previousState == null && state.elapsedSeconds == 0))
                val isWorkoutResume = state.isRunning && previousState?.isPaused == true
                isWorkoutStart ||
                    isWorkoutResume ||
                    state.isBailoutActive ||
                    state.isCadenceFloorActive ||
                    state.isPaused ||
                    state.isCompleted
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

        /**
         * Pure edge-trigger for bailout alert notifications (JVM-testable).
         * Returns true only on entry into bailout, not on every 1Hz telemetry
         * tick while already in bailout — prevents NotificationManager spam.
         */
        fun shouldPostBailoutAlert(state: WorkoutStateMessage, previous: WorkoutStateMessage?): Boolean {
            val nowActive = state.isBailoutActive || state.isCadenceFloorActive
            if (!nowActive) return false
            val wasActive = previous?.isBailoutActive == true || previous?.isCadenceFloorActive == true
            return !wasActive
        }

        /**
         * Pure edge-trigger for completion notification/haptic (JVM-testable).
         * Returns true only on entry into COMPLETED, not on repeated
         * COMPLETED broadcasts.
         */
        fun shouldPostCompletion(state: WorkoutStateMessage, previous: WorkoutStateMessage?): Boolean = state.isCompleted && previous?.isCompleted != true
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        val app = application as? WearBikeApplication
        val previousState = app?.messageManager?.workoutState?.value

        // 0. Forward ping-pong test messages to WearMessageManager and return early
        if (messageEvent.path == WearableProtocol.PATH_PING || messageEvent.path == WearableProtocol.PATH_PONG) {
            if (messageEvent.sourceNodeId.isNotBlank()) {
                app?.messageManager?.updatePhoneNode(messageEvent.sourceNodeId)
            }
            app?.messageManager?.onMessageReceived(messageEvent)
            return
        }

        // Maintain live link to phone so watch can reply with HR telemetry and gestures
        if (messageEvent.sourceNodeId.isNotBlank()) {
            app?.messageManager?.updatePhoneNode(messageEvent.sourceNodeId)
        }

        // 1. Play haptics directly even if MainActivity is not in foreground.
        // Haptic path is haptic-only: notifications are owned by the state path
        // below, which carries authoritative workoutName/elapsedSeconds.
        if (messageEvent.path == WearableProtocol.PATH_HAPTIC_TRIGGER) {
            val alert = HapticAlertType.fromByteArray(messageEvent.data)
            if (alert != null) {
                app?.hapticManager?.playAlert(alert)
                if (alert == HapticAlertType.RESUME_TRIGGERED) {
                    com.valpr.bikecompanion.wear.service.WearNotificationHelper.cancelBailoutNotification(this)
                }
            }
        }

        // 2. Immediately forward incoming workout state to WearMessageManager.
        // Notifications are edge-triggered on entry transitions only, so steady
        // 1Hz telemetry does not spam NotificationManager.
        if (messageEvent.path == WearableProtocol.PATH_WORKOUT_STATE) {
            val state = WorkoutStateMessage.fromByteArray(messageEvent.data)
            if (state != null) {
                app?.messageManager?.updateWorkoutState(state)
                if (shouldPostCompletion(state, previousState)) {
                    com.valpr.bikecompanion.wear.service.WearNotificationHelper.cancelBailoutNotification(this)
                    com.valpr.bikecompanion.wear.service.WearNotificationHelper.postWorkoutCompletedNotification(
                        context = this,
                        workoutName = state.workoutName,
                        elapsedSeconds = state.elapsedSeconds
                    )
                    app?.hapticManager?.playAlert(HapticAlertType.WORKOUT_COMPLETED)
                } else if (shouldPostBailoutAlert(state, previousState)) {
                    com.valpr.bikecompanion.wear.service.WearNotificationHelper.postBailoutNotification(
                        context = this,
                        isCadenceFloor = state.isCadenceFloorActive
                    )
                } else if (state.isRunning || state.isPaused) {
                    com.valpr.bikecompanion.wear.service.WearNotificationHelper.cancelBailoutNotification(this)
                    com.valpr.bikecompanion.wear.service.WearNotificationHelper.cancelWorkoutCompletedNotification(this)
                }
            }
        }

        // 3. Start or stop foreground service on workout state transitions
        when (resolveServiceAction(messageEvent.path, messageEvent.data)) {
            ServiceAction.START -> WearWorkoutTrackingService.start(this)
            ServiceAction.STOP -> WearWorkoutTrackingService.stop(this)
            ServiceAction.NONE -> Unit
        }

        // 4. Wake UI only for attention-requiring events
        val shouldWake = shouldWakeForMessage(messageEvent.path, messageEvent.data, previousState)
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

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Log.d(TAG, "onPeerConnected: ${peer.displayName} (${peer.id})")
        val app = application as? WearBikeApplication
        app?.messageManager?.updatePhoneNode(peer.id, peer.displayName)
    }

    override fun onPeerDisconnected(peer: Node) {
        super.onPeerDisconnected(peer)
        Log.d(TAG, "onPeerDisconnected: ${peer.displayName} (${peer.id})")
        val app = application as? WearBikeApplication
        app?.messageManager?.refreshConnectedPhone()
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        Log.d(TAG, "onCapabilityChanged: ${capabilityInfo.name}")
        val app = application as? WearBikeApplication
        app?.messageManager?.refreshConnectedPhone()
    }
}

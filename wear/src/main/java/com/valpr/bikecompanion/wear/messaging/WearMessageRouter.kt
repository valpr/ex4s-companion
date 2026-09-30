package com.valpr.bikecompanion.wear.messaging

import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.shared.WorkoutStateMessage

/**
 * Pure inbound-message router for the watch (framework-free, plain-JUnit testable).
 *
 * [WearMessageManager.onMessageReceived] delegates here; this object decides
 * WHAT to do without touching Android, Play Services, or haptics. Corrupt
 * payloads and unknown paths map to [Action.Ignore] (silent-drop by design,
 * never crash on the safety path).
 */
object WearMessageRouter {
    sealed interface Action {
        data class UpdateState(val state: WorkoutStateMessage) : Action

        data class PlayHaptic(val alert: HapticAlertType) : Action

        data object Ignore : Action
    }

    fun route(path: String, data: ByteArray): Action = when (path) {
        WearableProtocol.PATH_WORKOUT_STATE -> {
            val state = WorkoutStateMessage.fromByteArray(data)
            if (state != null) Action.UpdateState(state) else Action.Ignore
        }
        WearableProtocol.PATH_HAPTIC_TRIGGER -> {
            val alert = HapticAlertType.fromByteArray(data)
            if (alert != null) Action.PlayHaptic(alert) else Action.Ignore
        }
        else -> Action.Ignore
    }

    /** Pure send-guard: all three watch→phone send paths early-return on null/blank node. */
    fun canSend(nodeId: String?): Boolean = !nodeId.isNullOrBlank()

    /**
     * Pure node→state mapping for [WearMessageManager.refreshConnectedPhone].
     * First node wins; empty list maps to disconnected.
     */
    data class PhoneNode(val id: String, val displayName: String)

    data class PhoneLink(val isConnected: Boolean, val nodeId: String?)

    fun resolvePhoneLink(nodes: List<PhoneNode>): PhoneLink {
        val phone = nodes.firstOrNull()
        return if (phone != null) PhoneLink(true, phone.id) else PhoneLink(false, null)
    }
}

package com.valpr.bikecompanion.wear.messaging

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.shared.HeartRateBatch
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.haptics.WatchHapticManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Wearable Data Layer communications from the Pixel Watch to the phone.
 * Sends batched HR telemetry, instant rotary crown bailout, and resume slap events.
 * Receives live workout state updates and haptic alert commands.
 */
class WearMessageManager(
    private val context: Context,
    private val hapticManager: WatchHapticManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    messageClientOverride: MessageClient? = null,
    nodeClientOverride: NodeClient? = null,
    capabilityClientOverride: CapabilityClient? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "WearMessageManager"
        private const val REQUEST_STATE_DEBOUNCE_MS = 5000L
    }

    private val messageClient: MessageClient by lazy {
        messageClientOverride ?: Wearable.getMessageClient(context)
    }
    private val nodeClient: NodeClient by lazy {
        nodeClientOverride ?: Wearable.getNodeClient(context)
    }
    private val capabilityClient: CapabilityClient by lazy {
        capabilityClientOverride ?: Wearable.getCapabilityClient(context)
    }

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    private val _phoneNodeId = MutableStateFlow<String?>(null)

    private val _workoutState = MutableStateFlow<WorkoutStateMessage?>(null)
    val workoutState: StateFlow<WorkoutStateMessage?> = _workoutState.asStateFlow()

    private var lastRequestStateMs = 0L

    init {
        try {
            messageClient.addListener(this)
            capabilityClient.addListener(this, WearableProtocol.CAPABILITY_PHONE_APP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Wearable message listener: ${e.message}", e)
        }
        refreshConnectedPhone()
    }

    /**
     * Updates paired phone node ID and triggers workout state query if needed.
     */
    fun updatePhoneNode(nodeId: String, displayName: String = "Phone") {
        if (nodeId.isNotBlank()) {
            val wasConnected = _isPhoneConnected.value
            val prevNodeId = _phoneNodeId.value
            _phoneNodeId.value = nodeId
            _isPhoneConnected.value = true
            Log.d(TAG, "Phone node updated: $displayName ($nodeId)")
            if (!wasConnected || prevNodeId != nodeId || _workoutState.value == null) {
                requestWorkoutState()
            }
        }
    }

    /**
     * Requests active workout state from the connected phone.
     */
    fun requestWorkoutState() {
        val now = clock()
        if (now - lastRequestStateMs < REQUEST_STATE_DEBOUNCE_MS) return

        val target = _phoneNodeId.value
        if (!WearMessageRouter.canSend(target)) return
        val nodeId = target!!
        lastRequestStateMs = now
        scope.launch {
            try {
                Tasks.await(
                    messageClient.sendMessage(
                        nodeId,
                        WearableProtocol.PATH_REQUEST_STATE,
                        byteArrayOf(0x01)
                    )
                )
                Log.d(TAG, "Dispatched requestWorkoutState to phone ($nodeId)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send requestWorkoutState to phone: ${e.message}")
            }
        }
    }

    fun refreshConnectedPhone() {
        scope.launch {
            try {
                val capabilityInfo = Tasks.await(
                    capabilityClient.getCapability(
                        WearableProtocol.CAPABILITY_PHONE_APP,
                        CapabilityClient.FILTER_REACHABLE
                    )
                )
                val reachablePhone = capabilityInfo.nodes.firstOrNull()
                if (reachablePhone != null) {
                    updatePhoneNode(reachablePhone.id, reachablePhone.displayName)
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Phone capability query failed, falling back to connectedNodes: ${e.message}")
            }

            try {
                val nodes: List<Node> = Tasks.await(nodeClient.connectedNodes)
                val phone = nodes.firstOrNull()
                if (phone != null) {
                    updatePhoneNode(phone.id, phone.displayName)
                } else {
                    _phoneNodeId.value = null
                    _isPhoneConnected.value = false
                    Log.d(TAG, "No connected phone node found.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying connected phone: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.sourceNodeId.isNotBlank() &&
            (!_isPhoneConnected.value || _phoneNodeId.value.isNullOrBlank())
        ) {
            updatePhoneNode(messageEvent.sourceNodeId)
        }

        when (val action = WearMessageRouter.route(messageEvent.path, messageEvent.data)) {
            is WearMessageRouter.Action.UpdateState -> {
                updateWorkoutState(action.state)
            }
            is WearMessageRouter.Action.PlayHaptic -> {
                Log.i(TAG, "Received haptic trigger from phone: ${action.alert}")
                hapticManager.playAlert(action.alert)
            }
            WearMessageRouter.Action.Ignore -> {
                Log.d(TAG, "Unhandled message path: ${messageEvent.path}")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val reachablePhone = capabilityInfo.nodes.firstOrNull()
        if (reachablePhone != null) {
            updatePhoneNode(reachablePhone.id, reachablePhone.displayName)
        } else {
            refreshConnectedPhone()
        }
    }

    /**
     * Updates the active workout state directly.
     * Can be invoked from [WearMessageListenerService] to guarantee immediate
     * state synchronization upon background wakeup.
     */
    fun updateWorkoutState(state: WorkoutStateMessage) {
        _workoutState.value = state
        Log.d(
            TAG,
            "Updated workout state: status=${state.sessionStatus}, target=${state.targetWatts}W, hr=${state.heartRateBpm}"
        )
    }

    /**
     * Transmits batched heart rate telemetry to phone.
     */
    fun sendHeartRateBatch(batch: HeartRateBatch) {
        val target = _phoneNodeId.value
        if (!WearMessageRouter.canSend(target)) return
        val nodeId = target!!
        scope.launch {
            try {
                Tasks.await(
                    messageClient.sendMessage(
                        nodeId,
                        WearableProtocol.PATH_HEART_RATE,
                        batch.toByteArray()
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send HR batch to phone: ${e.message}")
            }
        }
    }

    /**
     * Transmits instant Rotary Crown Bailout gesture to phone.
     */
    fun sendRotaryBailout() {
        val target = _phoneNodeId.value
        if (!WearMessageRouter.canSend(target)) return
        val nodeId = target!!
        hapticManager.playAlert(HapticAlertType.BAILOUT_TRIGGERED)
        com.valpr.bikecompanion.wear.service.WearNotificationHelper.postBailoutNotification(
            context,
            isCadenceFloor = false
        )
        scope.launch {
            try {
                Tasks.await(
                    messageClient.sendMessage(
                        nodeId,
                        WearableProtocol.PATH_ROTARY_BAILOUT,
                        byteArrayOf(0x01)
                    )
                )
                Log.i(TAG, "Dispatched Rotary Crown Bailout to phone")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending bailout to phone: ${e.message}")
            }
        }
    }

    /**
     * Transmits instant "Resume Slap" tap gesture to phone.
     */
    fun sendResumeSlap() {
        val target = _phoneNodeId.value
        if (!WearMessageRouter.canSend(target)) return
        val nodeId = target!!
        hapticManager.playAlert(HapticAlertType.RESUME_TRIGGERED)
        com.valpr.bikecompanion.wear.service.WearNotificationHelper.cancelBailoutNotification(context)
        scope.launch {
            try {
                Tasks.await(
                    messageClient.sendMessage(
                        nodeId,
                        WearableProtocol.PATH_RESUME_SLAP,
                        byteArrayOf(0x01)
                    )
                )
                Log.i(TAG, "Dispatched Resume Slap to phone")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending resume slap to phone: ${e.message}")
            }
        }
    }

    fun onDestroy() {
        try {
            messageClient.removeListener(this)
            capabilityClient.removeListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering Wearable listeners: ${e.message}")
        }
        scope.cancel()
    }
}

package com.valpr.bikecompanion.wearable

import android.content.Context
import android.util.Log
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Live connection state for the paired Wear OS (Pixel Watch) device.
 */
data class WearableWatchState(
    val isConnected: Boolean = false,
    val nodeName: String = "",
    val nodeId: String = "",
    val lastHeartRateBpm: Int = 0,
    val lastHeartRateTimestampMs: Long = 0L
)

/**
 * Manages communication between the phone and Pixel Watch via Google Play Services Wearable.
 * Handles incoming batched HR telemetry, rotary crown bailout gestures, and resume slap taps.
 * Synchronizes workout state and haptic alerts to the watch.
 */
class PhoneWearableManager(
    private val context: Context,
    private val sessionManager: WorkoutSessionManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    messageClientOverride: MessageClient? = null,
    nodeClientOverride: NodeClient? = null,
    capabilityClientOverride: CapabilityClient? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneWearableManager"
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

    private val _watchState = MutableStateFlow(WearableWatchState())
    val watchState: StateFlow<WearableWatchState> = _watchState.asStateFlow()

    private var stateSyncJob: Job? = null
    private var hapticJob: Job? = null

    init {
        try {
            messageClient.addListener(this)
            capabilityClient.addListener(this, WearableProtocol.CAPABILITY_WEAR_APP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Wearable listeners: ${e.message}", e)
        }

        refreshConnectedNodes()
        startWorkoutStateSync()
        startHapticAlertSync()
    }

    /**
     * Queries connected Wear OS nodes and updates live watch connection state.
     */
    fun refreshConnectedNodes() {
        scope.launch {
            try {
                val nodes: List<Node> = Tasks.await(nodeClient.connectedNodes)
                val primaryNode = nodes.firstOrNull()

                if (primaryNode != null) {
                    _watchState.update {
                        it.copy(
                            isConnected = true,
                            nodeName = primaryNode.displayName.ifBlank { "Pixel Watch" },
                            nodeId = primaryNode.id
                        )
                    }
                    Log.d(TAG, "Connected to watch node: ${primaryNode.displayName} (${primaryNode.id})")
                } else {
                    _watchState.update {
                        it.copy(
                            isConnected = false,
                            nodeName = "",
                            nodeId = ""
                        )
                    }
                    Log.d(TAG, "No connected watch nodes found.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error refreshing connected nodes: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (val action = PhoneWearableRouter.route(messageEvent.path, messageEvent.data)) {
            is PhoneWearableRouter.Action.ForwardHeartRate -> {
                _watchState.update {
                    it.copy(
                        lastHeartRateBpm = action.bpm,
                        lastHeartRateTimestampMs = action.timestampMs
                    )
                }
                // Forward to WorkoutSessionManager for dynamic capping and UI
                sessionManager.updateHeartRate(action.bpm)
            }

            PhoneWearableRouter.Action.Clutch -> {
                Log.i(TAG, "Received Rotary Crown Bailout command from watch")
                sessionManager.toggleClutch()
            }

            PhoneWearableRouter.Action.Resume -> {
                Log.i(TAG, "Received Resume Slap tap command from watch")
                sessionManager.resumeManually()
            }

            PhoneWearableRouter.Action.Ignore -> {
                Log.d(TAG, "Received unhandled wearable message: ${messageEvent.path}")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: com.google.android.gms.wearable.CapabilityInfo) {
        refreshConnectedNodes()
    }

    /**
     * Continuously syncs live workout state to the watch, throttled to ~0.5Hz for
     * steady telemetry with immediate dispatch on attention-requiring transitions
     * (status / bailout / cadence-floor / HR-cap / target changes).
     */
    private fun startWorkoutStateSync() {
        stateSyncJob?.cancel()
        stateSyncJob = scope.launch {
            var lastSentMs = 0L
            var lastKeys: WearSyncKeys? = null
            sessionManager.sessionState.collect { session ->
                val targetNodeId = _watchState.value.nodeId

                val statusCode = when (session.status) {
                    SessionStatus.IDLE -> WorkoutStateMessage.STATUS_IDLE
                    SessionStatus.RUNNING -> WorkoutStateMessage.STATUS_RUNNING
                    SessionStatus.PAUSED -> WorkoutStateMessage.STATUS_PAUSED
                    SessionStatus.COMPLETED -> WorkoutStateMessage.STATUS_COMPLETED
                }

                val ergState = session.ergDecision?.state
                val isBailout = ergState == ErgState.MANUAL_BAILOUT
                val isCadenceFloor = ergState == ErgState.CADENCE_FLOOR_BAILOUT
                val isHrCapped = session.ergDecision?.isHrCapped == true || session.isCriticalHrActive
                val targetWatts = session.targetWatts ?: -1

                val now = clock()
                val current = WearSyncKeys(
                    statusCode = statusCode,
                    isBailout = isBailout,
                    isCadenceFloor = isCadenceFloor,
                    isHrCapped = isHrCapped,
                    targetWatts = targetWatts,
                    athleteMaxHr = session.athleteMaxHr
                )
                if (!WearSyncDecision.shouldSync(now, lastSentMs, current, lastKeys, targetNodeId.isBlank())) {
                    return@collect
                }

                val message = WorkoutStateMessage(
                    sessionStatus = statusCode,
                    elapsedSeconds = session.elapsedSeconds,
                    targetWatts = targetWatts,
                    currentWatts = session.latestTelemetry.estimatedWatts,
                    cadenceRpm = session.latestTelemetry.cadenceRpm,
                    heartRateBpm = session.currentHeartRate,
                    isBailoutActive = isBailout,
                    isCadenceFloorActive = isCadenceFloor,
                    isHrCapped = isHrCapped,
                    workoutName = session.workout?.name ?: if (session.isFreeRide && session.status != SessionStatus.IDLE) "Free Ride" else "",
                    athleteMaxHr = session.athleteMaxHr
                )

                try {
                    Tasks.await(
                        messageClient.sendMessage(
                            targetNodeId,
                            WearableProtocol.PATH_WORKOUT_STATE,
                            message.toByteArray()
                        )
                    )
                    lastSentMs = now
                    lastKeys = current
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send workout state to watch: ${e.message}")
                }
            }
        }
    }

    /**
     * Dispatches haptic alerts to the watch when critical HR or bailouts occur.
     */
    private fun startHapticAlertSync() {
        hapticJob?.cancel()
        hapticJob = scope.launch {
            sessionManager.hapticAlerts.collect { alert ->
                val targetNodeId = _watchState.value.nodeId
                if (targetNodeId.isBlank()) return@collect

                try {
                    Tasks.await(
                        messageClient.sendMessage(
                            targetNodeId,
                            WearableProtocol.PATH_HAPTIC_TRIGGER,
                            alert.toByteArray()
                        )
                    )
                    Log.d(TAG, "Dispatched haptic alert $alert to watch $targetNodeId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send haptic alert: ${e.message}")
                }
            }
        }
    }

    fun onDestroy() {
        try {
            messageClient.removeListener(this)
            capabilityClient.removeListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing wearable listeners: ${e.message}")
        }
        stateSyncJob?.cancel()
        hapticJob?.cancel()
        scope.cancel()
    }
}

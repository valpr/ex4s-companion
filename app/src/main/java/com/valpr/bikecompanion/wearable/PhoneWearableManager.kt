package com.valpr.bikecompanion.wearable

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.WorkoutSessionManager
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
 *
 * Freshness invariant: [lastHeartRateBpm] is the last value received and never
 * decays on its own — callers must consult [lastHeartRateTimestampMs] via
 * [PhoneWearableManager.resolveWatchHrStatus] before presenting it as live.
 */
data class WearableWatchState(
    val isConnected: Boolean = false,
    val nodeName: String = "",
    val nodeId: String = "",
    val lastHeartRateBpm: Int = 0,
    /** Phone receipt time (see [PhoneWearableManager.clock]) of the last HR batch. */
    val lastHeartRateTimestampMs: Long = 0L
)

/**
 * Freshness of watch heart-rate data for UI presentation.
 */
enum class WatchHrStatus {
    /** Node reachable and HR batch received within [PhoneWearableManager.HR_STALE_THRESHOLD_MS]. */
    LIVE,

    /** Node reachable but no HR batch within the threshold — value on screen is frozen. */
    STALE,

    /** Node reachable but no HR batch ever received in this session. */
    NO_DATA,

    /** No reachable watch node. */
    DISCONNECTED
}

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
) : MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneWearableManager"
        private const val NODE_REFRESH_INTERVAL_MS = 15_000L

        /**
         * HR is LIVE while a batch arrived within this window. Grounded in the
         * watch batch cadence (2–3s active, 5–10s ambient): 12s tolerates an
         * ambient batch plus radio jitter without crying stale.
         */
        const val HR_STALE_THRESHOLD_MS = 12_000L

        /**
         * Pure freshness resolver (JVM-testable): node reachability alone never
         * implies live data — a reachable watch with a frozen [WearableWatchState]
         * must present STALE, never LIVE.
         */
        fun resolveWatchHrStatus(state: WearableWatchState, nowMs: Long): WatchHrStatus {
            if (!state.isConnected) return WatchHrStatus.DISCONNECTED
            if (state.lastHeartRateTimestampMs <= 0L || state.lastHeartRateBpm <= 0) {
                return WatchHrStatus.NO_DATA
            }
            return if (nowMs - state.lastHeartRateTimestampMs <= HR_STALE_THRESHOLD_MS) {
                WatchHrStatus.LIVE
            } else {
                WatchHrStatus.STALE
            }
        }
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
    private var lastNodeRefreshMs = 0L

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
     * Updates active watch node state, auto-syncing current workout if newly connected.
     */
    fun updateWatchNode(node: Node) {
        val wasConnected = _watchState.value.isConnected
        val prevNodeId = _watchState.value.nodeId
        _watchState.update {
            it.copy(
                isConnected = true,
                nodeName = node.displayName.ifBlank { "Pixel Watch" },
                nodeId = node.id
            )
        }
        Log.d(TAG, "Watch node updated: ${node.displayName} (${node.id})")
        if ((!wasConnected || prevNodeId != node.id) &&
            sessionManager.sessionState.value.status != SessionStatus.IDLE
        ) {
            sendCurrentWorkoutState(node.id)
        }
    }

    /**
     * Queries connected Wear OS nodes and updates live watch connection state.
     * Prefers nodes with CAPABILITY_WEAR_APP; falls back to connectedNodes.
     */
    fun refreshConnectedNodes() {
        scope.launch {
            try {
                val capabilityInfo = Tasks.await(
                    capabilityClient.getCapability(
                        WearableProtocol.CAPABILITY_WEAR_APP,
                        CapabilityClient.FILTER_REACHABLE
                    )
                )
                val reachableNode = capabilityInfo.nodes.firstOrNull()
                if (reachableNode != null) {
                    updateWatchNode(reachableNode)
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Capability query failed, falling back to connectedNodes: ${e.message}")
            }

            try {
                val nodes: List<Node> = Tasks.await(nodeClient.connectedNodes)
                val primaryNode = nodes.firstOrNull()

                if (primaryNode != null) {
                    updateWatchNode(primaryNode)
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

    fun onPeerConnected(peer: Node) {
        Log.i(TAG, "Watch peer connected: ${peer.displayName} (${peer.id})")
        updateWatchNode(peer)
    }

    fun onPeerDisconnected(peer: Node) {
        Log.i(TAG, "Watch peer disconnected: ${peer.displayName} (${peer.id})")
        if (_watchState.value.nodeId == peer.id) {
            _watchState.update { it.copy(isConnected = false) }
        }
        refreshConnectedNodes()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val sourceNodeId = messageEvent.sourceNodeId
        if (sourceNodeId.isNotBlank() &&
            (!_watchState.value.isConnected || _watchState.value.nodeId.isBlank())
        ) {
            _watchState.update {
                it.copy(
                    isConnected = true,
                    nodeId = sourceNodeId,
                    nodeName = it.nodeName.ifBlank { "Pixel Watch" }
                )
            }
        }

        when (val action = PhoneWearableRouter.route(messageEvent.path, messageEvent.data)) {
            is PhoneWearableRouter.Action.ForwardHeartRate -> {
                // Stamp phone receipt time (clock, not the watch batch timestamp)
                // so freshness is immune to watch/phone clock skew.
                _watchState.update {
                    it.copy(
                        lastHeartRateBpm = action.bpm,
                        lastHeartRateTimestampMs = clock()
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
                // Session pause and ERG bailout are orthogonal axes: a slap
                // while PAUSED unfreezes the playhead, and independently clears
                // any ERG bailout. Either or both may apply.
                if (sessionManager.sessionState.value.status == SessionStatus.PAUSED) {
                    sessionManager.resumeWorkout()
                }
                sessionManager.resumeManually()
            }

            PhoneWearableRouter.Action.Pause -> {
                Log.i(TAG, "Received Pause command from watch")
                sessionManager.pauseWorkout()
            }

            PhoneWearableRouter.Action.RequestWorkoutState -> {
                Log.i(TAG, "Received RequestWorkoutState from watch ($sourceNodeId)")
                sendCurrentWorkoutState(sourceNodeId.ifBlank { null })
            }

            PhoneWearableRouter.Action.Ignore -> {
                Log.d(TAG, "Received unhandled wearable message: ${messageEvent.path}")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: com.google.android.gms.wearable.CapabilityInfo) {
        val reachableNode = capabilityInfo.nodes.firstOrNull()
        if (reachableNode != null) {
            updateWatchNode(reachableNode)
        } else {
            refreshConnectedNodes()
        }
    }

    /**
     * Immediately dispatches the current workout state to the specified watch node or active node.
     */
    fun sendCurrentWorkoutState(targetNodeIdOverride: String? = null) {
        val targetNodeId = targetNodeIdOverride ?: _watchState.value.nodeId
        if (targetNodeId.isBlank()) return

        scope.launch {
            val session = sessionManager.sessionState.value
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
                workoutName = session.workout?.name
                    ?: if (session.isFreeRide && session.status != SessionStatus.IDLE) "Free Ride" else "",
                athleteMaxHr = session.athleteMaxHr,
                athleteRestingHr = session.athleteRestingHr,
                useKarvonenZones = session.useKarvonenZones
            )

            try {
                Tasks.await(
                    messageClient.sendMessage(
                        targetNodeId,
                        WearableProtocol.PATH_WORKOUT_STATE,
                        message.toByteArray()
                    )
                )
                Log.d(TAG, "Dispatched workout state to watch ($targetNodeId): status=$statusCode, name=${message.workoutName}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send workout state to watch: ${e.message}")
            }
        }
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
                    athleteMaxHr = session.athleteMaxHr,
                    athleteRestingHr = session.athleteRestingHr,
                    useKarvonenZones = session.useKarvonenZones
                )
                if (targetNodeId.isBlank() && session.status != SessionStatus.IDLE) {
                    if (now - lastNodeRefreshMs >= NODE_REFRESH_INTERVAL_MS) {
                        lastNodeRefreshMs = now
                        refreshConnectedNodes()
                    }
                }

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
                    workoutName =
                    session.workout?.name
                        ?: if (session.isFreeRide && session.status != SessionStatus.IDLE) "Free Ride" else "",
                    athleteMaxHr = session.athleteMaxHr,
                    athleteRestingHr = session.athleteRestingHr,
                    useKarvonenZones = session.useKarvonenZones
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

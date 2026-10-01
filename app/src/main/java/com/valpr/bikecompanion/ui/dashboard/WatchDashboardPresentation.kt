package com.valpr.bikecompanion.ui.dashboard

import com.valpr.bikecompanion.wearable.PhoneWearableManager
import com.valpr.bikecompanion.wearable.WatchHrStatus
import com.valpr.bikecompanion.wearable.WearableWatchState
import com.valpr.bikecompanion.workout.SessionStatus

/**
 * Visual tone for rendering watch state on the Dashboard.
 */
enum class WatchStatusTone {
    MUTED,
    READY,
    LIVE,
    WARNING,
    ACQUIRING
}

/**
 * Pure presentation model for the watch status card on the Dashboard.
 * Framework-free for plain-JUnit testability (AGENTS.md §8).
 */
data class WatchDashboardUiModel(
    val title: String,
    val statusText: String,
    val tone: WatchStatusTone,
    val needsTicker: Boolean,
    val isConnected: Boolean
)

/**
 * Pure status resolver for the Dashboard watch card.
 *
 * Invariant (AGENTS.md §5): Watch HR sensing is intentionally gated on active workouts
 * (RUNNING / PAUSED). While in IDLE or COMPLETED, the watch optical sensor is off, so
 * the UI presents standby readiness ("Ready • Starts on ride") and suppresses stale HR
 * warnings or pending sync indicators.
 */
object WatchDashboardPresentation {

    fun resolve(
        watchState: WearableWatchState,
        sessionStatus: SessionStatus,
        nowMs: Long
    ): WatchDashboardUiModel {
        if (!watchState.isConnected) {
            return WatchDashboardUiModel(
                title = "Pixel Watch",
                statusText = "Waiting for Watch",
                tone = WatchStatusTone.MUTED,
                needsTicker = false,
                isConnected = false
            )
        }

        val title = watchState.nodeName.ifBlank { "Pixel Watch" }

        if (watchState.isPinging) {
            return WatchDashboardUiModel(
                title = title,
                statusText = "Pinging watch…",
                tone = WatchStatusTone.ACQUIRING,
                needsTicker = false,
                isConnected = true
            )
        }

        if (!watchState.isAppInstalled) {
            return WatchDashboardUiModel(
                title = title,
                statusText = watchState.pingStatusMessage ?: "Watch Paired • App Missing",
                tone = WatchStatusTone.WARNING,
                needsTicker = false,
                isConnected = true
            )
        }

        // Standby: optical sensor is dormant while IDLE or COMPLETED.
        if (sessionStatus == SessionStatus.IDLE || sessionStatus == SessionStatus.COMPLETED) {
            val statusText = watchState.pingStatusMessage ?: "Ready • Standby"
            val tone = if (watchState.pingStatusMessage?.startsWith("Verified") == true) {
                WatchStatusTone.LIVE
            } else if (watchState.pingStatusMessage != null) {
                WatchStatusTone.WARNING
            } else {
                WatchStatusTone.READY
            }
            return WatchDashboardUiModel(
                title = title,
                statusText = statusText,
                tone = tone,
                needsTicker = false,
                isConnected = true
            )
        }

        // Active session: HR sensing is active on the watch; evaluate live freshness.
        val hrStatus = PhoneWearableManager.resolveWatchHrStatus(watchState, nowMs)
        return when (hrStatus) {
            WatchHrStatus.LIVE -> WatchDashboardUiModel(
                title = title,
                statusText = "Live HR: ${watchState.lastHeartRateBpm} BPM",
                tone = WatchStatusTone.LIVE,
                needsTicker = true,
                isConnected = true
            )
            WatchHrStatus.STALE -> {
                val ageSec = ((nowMs - watchState.lastHeartRateTimestampMs) / 1000L).coerceAtLeast(0L)
                WatchDashboardUiModel(
                    title = title,
                    statusText = "HR stale • ${ageSec}s ago",
                    tone = WatchStatusTone.WARNING,
                    needsTicker = true,
                    isConnected = true
                )
            }
            WatchHrStatus.NO_DATA -> WatchDashboardUiModel(
                title = title,
                statusText = "Acquiring HR…",
                tone = WatchStatusTone.ACQUIRING,
                needsTicker = false,
                isConnected = true
            )
            WatchHrStatus.DISCONNECTED -> WatchDashboardUiModel(
                title = "Pixel Watch",
                statusText = "Waiting for Watch",
                tone = WatchStatusTone.MUTED,
                needsTicker = false,
                isConnected = false
            )
        }
    }
}

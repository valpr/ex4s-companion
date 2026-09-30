package com.valpr.bikecompanion.wear.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.R
import com.valpr.bikecompanion.wear.WearBikeApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service managing active heart rate sensing and ongoing notification
 * with [androidx.wear.ongoing.OngoingActivity] integration on Wear OS.
 *
 * Keeps the watch process and co-processor [HealthServicesManager] tracking alive
 * when the user navigates away from [com.valpr.bikecompanion.wear.MainActivity]
 * during an active workout.
 */
class WearWorkoutTrackingService : LifecycleService() {

    companion object {
        private const val TAG = "WearWorkoutService"

        const val ACTION_START = "ACTION_START_WEAR_WORKOUT"
        const val ACTION_STOP = "ACTION_STOP_WEAR_WORKOUT"

        /**
         * Pure terminal-state resolver (JVM-testable):
         * Returns true only when a workout has explicitly transitioned to IDLE or COMPLETED.
         * Null state represents uninitialized state (waiting for first phone broadcast)
         * and must NOT stop the tracking service.
         */
        fun shouldStopTracking(state: WorkoutStateMessage?): Boolean {
            if (state == null) return false
            return state.isIdle || state.isCompleted
        }

        fun start(context: Context) {
            val intent = Intent(context, WearWorkoutTrackingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WearWorkoutTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        WearNotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithOngoing()
                startTracking()
                observeWorkoutState()
            }
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithOngoing() {
        val initialNotification = WearNotificationHelper.buildNotification(
            context = this,
            title = getString(R.string.wear_notification_title),
            content = WearNotificationHelper.formatContent(0, 0)
        )

        val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                WearNotificationHelper.NOTIFICATION_ID,
                initialNotification,
                serviceTypes
            )
            Log.i(TAG, "WearWorkoutTrackingService started in foreground with HEALTH type")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for health foreground service: ${e.message}", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            stopSelf()
        }
    }

    private fun startTracking() {
        val app = application as? WearBikeApplication ?: return
        app.healthServicesManager.startHeartRateTracking()
    }

    private fun stopTracking() {
        observeJob?.cancel()
        observeJob = null
        val app = application as? WearBikeApplication ?: return
        app.healthServicesManager.stopHeartRateTracking()
    }

    private fun observeWorkoutState() {
        // Idempotency guard per AGENTS.md §3
        if (observeJob?.isActive == true) return

        val app = application as? WearBikeApplication ?: return
        val messageManager = app.messageManager
        val healthManager = app.healthServicesManager
        val notificationManager = getSystemService(NotificationManager::class.java)

        observeJob = lifecycleScope.launch {
            combine(
                messageManager.workoutState,
                healthManager.currentHeartRate
            ) { state, liveHr ->
                Pair(state, liveHr)
            }.collectLatest { (state, liveHr) ->
                if (state == null) {
                    if (liveHr > 0) {
                        val initialNotification = WearNotificationHelper.buildNotification(
                            context = this@WearWorkoutTrackingService,
                            title = getString(R.string.wear_notification_title),
                            content = WearNotificationHelper.formatContent(0, liveHr)
                        )
                        notificationManager?.notify(WearNotificationHelper.NOTIFICATION_ID, initialNotification)
                    }
                    return@collectLatest
                }

                if (shouldStopTracking(state)) {
                    Log.i(TAG, "Workout state became idle/completed, stopping service")
                    stopTracking()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }

                val title = if (state.workoutName.isNotBlank()) {
                    state.workoutName
                } else {
                    getString(R.string.wear_notification_title)
                }
                val hrToDisplay = if (liveHr > 0) liveHr else state.heartRateBpm
                val content = WearNotificationHelper.formatContent(state.elapsedSeconds, hrToDisplay)

                val updatedNotification = WearNotificationHelper.buildNotification(
                    context = this@WearWorkoutTrackingService,
                    title = title,
                    content = content
                )
                notificationManager?.notify(WearNotificationHelper.NOTIFICATION_ID, updatedNotification)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }
}

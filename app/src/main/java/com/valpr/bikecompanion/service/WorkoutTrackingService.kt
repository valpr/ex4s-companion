package com.valpr.bikecompanion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.MainActivity
import com.valpr.bikecompanion.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkoutTrackingService : LifecycleService() {

    companion object {
        const val ACTION_START = "ACTION_START_WORKOUT"
        const val ACTION_STOP = "ACTION_STOP_WORKOUT"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "workout_tracking_channel"

        fun startService(context: Context) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var telemetryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithTypes()
                observeTelemetry()
            }
            ACTION_STOP -> {
                telemetryJob?.cancel()
                telemetryJob = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithTypes() {
        val initialNotification = buildNotification("Connecting to bike...", "Live telemetry ready")

        val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                serviceTypes
            )
        } catch (e: SecurityException) {
            android.util.Log.e(
                "WorkoutTrackingService",
                "Missing required permission for foreground service: ${e.message}",
                e
            )
            stopSelf()
        } catch (e: Exception) {
            android.util.Log.e("WorkoutTrackingService", "Failed to start foreground service: ${e.message}", e)
            stopSelf()
        }
    }

    private fun observeTelemetry() {
        if (telemetryJob?.isActive == true) return

        val app = application as? BikeApplication ?: return
        val bleManager = app.bleManager
        val sessionManager = app.workoutSessionManager

        telemetryJob = lifecycleScope.launch {
            sessionManager.sessionState.collectLatest { session ->
                val connState = bleManager.connectionState.value

                val title = WorkoutNotificationContent.buildTitle(session, connState)
                val content = WorkoutNotificationContent.buildContent(session)

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
            }
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

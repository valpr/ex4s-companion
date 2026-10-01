package com.valpr.bikecompanion.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import com.valpr.bikecompanion.wear.MainActivity
import com.valpr.bikecompanion.wear.R

/**
 * Builds Wear OS notifications (ongoing tracking, bailout alerts, and completion notifications)
 * and binds the [OngoingActivity] watch-face indicator so users have a 1-tap shortcut back to [MainActivity].
 */
object WearNotificationHelper {
    private const val TAG = "WearNotificationHelper"

    const val CHANNEL_ID = "wear_workout_tracking"
    const val CHANNEL_ID_ALERTS = "wear_workout_alerts"

    const val NOTIFICATION_ID = 2001
    const val NOTIFICATION_ID_BAILOUT = 2002
    const val NOTIFICATION_ID_COMPLETED = 2003

    /**
     * Pure content formatter for ongoing workout tracking notification (JVM-testable without Android stubs).
     */
    fun formatContent(
        elapsedSeconds: Int,
        heartRateBpm: Int,
        isBailoutActive: Boolean = false,
        isCadenceFloorActive: Boolean = false
    ): String {
        val baseContent = formatBaseContent(elapsedSeconds, heartRateBpm)
        return when {
            isCadenceFloorActive -> "CADENCE BAILOUT • $baseContent"
            isBailoutActive -> "ERG SUSPENDED • $baseContent"
            else -> baseContent
        }
    }

    private fun formatBaseContent(elapsedSeconds: Int, heartRateBpm: Int): String {
        val timeStr =
            if (elapsedSeconds > 0) {
                "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
            } else {
                null
            }

        val hrStr =
            if (heartRateBpm > 0) {
                "$heartRateBpm BPM"
            } else {
                null
            }

        return when {
            timeStr != null && hrStr != null -> "$timeStr • $hrStr"
            hrStr != null -> hrStr
            timeStr != null -> timeStr
            else -> "Tracking heart rate..."
        }
    }

    /**
     * Pure formatter for bailout notification title (JVM-testable).
     */
    fun formatBailoutTitle(isCadenceFloor: Boolean): String = if (isCadenceFloor) "Cadence Floor Bailout" else "ERG Suspended"

    /**
     * Pure formatter for bailout notification content (JVM-testable).
     */
    fun formatBailoutContent(isCadenceFloor: Boolean): String = if (isCadenceFloor) {
        "Cadence dropped below floor. Spin up and tap to resume."
    } else {
        "Resistance dropped to recovery. Tap to resume."
    }

    /**
     * Pure formatter for workout completion notification title (JVM-testable).
     */
    fun formatCompletionTitle(): String = "Workout Complete!"

    /**
     * Pure formatter for workout completion notification content (JVM-testable).
     */
    fun formatCompletionContent(workoutName: String?, elapsedSeconds: Int): String {
        val timeStr = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
        return if (!workoutName.isNullOrBlank() && workoutName != "Free Ride") {
            "$workoutName Finished • $timeStr"
        } else if (elapsedSeconds > 0) {
            "Ride Finished • $timeStr"
        } else {
            "Ride Finished"
        }
    }

    /**
     * Creates notification channels for Wear OS workout tracking and high-priority alerts.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // 1. Ongoing tracking channel (LOW importance, silent, for foreground service)
            val trackingChannel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.wear_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.wear_notification_channel_desc)
                    setShowBadge(false)
                }
            manager.createNotificationChannel(trackingChannel)

            // 2. High-importance alerts channel (for Bailout & Completion alerts)
            val alertsChannel =
                NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    context.getString(R.string.wear_alert_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.wear_alert_channel_desc)
                    enableVibration(true)
                    setShowBadge(true)
                }
            manager.createNotificationChannel(alertsChannel)
        }
    }

    /**
     * Builds the ongoing workout tracking notification with OngoingActivity builder.
     */
    fun buildNotification(context: Context, title: String, content: String): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_workout_ongoing)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        val ongoingActivity =
            OngoingActivity
                .Builder(context, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_workout_ongoing)
                .setTouchIntent(pendingIntent)
                .build()

        ongoingActivity.apply(context)

        return builder.build()
    }

    /**
     * Builds high-priority bailout alert notification for Wear OS.
     * Titles resolve from localized resources with pure-formatter fallback.
     */
    fun buildBailoutNotification(context: Context, isCadenceFloor: Boolean): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val title =
            try {
                context.getString(
                    if (isCadenceFloor) {
                        R.string.wear_notification_cadence_bailout_title
                    } else {
                        R.string.wear_notification_erg_suspended_title
                    }
                )
            } catch (_: Exception) {
                formatBailoutTitle(isCadenceFloor)
            }
        val content = formatBailoutContent(isCadenceFloor)

        return NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_workout_ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Builds high-priority workout completion notification for Wear OS.
     */
    fun buildWorkoutCompletedNotification(
        context: Context,
        workoutName: String?,
        elapsedSeconds: Int
    ): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val title =
            try {
                context.getString(R.string.wear_notification_completed_title)
            } catch (_: Exception) {
                formatCompletionTitle()
            }
        val content = formatCompletionContent(workoutName, elapsedSeconds)

        return NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_workout_ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Posts the bailout alert notification on Wear OS.
     */
    fun postBailoutNotification(context: Context, isCadenceFloor: Boolean) {
        try {
            createNotificationChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val notification = buildBailoutNotification(context, isCadenceFloor)
            manager.notify(NOTIFICATION_ID_BAILOUT, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post bailout notification: ${e.message}", e)
        }
    }

    /**
     * Dismisses the bailout alert notification on Wear OS.
     */
    fun cancelBailoutNotification(context: Context) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.cancel(NOTIFICATION_ID_BAILOUT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel bailout notification: ${e.message}", e)
        }
    }

    /**
     * Posts the workout completed notification on Wear OS.
     */
    fun postWorkoutCompletedNotification(
        context: Context,
        workoutName: String?,
        elapsedSeconds: Int
    ) {
        try {
            createNotificationChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val notification = buildWorkoutCompletedNotification(context, workoutName, elapsedSeconds)
            manager.notify(NOTIFICATION_ID_COMPLETED, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post workout completed notification: ${e.message}", e)
        }
    }

    /**
     * Dismisses the workout completed notification on Wear OS.
     */
    fun cancelWorkoutCompletedNotification(context: Context) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.cancel(NOTIFICATION_ID_COMPLETED)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel workout completed notification: ${e.message}", e)
        }
    }
}

package com.valpr.bikecompanion.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import com.valpr.bikecompanion.wear.MainActivity
import com.valpr.bikecompanion.wear.R

/**
 * Builds Wear OS foreground notifications and binds the [OngoingActivity]
 * watch-face indicator so users have a 1-tap shortcut back to [MainActivity].
 */
object WearNotificationHelper {
    const val CHANNEL_ID = "wear_workout_tracking"
    const val NOTIFICATION_ID = 2001

    /**
     * Pure content formatter (JVM-testable without Android stubs).
     */
    fun formatContent(elapsedSeconds: Int, heartRateBpm: Int): String {
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
     * Creates the notification channel for Wear OS workout tracking.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.wear_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.wear_notification_channel_desc)
                    setShowBadge(false)
                }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the notification and applies the OngoingActivity builder.
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
}

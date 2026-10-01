package com.valpr.bikecompanion.wear.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.valpr.bikecompanion.shared.HapticAlertType

/**
 * Manages tactile haptic alerts on the Wear OS watch.
 */
class WatchHapticManager(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val TAG = "WatchHapticManager"
        private const val COMPLETION_DEBOUNCE_MS = 2000L
    }

    // Initialized below zero so a fake clock starting at t=0 still fires the first alert.
    @Volatile
    private var lastCompletionMs = -COMPLETION_DEBOUNCE_MS

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun playAlert(alertType: HapticAlertType) {
        val vib =
            vibrator ?: run {
                Log.w(TAG, "No vibrator service available on device")
                return
            }

        try {
            when (alertType) {
                HapticAlertType.CRITICAL_HR_WARNING -> {
                    // Double pulse warning for critical HR threshold
                    val timings = longArrayOf(0, 180, 80, 180)
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vib.vibrate(effect)
                }

                HapticAlertType.BAILOUT_TRIGGERED -> {
                    // Strong, distinct single pulse for ERG bailout
                    val effect = VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                }

                HapticAlertType.RESUME_TRIGGERED -> {
                    // Short crisp tap pulse for ERG re-engagement
                    val effect = VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                }

                HapticAlertType.WORKOUT_COMPLETED -> {
                    val now = clock()
                    synchronized(this) {
                        if (now - lastCompletionMs < COMPLETION_DEBOUNCE_MS) {
                            return
                        }
                        lastCompletionMs = now
                    }
                    // Distinct celebratory multi-pulse vibration on workout completion
                    val timings = longArrayOf(0, 150, 100, 150, 100, 350)
                    val amplitudes = intArrayOf(0, 200, 0, 200, 0, 255)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vib.vibrate(effect)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing haptic effect for $alertType: ${e.message}", e)
        }
    }
}

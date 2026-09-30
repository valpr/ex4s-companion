package com.valpr.bikecompanion.wear.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.valpr.bikecompanion.shared.HeartRateBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Manages low-power heart rate telemetry using Health Services API (ExerciseClient)
 * on Pixel Watch co-processors, with graceful fallback to standard SensorManager.
 *
 * Implements batched delivery:
 * - 2-3s batching in active display mode
 * - 5-10s batching in ambient display mode (to allow AP sleep)
 */
class HealthServicesManager(
    private val context: Context,
    private val onBatchReady: (HeartRateBatch) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "HealthServicesMgr"
        const val ACTIVE_BATCH_INTERVAL_MS = HrBatchAccumulator.ACTIVE_INTERVAL_MS
        const val AMBIENT_BATCH_INTERVAL_MS = HrBatchAccumulator.AMBIENT_INTERVAL_MS
    }

    private val healthClient by lazy { HealthServices.getClient(context) }
    private val exerciseClient by lazy { healthClient.exerciseClient }
    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var isAmbientMode = false
    private val batchAccumulator = HrBatchAccumulator()

    private var batchLoopJob: Job? = null
    private var isUsingFallbackSensor = false
    private val executor = Executors.newSingleThreadExecutor()

    private val exerciseCallback =
        object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                val hrDataPoints = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                if (hrDataPoints.isNotEmpty()) {
                    for (dp in hrDataPoints) {
                        val bpm = dp.value.toInt()
                        recordSample(bpm)
                    }
                }
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}

            override fun onRegistered() {
                Log.d(TAG, "ExerciseClient callback registered successfully")
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                Log.w(TAG, "ExerciseClient callback registration failed: ${throwable.message}")
                startFallbackSensorTracking()
            }

            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
                Log.d(TAG, "Health Services availability changed: $dataType -> $availability")
            }
        }

    private val fallbackSensorListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
                    val bpm = event.values.firstOrNull()?.toInt() ?: 0
                    if (bpm > 0) {
                        recordSample(bpm)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

    /**
     * Starts low-power ExerciseClient heart rate session.
     */
    fun startHeartRateTracking() {
        if (_isTracking.value) return
        _isTracking.value = true

        startBatchDispatchLoop()

        try {
            val config =
                ExerciseConfig
                    .builder(ExerciseType.BIKING_STATIONARY)
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                    .setIsAutoPauseAndResumeEnabled(false)
                    .setIsGpsEnabled(false)
                    .build()

            exerciseClient.setUpdateCallback(exerciseCallback)

            val startFuture = exerciseClient.startExerciseAsync(config)
            Futures.addCallback(
                startFuture,
                object : FutureCallback<Void> {
                    override fun onSuccess(result: Void?) {
                        Log.i(TAG, "Health Services ExerciseClient started on co-processor")
                        isUsingFallbackSensor = false
                    }

                    override fun onFailure(t: Throwable) {
                        Log.w(TAG, "Health Services start failed, falling back to SensorManager: ${t.message}")
                        startFallbackSensorTracking()
                    }
                },
                executor
            )
        } catch (e: Exception) {
            Log.w(TAG, "Exception initializing ExerciseClient: ${e.message}", e)
            startFallbackSensorTracking()
        }
    }

    /**
     * Fallback standard sensor listener if Health Services ExerciseClient is unsupported.
     */
    private fun startFallbackSensorTracking() {
        if (isUsingFallbackSensor) return
        isUsingFallbackSensor = true

        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (hrSensor != null) {
            sensorManager.registerListener(
                fallbackSensorListener,
                hrSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.i(TAG, "Standard SensorManager HEART_RATE tracking registered")
        } else {
            Log.w(TAG, "No HEART_RATE sensor found on device")
        }
    }

    /**
     * Updates batch frequency depending on Ambient Mode.
     * Active: 2-3s; Ambient: 5-10s.
     */
    fun setAmbientMode(ambient: Boolean) {
        isAmbientMode = ambient
        Log.d(
            TAG,
            "Ambient mode updated: $ambient (Interval: ${if (ambient) AMBIENT_BATCH_INTERVAL_MS else ACTIVE_BATCH_INTERVAL_MS}ms)"
        )
    }

    /**
     * Ingests a new instantaneous HR sample into the batch buffer.
     */
    fun recordSample(bpm: Int) {
        if (!batchAccumulator.recordSample(bpm)) return
        _currentHeartRate.value = bpm
    }

    /**
     * Periodic batch dispatch loop.
     */
    private fun startBatchDispatchLoop() {
        batchLoopJob?.cancel()
        batchLoopJob =
            scope.launch {
                while (isActive) {
                    val interval = HrBatchAccumulator.intervalFor(isAmbientMode)
                    delay(interval)

                    val samplesToSend: List<Int> = batchAccumulator.drain()

                    if (samplesToSend.isNotEmpty()) {
                        val batch =
                            HeartRateBatch(
                                timestampMs = System.currentTimeMillis(),
                                bpmSamples = samplesToSend,
                                accuracy = 3
                            )
                        onBatchReady(batch)
                    }
                }
            }
    }

    /**
     * Stops heart rate tracking and shuts down sessions.
     */
    fun stopHeartRateTracking() {
        _isTracking.value = false
        batchLoopJob?.cancel()
        batchLoopJob = null

        if (isUsingFallbackSensor) {
            try {
                sensorManager.unregisterListener(fallbackSensorListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering fallback sensor: ${e.message}")
            }
            isUsingFallbackSensor = false
        } else {
            try {
                val endFuture = exerciseClient.endExerciseAsync()
                Futures.addCallback(
                    endFuture,
                    object : FutureCallback<Void> {
                        override fun onSuccess(result: Void?) {
                            Log.d(TAG, "ExerciseClient session ended cleanly")
                        }

                        override fun onFailure(t: Throwable) {
                            Log.w(TAG, "Error ending ExerciseClient session: ${t.message}")
                        }
                    },
                    executor
                )
                exerciseClient.clearUpdateCallbackAsync(exerciseCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error ending ExerciseClient: ${e.message}")
            }
        }

        batchAccumulator.clear()
        _currentHeartRate.value = 0
    }

    fun onDestroy() {
        stopHeartRateTracking()
        scope.cancel()
        executor.shutdown()
    }
}

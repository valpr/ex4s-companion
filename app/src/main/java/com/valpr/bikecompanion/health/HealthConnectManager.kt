package com.valpr.bikecompanion.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Power
import com.valpr.bikecompanion.workout.WorkoutSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Sync state for the post-workout Health Connect batch write.
 */
sealed interface HealthSyncState {
    data object Idle : HealthSyncState
    data object Syncing : HealthSyncState
    data object Success : HealthSyncState
    data object PermissionRequired : HealthSyncState
    data object NotAvailable : HealthSyncState
    data class Failed(val reason: String) : HealthSyncState
}

/**
 * Availability + permission snapshot for Settings display.
 */
data class HealthConnectionStatus(val providerAvailable: Boolean? = null, val permissionsGranted: Boolean? = null)

/** Plain HR point (JVM-safe, no Health Connect dependency). */
data class HrPoint(val timeEpochMs: Long, val bpm: Int)

/** Plain power point (JVM-safe, no Health Connect dependency). */
data class PowerPoint(val timeEpochMs: Long, val watts: Int)

/**
 * Planned record set derived from a [WorkoutSummary] using only plain Kotlin types,
 * so the planning math is unit-testable on the JVM without Android/Health Connect.
 */
data class PlannedWorkoutRecords(
    val sessionStartEpochMs: Long,
    val sessionEndEpochMs: Long,
    val hrPoints: List<HrPoint>,
    val powerChunks: List<List<PowerPoint>>,
    val totalCaloriesKcal: Double,
    val activeCaloriesKcal: Double
)

/**
 * Batch-writes completed workouts to Google Health Connect (cold storage).
 *
 * Never called during a workout — invoked once on session completion with the
 * full in-memory sample buffer:
 * 1× [ExerciseSessionRecord] (`BIKING_STATIONARY`), 1× [HeartRateRecord],
 * N× [PowerRecord] (chunked to stay under the Binder buffer), and
 * 1× [ActiveCaloriesBurnedRecord] (1 kJ mechanical ≈ 1 kcal metabolic).
 */
class HealthConnectManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "HealthConnectManager"

        /** Max span per PowerRecord — keeps large sessions under the ~1MB Binder limit. */
        const val POWER_CHUNK_SECONDS = 1800

        /** Permissions required for the batch write. */
        fun requiredPermissions(): Set<String> = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(PowerRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
        )

        /**
         * Active calories from mechanical work using the kJ ≈ kcal rule.
         * `total` is the full metabolic cost; subtract resting (BMR ≈ 1 kcal/kg/hr)
         * for the duration to match Fitbit / Pixel Watch "active calories".
         */
        fun activeCaloriesKcal(totalWorkKj: Double, weightKg: Double, durationSeconds: Int): Double {
            val total = totalWorkKj.coerceAtLeast(0.0)
            val resting = (weightKg.coerceAtLeast(0.0) * durationSeconds.coerceAtLeast(0)) / 3600.0
            return (total - resting).coerceAtLeast(0.0)
        }

        /**
         * Pure record-window helper (JVM-testable): clamps [rawStartMs, rawEndMs]
         * into the parent session window and enforces a ≥1s non-zero interval.
         * Returns null when the bucket lies fully outside the session and must be dropped
         * (emitting it would throw IllegalArgumentException and fail the whole batch).
         */
        fun clampRecordWindow(
            rawStartMs: Long,
            rawEndMs: Long,
            sessionStartMs: Long,
            sessionEndMs: Long
        ): Pair<Long, Long>? {
            val s = rawStartMs.coerceIn(sessionStartMs, sessionEndMs)
            if (s >= sessionEndMs) return null
            var e = rawEndMs.coerceIn(sessionStartMs, sessionEndMs)
            if (e <= s) {
                e = (s + 1000L).coerceAtMost(sessionEndMs)
            }
            if (e <= s) return null
            return s to e
        }

        /**
         * Plans Health Connect record windows from a workout summary.
         * All sample timestamps are clamped into `[start, end]` (a single sample
         * 1ms outside throws `IllegalArgumentException` at insert time).
         */
        fun planRecords(summary: WorkoutSummary, weightKg: Double): PlannedWorkoutRecords {
            val durationMs = summary.totalDurationSeconds.coerceAtLeast(1) * 1000L
            val endHint = if (summary.startTimeEpochMs > 0) {
                summary.startTimeEpochMs + durationMs
            } else {
                System.currentTimeMillis()
            }
            val start = if (summary.startTimeEpochMs > 0) summary.startTimeEpochMs else endHint - durationMs
            val end = start + durationMs

            val hrPoints = summary.samples
                .filter { it.heartRateBpm > 0 }
                .map { sample ->
                    HrPoint(
                        timeEpochMs = (start + sample.elapsedSeconds * 1000L).coerceIn(start, end),
                        bpm = sample.heartRateBpm
                    )
                }

            // Chunk power samples into ≤30-min buckets keyed by elapsed second.
            val powerChunks = summary.samples
                .groupBy { it.elapsedSeconds / POWER_CHUNK_SECONDS }
                .toSortedMap()
                .values
                .map { bucket ->
                    bucket.map { sample ->
                        PowerPoint(
                            timeEpochMs = (start + sample.elapsedSeconds * 1000L).coerceIn(start, end),
                            watts = sample.watts.coerceAtLeast(0)
                        )
                    }
                }
                .filter { it.isNotEmpty() }

            val totalKcal = summary.totalWorkKj.coerceAtLeast(0.0)

            return PlannedWorkoutRecords(
                sessionStartEpochMs = start,
                sessionEndEpochMs = end,
                hrPoints = hrPoints,
                powerChunks = powerChunks,
                totalCaloriesKcal = totalKcal,
                activeCaloriesKcal = activeCaloriesKcal(
                    totalWorkKj = summary.totalWorkKj,
                    weightKg = weightKg,
                    durationSeconds = summary.totalDurationSeconds
                )
            )
        }

        /**
         * Assembles Health Connect [Record] objects from a plan. Runs on-device only
         * (kept separate from [planRecords] so planning stays JVM-testable).
         */
        fun toHealthRecords(plan: PlannedWorkoutRecords, title: String): List<Record> {
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(
                Instant.ofEpochMilli(plan.sessionStartEpochMs)
            )
            val start = Instant.ofEpochMilli(plan.sessionStartEpochMs)
            val end = Instant.ofEpochMilli(plan.sessionEndEpochMs)
            val metadata = Metadata.activelyRecorded(
                device = Device(type = Device.TYPE_PHONE)
            )
            val records = mutableListOf<Record>()

            records += ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zoneOffset,
                endTime = end,
                endZoneOffset = zoneOffset,
                metadata = metadata,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
                title = title
            )

            if (plan.hrPoints.isNotEmpty()) {
                val window = clampRecordWindow(
                    plan.hrPoints.first().timeEpochMs,
                    plan.hrPoints.last().timeEpochMs,
                    plan.sessionStartEpochMs,
                    plan.sessionEndEpochMs
                )
                if (window != null) {
                    val (hrStartMs, hrEndMs) = window
                    val hrStart = Instant.ofEpochMilli(hrStartMs)
                    val hrEnd = Instant.ofEpochMilli(hrEndMs)
                    records += HeartRateRecord(
                        startTime = hrStart,
                        startZoneOffset = zoneOffset,
                        endTime = hrEnd,
                        endZoneOffset = zoneOffset,
                        samples = plan.hrPoints.map { point ->
                            HeartRateRecord.Sample(
                                time = Instant.ofEpochMilli(point.timeEpochMs),
                                beatsPerMinute = point.bpm.toLong()
                            )
                        },
                        metadata = metadata
                    )
                }
            }

            for (chunk in plan.powerChunks) {
                val window = clampRecordWindow(
                    chunk.first().timeEpochMs,
                    chunk.last().timeEpochMs,
                    plan.sessionStartEpochMs,
                    plan.sessionEndEpochMs
                ) ?: continue
                val chunkStart = Instant.ofEpochMilli(window.first)
                val chunkEnd = Instant.ofEpochMilli(window.second)
                records += PowerRecord(
                    startTime = chunkStart,
                    startZoneOffset = zoneOffset,
                    endTime = chunkEnd,
                    endZoneOffset = zoneOffset,
                    samples = chunk.map { point ->
                        PowerRecord.Sample(
                            power = Power.watts(point.watts.toDouble()),
                            time = Instant.ofEpochMilli(point.timeEpochMs)
                        )
                    },
                    metadata = metadata
                )
            }

            records += ActiveCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = zoneOffset,
                endTime = end,
                endZoneOffset = zoneOffset,
                energy = Energy.kilocalories(plan.activeCaloriesKcal),
                metadata = metadata
            )

            return records
        }
    }

    private val _syncState = MutableStateFlow<HealthSyncState>(HealthSyncState.Idle)
    val syncState: StateFlow<HealthSyncState> = _syncState.asStateFlow()

    private val _connectionStatus = MutableStateFlow(HealthConnectionStatus())
    val connectionStatus: StateFlow<HealthConnectionStatus> = _connectionStatus.asStateFlow()

    private var lastSummary: WorkoutSummary? = null
    private var lastWeightKg: Float = 75.0f

    private fun clientOrNull(): HealthConnectClient? = try {
        HealthConnectClient.getOrCreate(context)
    } catch (e: Exception) {
        Log.w(TAG, "Health Connect provider unavailable: ${e.message}")
        null
    }

    /** Refreshes availability + permission snapshot (for Settings display). */
    fun refreshConnectionStatus() {
        scope.launch {
            val client = clientOrNull()
            if (client == null) {
                _connectionStatus.value = HealthConnectionStatus(
                    providerAvailable = false,
                    permissionsGranted = false
                )
                return@launch
            }
            val granted = try {
                client.permissionController.getGrantedPermissions().containsAll(requiredPermissions())
            } catch (e: Exception) {
                Log.w(TAG, "Permission check failed: ${e.message}")
                false
            }
            _connectionStatus.value = HealthConnectionStatus(
                providerAvailable = true,
                permissionsGranted = granted
            )
        }
    }

    /**
     * Batch-writes a completed workout. Safe to call repeatedly — records the
     * request for [retry] and reports progress via [syncState].
     */
    fun syncWorkout(summary: WorkoutSummary, weightKg: Float) {
        lastSummary = summary
        lastWeightKg = weightKg
        scope.launch {
            _syncState.value = HealthSyncState.Syncing
            val client = clientOrNull()
            if (client == null) {
                _syncState.value = HealthSyncState.NotAvailable
                return@launch
            }
            val hasPermissions = try {
                client.permissionController.getGrantedPermissions().containsAll(requiredPermissions())
            } catch (e: Exception) {
                Log.w(TAG, "Permission check failed: ${e.message}")
                false
            }
            if (!hasPermissions) {
                _syncState.value = HealthSyncState.PermissionRequired
                return@launch
            }
            try {
                val plan = planRecords(summary, weightKg.toDouble())
                val records = toHealthRecords(plan, summary.workoutName)
                client.insertRecords(records)
                Log.i(TAG, "Synced ${summary.workoutName}: ${records.size} records")
                _syncState.value = HealthSyncState.Success
            } catch (e: Exception) {
                Log.w(TAG, "Health Connect write failed: ${e.message}")
                _syncState.value = HealthSyncState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    /** Re-attempts the last sync (used by the summary retry button). */
    fun retry() {
        val summary = lastSummary ?: return
        syncWorkout(summary, lastWeightKg)
    }

    fun reset() {
        _syncState.value = HealthSyncState.Idle
    }

    fun onDestroy() {
        scope.cancel()
    }
}

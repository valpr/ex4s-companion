package com.valpr.bikecompanion.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Opt-in reader for importing athlete profile metrics (weight, height, resting HR)
 * and recovery indicators (sleep, HRV) from Health Connect into local settings.
 */
class HealthConnectReader(
    private val context: Context,
    private val clientProvider: () -> HealthConnectClient? = {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.w("HealthConnectReader", "Health Connect client unavailable: ${e.message}")
            null
        }
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "HealthConnectReader"

        /** Permissions required to read athlete metrics and recovery data. */
        fun readPermissions(): Set<String> = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
        )
    }

    suspend fun hasReadPermissions(): Boolean {
        val client = clientProvider() ?: return false
        return try {
            client.permissionController.getGrantedPermissions().containsAll(readPermissions())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check read permissions: ${e.message}")
            false
        }
    }

    /**
     * Reads recent metrics from Health Connect over the last [lookbackDays] (default 7)
     * and compiles an import preview compared against [currentProfileLastUpdatedEpochMs].
     */
    suspend fun fetchImportPreview(
        currentProfileLastUpdatedEpochMs: Long,
        lookbackDays: Long = 7L
    ): Result<HealthImportPreview> = withContext(ioDispatcher) {
        val client = clientProvider() ?: return@withContext Result.failure(
            IllegalStateException("Health Connect is unavailable on this device")
        )

        try {
            val now = Instant.now()
            val startWindow = now.minus(lookbackDays, ChronoUnit.DAYS)
            val filter = TimeRangeFilter.between(startWindow, now)

            val weights = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = filter
                    )
                ).records.map { it.time.toEpochMilli() to it.weight.inKilograms.toFloat() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading WeightRecords: ${e.message}")
                emptyList()
            }

            val heights = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeightRecord::class,
                        timeRangeFilter = filter
                    )
                ).records.map { it.time.toEpochMilli() to (it.height.inMeters * 100.0).toFloat() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading HeightRecords: ${e.message}")
                emptyList()
            }

            val restingHrs = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = filter
                    )
                ).records.map { it.time.toEpochMilli() to it.beatsPerMinute.toInt() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading RestingHeartRateRecords: ${e.message}")
                emptyList()
            }

            val sleepSessions = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = filter
                    )
                ).records.map {
                    val durationMinutes = java.time.Duration.between(it.startTime, it.endTime).toMinutes()
                    it.endTime.toEpochMilli() to durationMinutes
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading SleepSessionRecords: ${e.message}")
                emptyList()
            }

            val hrvs = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateVariabilityRmssdRecord::class,
                        timeRangeFilter = filter
                    )
                ).records.map { it.time.toEpochMilli() to it.heartRateVariabilityMillis }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading HRV records: ${e.message}")
                emptyList()
            }

            val preview = HealthImportCalculator.buildPreview(
                weights = weights,
                heights = heights,
                restingHrs = restingHrs,
                sleepSessions = sleepSessions,
                hrvs = hrvs,
                currentProfileLastUpdatedEpochMs = currentProfileLastUpdatedEpochMs
            )

            Result.success(preview)
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching import preview: ${e.message}", e)
            Result.failure(e)
        }
    }
}

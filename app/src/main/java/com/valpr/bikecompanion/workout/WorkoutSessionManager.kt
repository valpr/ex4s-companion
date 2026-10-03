package com.valpr.bikecompanion.workout

import com.valpr.bikecompanion.ble.EchelonBleManager
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.data.UserProfileRepository
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.engine.ErgDecision
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.HapticAlertType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SessionStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

data class WorkoutMetricSample(
    val elapsedSeconds: Int,
    val watts: Int,
    val targetWatts: Int?,
    val cadenceRpm: Int,
    val targetCadence: Int?,
    val resistance: Int,
    val speedKmh: Double,
    val heartRateBpm: Int = 0
)

data class WorkoutSummary(
    val workoutName: String,
    val totalDurationSeconds: Int,
    val totalDistanceKm: Double,
    val avgWatts: Int,
    val maxWatts: Int,
    val avgCadence: Int,
    val maxCadence: Int,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val totalWorkKj: Double,
    val totalCaloriesKcal: Int,
    val samples: List<WorkoutMetricSample>,
    /** Wall-clock epoch millis when the session started (anchors Health Connect record windows). */
    val startTimeEpochMs: Long = 0L,
    val workout: Workout? = null
)

data class WorkoutSessionState(
    val status: SessionStatus = SessionStatus.IDLE,
    val workout: Workout? = null,
    /** Original `.zwo` library filename (null for Free Ride). Feeds history attribution. */
    val sourceWorkoutFilename: String? = null,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val targetWatts: Int? = null,
    val targetCadence: Int? = null,
    val intensityScale: Float = 1.0f,
    val ergDecision: ErgDecision? = null,
    val activeCues: List<WorkoutTextEvent> = emptyList(),
    val currentPosition: SegmentPosition? = null,
    val latestTelemetry: BikeTelemetry = BikeTelemetry(),
    val currentHeartRate: Int = 0,
    val isCriticalHrActive: Boolean = false,
    val athleteMaxHr: Int = 190,
    val athleteRestingHr: Int = 60,
    val useKarvonenZones: Boolean = false,
    val athletePreferredCadence: Int = 85,
    val summary: WorkoutSummary? = null
) {
    val isFreeRide: Boolean get() = workout == null
    val progress: Float
        get() = if (totalSeconds > 0) (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f
    val formattedElapsedTime: String
        get() {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
    val formattedRemainingTime: String
        get() {
            val remaining = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
            val minutes = remaining / 60
            val seconds = remaining % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

/**
 * Manages live workout session execution, playhead progression, and ERG control loops.
 */
class WorkoutSessionManager(
    private val telemetryFlow: StateFlow<BikeTelemetry>,
    private val onSetResistance: (Int) -> Unit,
    private val userProfileFlow: kotlinx.coroutines.flow.Flow<UserProfile>,
    private val ergController: ErgController = ErgController(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val isBikeConnected: () -> Boolean = { true }
) {
    constructor(
        bleManager: EchelonBleManager,
        userProfileRepository: UserProfileRepository,
        ergController: ErgController = ErgController(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ) : this(
        telemetryFlow = bleManager.telemetry,
        onSetResistance = { bleManager.setResistance(it) },
        userProfileFlow = userProfileRepository.userProfileFlow,
        ergController = ergController,
        scope = scope,
        isBikeConnected = {
            bleManager.connectionState.value is com.valpr.bikecompanion.data.BleConnectionState.Connected
        }
    )

    private val _sessionState = MutableStateFlow(WorkoutSessionState())
    val sessionState: StateFlow<WorkoutSessionState> = _sessionState.asStateFlow()

    private val _hapticAlerts = MutableSharedFlow<HapticAlertType>(extraBufferCapacity = 16)
    val hapticAlerts: SharedFlow<HapticAlertType> = _hapticAlerts.asSharedFlow()

    private var sessionJob: Job? = null
    private var profileJob: Job? = null
    private val recordedSamples = mutableListOf<WorkoutMetricSample>()
    private var athleteFtp: Int = 200
    private var athleteFtpConfigured: Boolean = false
    private var athleteMaxHr: Int = 190
    private var athleteRestingHr: Int = 60
    private var useKarvonenZones: Boolean = false
    private var athleteCriticalHr: Int = 175
    private var athletePreferredCadence: Int = 85
    private var configuredCadenceFloorRpm: Double = 60.0
    private var sessionStartEpochMs: Long = 0L
    private var pauseOdometerSnapshotKm: Double = 0.0
    private var pausedDistanceKm: Double = 0.0

    /** Whether a structured (FTP-based) workout may start. Free Ride (`null`) is always allowed. */
    val isFtpConfigured: Boolean
        get() = athleteFtpConfigured

    init {
        // Observe live telemetry from bike
        scope.launch {
            telemetryFlow.collect { telemetry ->
                _sessionState.update { it.copy(latestTelemetry = telemetry) }
            }
        }

        // Observe athlete profile to tune ERG controller and update FTP
        bindUserProfileFlow(userProfileFlow)
    }

    /**
     * Rebinds the athlete profile source (profile switching). Safe only while
     * no session is RUNNING/PAUSED — callers must guard the switch.
     */
    fun bindUserProfileFlow(flow: kotlinx.coroutines.flow.Flow<UserProfile>) {
        profileJob?.cancel()
        profileJob = scope.launch {
            flow.collect { profile -> applyUserProfile(profile) }
        }
    }

    private fun applyUserProfile(profile: UserProfile) {
        athleteFtp = if (profile.isFtpConfigured) profile.ftp else 200
        athleteFtpConfigured = profile.isFtpConfigured
        athleteMaxHr = profile.maxHeartRate
        athleteRestingHr = profile.restingHeartRate
        useKarvonenZones = profile.useKarvonenZones
        athleteCriticalHr = profile.criticalHeartRate
        athletePreferredCadence = profile.preferredCadenceRpm
        ergController.kp = profile.ergKp.toDouble()
        ergController.ki = profile.ergKi.toDouble()
        configuredCadenceFloorRpm = profile.cadenceFloorRpm.toDouble()
        ergController.cadenceFloorRpm = configuredCadenceFloorRpm
        ergController.recoveryThresholdRpm = profile.cadenceRecoveryRpm.toDouble()
        _sessionState.update {
            it.copy(
                athleteMaxHr = profile.maxHeartRate,
                athleteRestingHr = profile.restingHeartRate,
                useKarvonenZones = profile.useKarvonenZones,
                athletePreferredCadence = profile.preferredCadenceRpm
            )
        }
    }

    /**
     * Updates the current heart rate reading from the Pixel Watch.
     * Evaluates dynamic HR capping threshold and triggers haptic alerts if critical.
     */
    fun updateHeartRate(bpm: Int) {
        if (bpm <= 0) return
        val clampedBpm = bpm.coerceIn(30, 250)
        val wasCritical = ergController.isCriticalHrActive
        val isCritical = if (athleteCriticalHr > 0) {
            if (wasCritical) {
                clampedBpm >= (athleteCriticalHr - 5)
            } else {
                clampedBpm >= athleteCriticalHr
            }
        } else {
            false
        }

        ergController.isCriticalHrActive = isCritical

        if (isCritical && !wasCritical) {
            _hapticAlerts.tryEmit(HapticAlertType.CRITICAL_HR_WARNING)
        }

        _sessionState.update {
            it.copy(
                currentHeartRate = clampedBpm,
                isCriticalHrActive = isCritical
            )
        }
    }

    /**
     * Starts a structured workout or a free ride session.
     * Structured workouts require a configured FTP; Free Ride (`null`) is always allowed.
     * Both require a live bike connection — starting disconnected would record
     * phantom zero-telemetry and send ERG commands into a dropped GATT.
     * Returns failure instead of silently falling back to a phantom 200W target.
     * @param sourceFilename original `.zwo` library filename for history attribution
     * (null for Free Ride or unknown sources).
     */
    fun startWorkout(workout: Workout?, sourceFilename: String? = null): Result<Unit> {
        if (!isBikeConnected()) {
            return Result.failure(IllegalStateException("Bike not connected"))
        }
        if (workout != null && !athleteFtpConfigured) {
            return Result.failure(IllegalStateException("FTP required for structured workouts"))
        }
        stopSessionLoop()
        recordedSamples.clear()
        ergController.reset()
        sessionStartEpochMs = System.currentTimeMillis()
        pauseOdometerSnapshotKm = 0.0
        pausedDistanceKm = 0.0

        val totalDuration = workout?.totalDurationSeconds ?: 0

        // Re-evaluate HR cap from preserved HR instead of forcing false (avoids 1-tick delay).
        val preservedHr = _sessionState.value.currentHeartRate
        val reEvaluatedCritical = if (athleteCriticalHr > 0 && preservedHr > 0) {
            preservedHr >= athleteCriticalHr
        } else {
            false
        }
        ergController.isCriticalHrActive = reEvaluatedCritical

        _sessionState.update {
            WorkoutSessionState(
                status = SessionStatus.RUNNING,
                workout = workout,
                sourceWorkoutFilename = sourceFilename,
                elapsedSeconds = 0,
                totalSeconds = totalDuration,
                intensityScale = 1.0f,
                latestTelemetry = it.latestTelemetry,
                currentHeartRate = it.currentHeartRate,
                isCriticalHrActive = reEvaluatedCritical,
                athleteMaxHr = it.athleteMaxHr,
                athleteRestingHr = it.athleteRestingHr,
                useKarvonenZones = it.useKarvonenZones,
                athletePreferredCadence = it.athletePreferredCadence,
                summary = null
            )
        }

        startSessionLoop()
        return Result.success(Unit)
    }

    fun pauseWorkout() {
        if (_sessionState.value.status == SessionStatus.RUNNING) {
            // Snapshot the bike odometer so pedaling during the pause can be
            // excluded from the summary distance (elapsed/samples already freeze).
            pauseOdometerSnapshotKm = _sessionState.value.latestTelemetry.distanceKm
            _sessionState.update { it.copy(status = SessionStatus.PAUSED) }
        }
    }

    fun resumeWorkout() {
        if (_sessionState.value.status == SessionStatus.PAUSED) {
            // Accumulate odometer drift while paused; multiple pause/resume
            // cycles sum. Negative drift (odometer reset) is ignored.
            pausedDistanceKm +=
                (_sessionState.value.latestTelemetry.distanceKm - pauseOdometerSnapshotKm)
                    .coerceAtLeast(0.0)
            _sessionState.update { it.copy(status = SessionStatus.RUNNING) }
        }
    }

    fun setManualResistance(level: Int) {
        val clamped = level.coerceIn(1, 32)
        onSetResistance(clamped)
    }

    fun adjustManualResistance(delta: Int) {
        val current = _sessionState.value.latestTelemetry.resistanceLevel
        setManualResistance(current + delta)
    }

    /**
     * Resumes ERG mode from manual or cadence floor bailout.
     * Invariant: Actuate instantly with dtSeconds = 0.0 to prevent delay.
     */
    fun resumeManually() {
        if (ergController.state == ErgState.MANUAL_BAILOUT || ergController.state == ErgState.CADENCE_FLOOR_BAILOUT) {
            ergController.resumeManually()
            if (_sessionState.value.workout != null) {
                val telemetry = _sessionState.value.latestTelemetry
                val workout = _sessionState.value.workout
                val elapsed = _sessionState.value.elapsedSeconds
                val targetWatts = workout?.targetWattsAt(athleteFtp, elapsed, _sessionState.value.intensityScale)
                val decision = ergController.update(
                    targetWatts = targetWatts,
                    actualWatts = telemetry.estimatedWatts,
                    rawCadence = telemetry.cadenceRpm.toDouble(),
                    dtSeconds = 0.0,
                    isCriticalHr = ergController.isCriticalHrActive
                )
                if (decision.shouldSendBleCommand) {
                    onSetResistance(decision.targetResistance)
                }
                _sessionState.update { it.copy(ergDecision = decision) }
            } else {
                _sessionState.update { it.copy(ergDecision = null) }
            }
            _hapticAlerts.tryEmit(HapticAlertType.RESUME_TRIGGERED)
        }
    }

    fun toggleClutch() {
        if (ergController.state == ErgState.MANUAL_BAILOUT || ergController.state == ErgState.CADENCE_FLOOR_BAILOUT) {
            resumeManually()
        } else {
            val decision = ergController.suspendManually()
            if (decision.shouldSendBleCommand) {
                onSetResistance(decision.targetResistance)
            }
            _sessionState.update { it.copy(ergDecision = decision) }
            _hapticAlerts.tryEmit(HapticAlertType.BAILOUT_TRIGGERED)
        }
    }

    fun adjustIntensity(delta: Float) {
        val newScale = (_sessionState.value.intensityScale + delta).coerceIn(0.50f, 1.50f)
        _sessionState.update { it.copy(intensityScale = newScale) }
    }

    fun stopWorkout() {
        val currentState = _sessionState.value
        if (currentState.status == SessionStatus.IDLE || currentState.status == SessionStatus.COMPLETED) {
            return
        }
        stopSessionLoop()
        val summary = generateSummary(currentState)
        _sessionState.update {
            it.copy(
                status = SessionStatus.COMPLETED,
                summary = summary
            )
        }
        _hapticAlerts.tryEmit(HapticAlertType.WORKOUT_COMPLETED)
    }

    fun resetToIdle() {
        stopSessionLoop()
        pauseOdometerSnapshotKm = 0.0
        pausedDistanceKm = 0.0
        _sessionState.update {
            WorkoutSessionState(
                latestTelemetry = it.latestTelemetry,
                currentHeartRate = it.currentHeartRate,
                athleteMaxHr = it.athleteMaxHr,
                athleteRestingHr = it.athleteRestingHr,
                useKarvonenZones = it.useKarvonenZones,
                athletePreferredCadence = it.athletePreferredCadence
            )
        }
    }

    private fun startSessionLoop() {
        sessionJob = scope.launch {
            while (isActive) {
                delay(1000L)

                if (_sessionState.value.status != SessionStatus.RUNNING) {
                    continue
                }

                val current = _sessionState.value
                val elapsed = current.elapsedSeconds + 1
                val workout = current.workout

                if (workout != null && elapsed >= workout.totalDurationSeconds) {
                    // Structured workout finished
                    _sessionState.update { it.copy(elapsedSeconds = elapsed) }
                    stopWorkout()
                    break
                }

                // Compute playhead positions and targets
                val position = workout?.getSegmentAtTime(elapsed)
                val targetWatts = workout?.targetWattsAt(athleteFtp, elapsed, current.intensityScale)
                val targetCadence = workout?.targetCadenceAt(elapsed)
                val activeCues = workout?.activeTextEventsAt(elapsed) ?: emptyList()

                // Target-aware bailout floor: low-cadence prescriptions get
                // dip margin (target − 15) instead of the global floor, since
                // prescriptions change per segment. Applied before every
                // update so mid-ride retunes and segment changes both land.
                ergController.cadenceFloorRpm =
                    ErgController.effectiveFloor(configuredCadenceFloorRpm, targetCadence)

                // Execute ERG controller update
                val telemetry = current.latestTelemetry
                val wasInCadenceBailout = ergController.state == ErgState.CADENCE_FLOOR_BAILOUT
                val decision = ergController.update(
                    targetWatts = targetWatts,
                    actualWatts = telemetry.estimatedWatts,
                    rawCadence = telemetry.cadenceRpm.toDouble(),
                    dtSeconds = 1.0,
                    isCriticalHr = ergController.isCriticalHrActive
                )

                // Trigger haptic alert if transitioning into cadence floor bailout
                if (decision.state == ErgState.CADENCE_FLOOR_BAILOUT && !wasInCadenceBailout) {
                    _hapticAlerts.tryEmit(HapticAlertType.BAILOUT_TRIGGERED)
                }

                // Dispatch resistance to bike if needed
                if (decision.shouldSendBleCommand) {
                    onSetResistance(decision.targetResistance)
                }

                // Record sample for graphs and summary
                val sample = WorkoutMetricSample(
                    elapsedSeconds = elapsed,
                    watts = telemetry.estimatedWatts,
                    targetWatts = decision.effectiveTargetWatts ?: targetWatts,
                    cadenceRpm = telemetry.cadenceRpm,
                    targetCadence = targetCadence,
                    resistance = telemetry.resistanceLevel,
                    speedKmh = telemetry.speedKmh,
                    heartRateBpm = current.currentHeartRate
                )
                recordedSamples.add(sample)

                _sessionState.update {
                    it.copy(
                        elapsedSeconds = elapsed,
                        currentPosition = position,
                        targetWatts = decision.effectiveTargetWatts ?: targetWatts,
                        targetCadence = targetCadence,
                        activeCues = activeCues,
                        ergDecision = decision
                    )
                }
            }
        }
    }

    private fun stopSessionLoop() {
        sessionJob?.cancel()
        sessionJob = null
    }

    private fun generateSummary(state: WorkoutSessionState): WorkoutSummary {
        val samples = recordedSamples.toList()
        val duration = state.elapsedSeconds
        val workoutName = state.workout?.name ?: "Free Ride"
        // Odometer keeps counting while paused; subtract the accumulated
        // paused drift so distance matches the recorded duration/samples.
        val distance = (state.latestTelemetry.distanceKm - pausedDistanceKm).coerceAtLeast(0.0)

        val avgWatts = if (samples.isNotEmpty()) samples.map { it.watts }.average().roundToInt() else 0
        val maxWatts = samples.maxOfOrNull { it.watts } ?: 0
        val avgCadence = if (samples.isNotEmpty()) samples.map { it.cadenceRpm }.average().roundToInt() else 0
        val maxCadence = samples.maxOfOrNull { it.cadenceRpm } ?: 0

        val hrSamples = samples.filter { it.heartRateBpm > 0 }
        val avgHeartRate = if (hrSamples.isNotEmpty()) hrSamples.map { it.heartRateBpm }.average().roundToInt() else 0
        val maxHeartRate = if (hrSamples.isNotEmpty()) hrSamples.maxOf { it.heartRateBpm } else 0

        // Mechanical work (kJ) = sum(watts * 1s) / 1000
        val totalWorkKj = samples.sumOf { it.watts.toDouble() } / 1000.0
        // 1 kJ mechanical work ≈ 1 kcal metabolic cost for human cycling (Garmin / Strava standard)
        val totalCaloriesKcal = totalWorkKj.roundToInt()

        return WorkoutSummary(
            workoutName = workoutName,
            totalDurationSeconds = duration,
            totalDistanceKm = distance,
            avgWatts = avgWatts,
            maxWatts = maxWatts,
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            totalWorkKj = totalWorkKj,
            totalCaloriesKcal = totalCaloriesKcal,
            samples = samples,
            startTimeEpochMs = sessionStartEpochMs,
            workout = state.workout
        )
    }
}

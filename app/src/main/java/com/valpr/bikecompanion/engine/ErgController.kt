package com.valpr.bikecompanion.engine

import com.valpr.bikecompanion.data.EchelonWattTable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Controller states for the ERG and Anti-Spiral engine.
 */
enum class ErgState {
    /** ERG mode inactive or stopped. */
    INACTIVE,

    /** Active ERG mode: Feedforward + PI trim is commanding resistance. */
    ACTIVE,

    /** Cadence collapsed below floor threshold; ERG suspended and dropped to recovery resistance. */
    CADENCE_FLOOR_BAILOUT,

    /** Manual bailout ("The Clutch") triggered by user tap or watch gesture. */
    MANUAL_BAILOUT,

    /** Current workout segment has ERG disabled (FreeRide or MaxEffort). */
    FREE_RIDE
}

/**
 * Result of an ERG controller update cycle.
 */
data class ErgDecision(
    val state: ErgState,
    val targetResistance: Int,
    val shouldSendBleCommand: Boolean,
    val smoothedCadence: Double,
    val nominalResistance: Int,
    val trimOffset: Int,
    val powerErrorWatts: Int,
    val consecutiveRecoverySeconds: Int = 0,
    val effectiveTargetWatts: Int? = null,
    val isHrCapped: Boolean = false
)

/**
 * Feedforward + PI Trim ERG Controller with Anti-Spiral protection.
 *
 * Implements:
 * 1. Feedforward base resistance from [EchelonWattTable] using 3-second EMA cadence.
 * 2. PI trim correction clamped to [-3, +3] resistance steps.
 * 3. Integral anti-windup clamping.
 * 4. 5W deadband guard: suppresses BLE writes when output power is within 5W of target.
 * 5. Anti-Spiral cadence floor (<60 RPM) with drop to recovery resistance (8) after
 *    [belowFloorRequiredSeconds] consecutive sub-floor ticks — a single dropout
 *    sample rides a grace tick of normal ERG instead of slamming the bike.
 * 6. Recovery gate requiring >= 75 RPM sustained for 3 consecutive seconds to re-engage.
 */
class ErgController(
    var kp: Double = 0.05,
    var ki: Double = 0.01,
    var cadenceFloorRpm: Double = 60.0,
    var recoveryThresholdRpm: Double = 75.0,
    var recoveryRequiredSeconds: Int = 3,
    var deadbandWatts: Int = 5,
    var recoveryResistance: Int = 8,
    var emaAlpha: Double = 0.5, // 3-second EMA: 2 / (3 + 1) = 0.5
    var hrCappingScale: Double = 0.90, // 10% reduction when HR is critical
    var belowFloorRequiredSeconds: Int = 2 // consecutive sub-floor ticks before bailout entry
) {
    companion object {
        const val MAX_TRIM = 3
        const val MIN_RESISTANCE = EchelonWattTable.MIN_RESISTANCE
        const val MAX_RESISTANCE = EchelonWattTable.MAX_RESISTANCE
        const val MAX_INTEGRAL_WINDUP = 300.0 // Clamps integral contribution to ~300 * ki

        /**
         * Dip margin below the workout's prescribed cadence before the
         * bailout floor can trip.
         */
        const val TARGET_CADENCE_FLOOR_MARGIN_RPM = 15.0

        /**
         * Target-aware bailout floor: the lower of the athlete's configured
         * floor and 15 RPM below the prescribed cadence. Low-cadence
         * prescriptions (e.g. 70 RPM climbs) get dip margin instead of
         * tripping on the global floor; high-cadence work keeps the
         * configured floor. Null prescription (Free Ride, custom segments
         * without cadence) keeps the configured floor.
         *
         * Pure policy (no Android types) so it stays plain-JUnit; the session
         * loop applies it per tick since prescriptions change per segment.
         */
        fun effectiveFloor(configuredFloorRpm: Double, targetCadenceRpm: Int?): Double = if (targetCadenceRpm == null) {
            configuredFloorRpm
        } else {
            minOf(configuredFloorRpm, targetCadenceRpm - TARGET_CADENCE_FLOOR_MARGIN_RPM)
        }
    }

    var state: ErgState = ErgState.INACTIVE
        private set

    var smoothedCadence: Double = 0.0
        private set

    var lastCommandedResistance: Int = recoveryResistance
        private set

    var isCriticalHrActive: Boolean = false

    private var lastTargetWatts: Int? = null
    private var integralError: Double = 0.0
    private var consecutiveRecoverySeconds: Int = 0
    private var consecutiveBelowFloorSeconds: Int = 0

    /**
     * Resets internal controller state (cadence EMA, integral error, and recovery counters).
     */
    fun reset() {
        state = ErgState.INACTIVE
        smoothedCadence = 0.0
        integralError = 0.0
        consecutiveRecoverySeconds = 0
        consecutiveBelowFloorSeconds = 0
        lastTargetWatts = null
        lastCommandedResistance = recoveryResistance
        isCriticalHrActive = false
    }

    /**
     * Manually triggers the "Clutch" bailout (e.g. from UI button or watch rotary flick).
     */
    fun suspendManually(bailoutResistance: Int = recoveryResistance): ErgDecision {
        state = ErgState.MANUAL_BAILOUT
        integralError = 0.0
        consecutiveRecoverySeconds = 0
        consecutiveBelowFloorSeconds = 0
        lastTargetWatts = null
        lastCommandedResistance = bailoutResistance
        return ErgDecision(
            state = state,
            targetResistance = bailoutResistance,
            shouldSendBleCommand = true,
            smoothedCadence = smoothedCadence,
            nominalResistance = bailoutResistance,
            trimOffset = 0,
            powerErrorWatts = 0,
            consecutiveRecoverySeconds = 0,
            effectiveTargetWatts = null,
            isHrCapped = false
        )
    }

    /**
     * Resumes ERG mode from manual or cadence-floor bailout.
     */
    fun resumeManually() {
        state = ErgState.ACTIVE
        integralError = 0.0
        consecutiveRecoverySeconds = 0
        consecutiveBelowFloorSeconds = 0
        lastTargetWatts = null
        lastCommandedResistance = -1
    }

    /**
     * Updates cadence EMA smoothing.
     */
    private fun updateSmoothedCadence(rawCadence: Double) {
        smoothedCadence = when {
            rawCadence <= 0.0 -> 0.0
            smoothedCadence <= 0.0 -> rawCadence
            else -> emaAlpha * rawCadence + (1.0 - emaAlpha) * smoothedCadence
        }
    }

    /**
     * Computes the next resistance command for the EX-4S bike.
     *
     * @param targetWatts Target power in Watts. If null, ERG is disabled (FreeRide / MaxEffort).
     * @param actualWatts Current estimated power output from bike telemetry.
     * @param rawCadence Current instantaneous cadence in RPM from 0xD1 frame.
     * @param dtSeconds Sample period in seconds (typically 1.0s).
     */
    fun update(
        targetWatts: Int?,
        actualWatts: Int,
        rawCadence: Double,
        dtSeconds: Double = 1.0,
        isCriticalHr: Boolean = isCriticalHrActive
    ): ErgDecision {
        updateSmoothedCadence(rawCadence)

        // Case 1: Target watts is null -> FreeRide or MaxEffort (ERG disabled)
        if (targetWatts == null) {
            state = ErgState.FREE_RIDE
            integralError = 0.0
            consecutiveRecoverySeconds = 0
            consecutiveBelowFloorSeconds = 0
            lastTargetWatts = null
            return ErgDecision(
                state = state,
                targetResistance = lastCommandedResistance,
                shouldSendBleCommand = false,
                smoothedCadence = smoothedCadence,
                nominalResistance = lastCommandedResistance,
                trimOffset = 0,
                powerErrorWatts = 0,
                effectiveTargetWatts = null,
                isHrCapped = false
            )
        }

        // Apply Dynamic HR Capping (10% reduction) if critical HR is active
        val effectiveTargetWatts = if (isCriticalHr) {
            (targetWatts * hrCappingScale).roundToInt().coerceAtLeast(0)
        } else {
            targetWatts
        }

        // Case 2: In Manual Bailout mode -> maintain bailout resistance until rider resumes
        if (state == ErgState.MANUAL_BAILOUT) {
            return ErgDecision(
                state = state,
                targetResistance = lastCommandedResistance,
                shouldSendBleCommand = false,
                smoothedCadence = smoothedCadence,
                nominalResistance = recoveryResistance,
                trimOffset = 0,
                powerErrorWatts = effectiveTargetWatts - actualWatts,
                effectiveTargetWatts = effectiveTargetWatts,
                isHrCapped = isCriticalHr
            )
        }

        // Case 3: Anti-Spiral check with entry debounce. A single sub-floor
        // sample (BLE dropout, pedal micro-pause) must not slam the bike to
        // recovery: entry requires `belowFloorRequiredSeconds` consecutive
        // sub-floor ticks — symmetric with the recovery gate (Case 4). Grace
        // ticks fall through to normal ERG below, which is spiral-safe here:
        // feedforward at collapsed cadence yields MIN_RESISTANCE and PI trim
        // is clamped to ±3.
        val isCadenceBelowFloor = (rawCadence < cadenceFloorRpm || smoothedCadence < cadenceFloorRpm)
        if (state != ErgState.CADENCE_FLOOR_BAILOUT) {
            if (!isCadenceBelowFloor) {
                consecutiveBelowFloorSeconds = 0
            } else {
                consecutiveBelowFloorSeconds++
                if (consecutiveBelowFloorSeconds >= belowFloorRequiredSeconds) {
                    consecutiveBelowFloorSeconds = 0
                    state = ErgState.CADENCE_FLOOR_BAILOUT
                    integralError = 0.0
                    consecutiveRecoverySeconds = 0
                    lastTargetWatts = null
                    // AGENTS.md §1: emergency bailout must bypass de-duplication caching and
                    // always dispatch so the bike actuates even if cache already reads recovery.
                    lastCommandedResistance = recoveryResistance

                    return ErgDecision(
                        state = state,
                        targetResistance = recoveryResistance,
                        shouldSendBleCommand = true,
                        smoothedCadence = smoothedCadence,
                        nominalResistance = recoveryResistance,
                        trimOffset = 0,
                        powerErrorWatts = effectiveTargetWatts - actualWatts,
                        consecutiveRecoverySeconds = 0,
                        effectiveTargetWatts = effectiveTargetWatts,
                        isHrCapped = isCriticalHr
                    )
                }
            }
        }

        // Case 4: In Cadence Floor Bailout -> verify recovery gate
        if (state == ErgState.CADENCE_FLOOR_BAILOUT) {
            if (rawCadence >= recoveryThresholdRpm) {
                consecutiveRecoverySeconds++
                if (consecutiveRecoverySeconds >= recoveryRequiredSeconds) {
                    // Recovered! Transition back to ACTIVE ERG
                    state = ErgState.ACTIVE
                    integralError = 0.0
                    consecutiveRecoverySeconds = 0
                    lastTargetWatts = null
                }
            } else {
                // Cadence dipped below recovery threshold; reset gate counter
                consecutiveRecoverySeconds = 0
            }

            if (state == ErgState.CADENCE_FLOOR_BAILOUT) {
                // Still in bailout: hardware was already actuated unconditionally on entry
                // (Case 3). Suppress redundant re-sends to save BLE traffic / motor wear.
                val shouldSend = lastCommandedResistance != recoveryResistance
                lastCommandedResistance = recoveryResistance
                return ErgDecision(
                    state = state,
                    targetResistance = recoveryResistance,
                    shouldSendBleCommand = shouldSend,
                    smoothedCadence = smoothedCadence,
                    nominalResistance = recoveryResistance,
                    trimOffset = 0,
                    powerErrorWatts = effectiveTargetWatts - actualWatts,
                    consecutiveRecoverySeconds = consecutiveRecoverySeconds,
                    effectiveTargetWatts = effectiveTargetWatts,
                    isHrCapped = isCriticalHr
                )
            }
        }

        // Case 5: Normal Active ERG mode
        state = ErgState.ACTIVE
        val errorWatts = effectiveTargetWatts - actualWatts

        // Step 1: Feedforward base resistance from authoritative Watt Table
        val nominalResistance = EchelonWattTable.resistanceFromPowerTarget(effectiveTargetWatts, smoothedCadence)

        // Step 2: 5W Deadband check (only if target power hasn't changed)
        val isSameTarget = lastTargetWatts == effectiveTargetWatts
        if (isSameTarget && abs(errorWatts) <= deadbandWatts) {
            // Power is within deadband on steady target. Retain current resistance.
            val currentRes = lastCommandedResistance.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
            return ErgDecision(
                state = state,
                targetResistance = currentRes,
                shouldSendBleCommand = false,
                smoothedCadence = smoothedCadence,
                nominalResistance = nominalResistance,
                trimOffset = (currentRes - nominalResistance).coerceIn(-MAX_TRIM, MAX_TRIM),
                powerErrorWatts = errorWatts,
                effectiveTargetWatts = effectiveTargetWatts,
                isHrCapped = isCriticalHr
            )
        }

        lastTargetWatts = effectiveTargetWatts

        // Step 3: PI Trim calculation
        val pTerm = kp * errorWatts
        integralError = (integralError + errorWatts * dtSeconds).coerceIn(-MAX_INTEGRAL_WINDUP, MAX_INTEGRAL_WINDUP)
        val iTerm = ki * integralError

        val trimOffset = (pTerm + iTerm).roundToInt().coerceIn(-MAX_TRIM, MAX_TRIM)
        val calculatedResistance = (nominalResistance + trimOffset).coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)

        val shouldSend = calculatedResistance != lastCommandedResistance
        lastCommandedResistance = calculatedResistance

        return ErgDecision(
            state = state,
            targetResistance = calculatedResistance,
            shouldSendBleCommand = shouldSend,
            smoothedCadence = smoothedCadence,
            nominalResistance = nominalResistance,
            trimOffset = trimOffset,
            powerErrorWatts = errorWatts,
            effectiveTargetWatts = effectiveTargetWatts,
            isHrCapped = isCriticalHr
        )
    }
}

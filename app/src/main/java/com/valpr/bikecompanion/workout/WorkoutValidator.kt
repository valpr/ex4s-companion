package com.valpr.bikecompanion.workout

/**
 * Structured workout validation for the form editor.
 *
 * Unlike [ZwoParser.parseSafe] (one opaque failure for imports), this returns
 * a per-field issue list so the UI can highlight offending inputs and report
 * validity *before* saving. Framework-free per AGENTS.md §8.
 */
object WorkoutValidator {

    const val MIN_DURATION_SECONDS = 5
    const val MAX_DURATION_SECONDS = 7200
    const val MAX_TOTAL_SECONDS = 21600
    const val MAX_POWER_FRACTION = 2.0f
    const val MIN_CADENCE_RPM = 40
    const val MAX_CADENCE_RPM = 140

    /** Peak intensity above this warns: resistance 1..32 may not deliver it at high FTP. */
    const val ERG_REACH_FRACTION = 1.35f

    /** TSS above this warns that the session is very demanding. */
    const val HIGH_TSS = 100.0

    enum class Field {
        NAME,
        SEGMENTS,
        TOTAL,
        DURATION,
        POWER,
        POWER_LOW,
        POWER_HIGH,
        CADENCE,
        CUES
    }

    data class ValidationIssue(
        /** Null for workout-level issues (name, empty, total). */
        val segmentIndex: Int?,
        val field: Field,
        val message: String,
        val isError: Boolean
    )

    fun validate(workout: Workout): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (workout.name.isBlank()) {
            issues.add(ValidationIssue(null, Field.NAME, "Workout name is required", true))
        }
        if (workout.segments.isEmpty()) {
            issues.add(ValidationIssue(null, Field.SEGMENTS, "Add at least one segment", true))
            return issues
        }

        workout.segments.forEachIndexed { index, segment ->
            if (segment.durationSeconds < MIN_DURATION_SECONDS ||
                segment.durationSeconds > MAX_DURATION_SECONDS
            ) {
                issues.add(
                    ValidationIssue(
                        index,
                        Field.DURATION,
                        "Duration must be $MIN_DURATION_SECONDS–$MAX_DURATION_SECONDS s",
                        true
                    )
                )
            }
            when (segment) {
                is WorkoutSegment.Warmup -> {
                    issues.addAll(checkRampPowers(index, segment.powerLow, segment.powerHigh, checkOrder = true))
                }
                is WorkoutSegment.Cooldown -> {
                    // Cooldowns ramp down: start (PowerLow) exceeds end (PowerHigh) by design.
                    issues.addAll(checkRampPowers(index, segment.powerLow, segment.powerHigh, checkOrder = false))
                }
                is WorkoutSegment.Ramp -> {
                    // Ramps are directional: descending (e.g. 90%→50%) is legal,
                    // so only the range is checked, never the order.
                    issues.addAll(checkRampPowers(index, segment.powerLow, segment.powerHigh, checkOrder = false))
                }
                is WorkoutSegment.SteadyState -> {
                    checkPower(index, Field.POWER, segment.power)?.let(issues::add)
                }
                is WorkoutSegment.FreeRide,
                is WorkoutSegment.MaxEffort -> Unit
            }
            segment.targetCadence?.let { cadence ->
                if (cadence < MIN_CADENCE_RPM || cadence > MAX_CADENCE_RPM) {
                    issues.add(
                        ValidationIssue(
                            index,
                            Field.CADENCE,
                            "Cadence must be $MIN_CADENCE_RPM–$MAX_CADENCE_RPM RPM or empty",
                            true
                        )
                    )
                }
            }
        }

        if (workout.totalDurationSeconds > MAX_TOTAL_SECONDS) {
            issues.add(
                ValidationIssue(
                    null,
                    Field.TOTAL,
                    "Total duration exceeds 6 hours",
                    true
                )
            )
        }

        // ERG-disabled segments (FreeRide/MaxEffort) are rider-controlled, so only
        // ERG-driven segments count toward the hardware-reach warning.
        val ergSegments = workout.segments.filter { it.isErgEnabled }
        val peak = ergSegments.maxOfOrNull { it.averageIntensityFactor } ?: 0f
        if (peak > ERG_REACH_FRACTION) {
            issues.add(
                ValidationIssue(
                    null,
                    Field.TOTAL,
                    "Peak intensity is very high and may exceed resistance 32 at high FTP",
                    false
                )
            )
        }
        if (workout.estimatedTss > HIGH_TSS) {
            issues.add(
                ValidationIssue(
                    null,
                    Field.TOTAL,
                    "TSS %.0f is very demanding".format(workout.estimatedTss),
                    false
                )
            )
        }
        return issues
    }

    /** True when the workout can be saved (no errors; warnings allowed). */
    fun isValid(workout: Workout): Boolean = validate(workout).none { it.isError }

    private fun checkPower(index: Int, field: Field, power: Float): ValidationIssue? {
        if (power < 0.01f || power > MAX_POWER_FRACTION) {
            return ValidationIssue(
                index,
                field,
                "Power must be 1–200% FTP",
                true
            )
        }
        return null
    }

    private fun checkRampPowers(index: Int, low: Float, high: Float, checkOrder: Boolean): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        checkPower(index, Field.POWER_LOW, low)?.let(issues::add)
        checkPower(index, Field.POWER_HIGH, high)?.let(issues::add)
        if (checkOrder && low > high) {
            issues.add(
                ValidationIssue(
                    index,
                    Field.POWER_LOW,
                    "Low power must not exceed high power",
                    true
                )
            )
        }
        return issues
    }
}

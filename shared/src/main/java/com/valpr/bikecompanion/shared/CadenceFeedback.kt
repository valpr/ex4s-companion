package com.valpr.bikecompanion.shared

/**
 * Cadence evaluation status relative to workout target or endurance range.
 */
enum class CadenceState {
    STOPPED,
    ON_TARGET,
    TOO_SLOW,
    TOO_FAST
}

/**
 * Evaluation result for live cadence feedback.
 *
 * Framework-free so it stays plain-JUnit testable (AGENTS.md §8).
 */
data class CadenceEvaluation(
    val state: CadenceState,
    val displayLabel: String,
    /**
     * Semantic color token mapped by the UI layer:
     * - "GREEN": In target / optimal cadence zone
     * - "AMBER": Too slow (< target or grinding)
     * - "CYAN": Fast / easing up (> target or high cadence)
     * - "MUTED": Stopped / stationary
     */
    val colorToken: String
)

object CadenceEvaluator {
    const val DEFAULT_PREFERRED_CADENCE = 85
    const val TARGET_TOLERANCE_LOW_RPM = 5
    const val TARGET_TOLERANCE_HIGH_RPM = 8

    /**
     * Evaluates cadence feedback given current RPM, optional workout segment target,
     * and rider's preferred cadence.
     *
     * @param actualCadence Live RPM from bike telemetry.
     * @param targetCadence Structured segment target RPM (null for Free Ride or untargeted segments).
     * @param preferredCadence Rider's configured preferred cadence (defaults to 85).
     */
    fun evaluate(
        actualCadence: Int,
        targetCadence: Int?,
        preferredCadence: Int = DEFAULT_PREFERRED_CADENCE
    ): CadenceEvaluation {
        if (actualCadence <= 0) {
            val label = if (targetCadence != null) {
                "TARGET $targetCadence"
            } else {
                freeRideOptimalLabel(preferredCadence)
            }
            return CadenceEvaluation(
                state = CadenceState.STOPPED,
                displayLabel = label,
                colorToken = "MUTED"
            )
        }

        if (targetCadence != null) {
            val delta = actualCadence - targetCadence
            return when {
                delta < -TARGET_TOLERANCE_LOW_RPM -> {
                    CadenceEvaluation(
                        state = CadenceState.TOO_SLOW,
                        displayLabel = "▲ SPIN UP ($targetCadence)",
                        colorToken = "AMBER"
                    )
                }
                delta > TARGET_TOLERANCE_HIGH_RPM -> {
                    CadenceEvaluation(
                        state = CadenceState.TOO_FAST,
                        displayLabel = "▼ EASE UP ($targetCadence)",
                        colorToken = "CYAN"
                    )
                }
                else -> {
                    CadenceEvaluation(
                        state = CadenceState.ON_TARGET,
                        displayLabel = "TARGET $targetCadence",
                        colorToken = "GREEN"
                    )
                }
            }
        } else {
            // Free Ride / Unstructured guidance around preferred cadence
            val lowerIdeal = (preferredCadence - 10).coerceAtLeast(65)
            val upperIdeal = (preferredCadence + 10).coerceAtMost(105)
            val optimalLabel = freeRideOptimalLabel(preferredCadence)

            return when {
                actualCadence < lowerIdeal -> {
                    CadenceEvaluation(
                        state = CadenceState.TOO_SLOW,
                        displayLabel = "▲ SPIN UP (~$preferredCadence)",
                        colorToken = "AMBER"
                    )
                }
                actualCadence > upperIdeal -> {
                    CadenceEvaluation(
                        state = CadenceState.TOO_FAST,
                        displayLabel = "▼ FAST (~$preferredCadence)",
                        colorToken = "CYAN"
                    )
                }
                else -> {
                    CadenceEvaluation(
                        state = CadenceState.ON_TARGET,
                        displayLabel = optimalLabel,
                        colorToken = "GREEN"
                    )
                }
            }
        }
    }

    private fun freeRideOptimalLabel(preferredCadence: Int): String = if (preferredCadence in 80..90) {
        "IDEAL 80–90"
    } else {
        "IDEAL ~$preferredCadence"
    }
}

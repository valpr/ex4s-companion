package com.valpr.bikecompanion.ui.athletestats

/**
 * Pure helper containing titles, descriptions, and tooltip explanations for the
 * Advanced Engine Tuning parameters in Athlete Stats.
 */
object EngineTuningHelp {
    const val ENGINE_TUNING_TITLE = "Advanced Engine Tuning"
    const val ENGINE_TUNING_TOOLTIP =
        "Fine-tunes the ERG mode closed-loop resistance controller and anti-spiral safety bailouts. Controls how quickly resistance adjusts to target watts and when it drops to prevent pedal stall."
    const val ENGINE_TUNING_CONTENT_DESC = "Advanced Engine Tuning info"

    const val KP_TITLE = "Proportional Gain (Kp)"
    const val KP_TOOLTIP =
        "Proportional Gain (Kp): Controls how aggressively resistance adjusts to immediate differences between target and actual wattage. Higher values react faster to power changes but can induce resistance oscillation. Lower values produce smoother, more gradual corrections."
    const val KP_CONTENT_DESC = "Proportional Gain info"

    const val KI_TITLE = "Integral Gain (Ki)"
    const val KI_TOOLTIP =
        "Integral Gain (Ki): Accumulates steady-state power error over time to eliminate persistent wattage offsets. Higher values bring actual power closer to target wattage over sustained intervals, while lower values reduce overshoot."
    const val KI_CONTENT_DESC = "Integral Gain info"

    const val CADENCE_FLOOR_TITLE = "Cadence Floor"
    const val CADENCE_FLOOR_TOOLTIP =
        "Cadence Floor: Anti-spiral safety threshold. If cadence stays below this RPM for 2 consecutive seconds, ERG mode suspends and drops resistance to level 8 to prevent pedal lockup ('spiral of death'). Brief dips are ignored."
    const val CADENCE_FLOOR_CONTENT_DESC = "Cadence Floor info"

    const val RECOVERY_THRESHOLD_TITLE = "Recovery Threshold"
    const val RECOVERY_THRESHOLD_TOOLTIP =
        "Recovery Threshold: Cadence required to resume ERG mode following a bailout. You must maintain pedaling at or above this RPM for 3 consecutive seconds before target resistance re-engages."
    const val RECOVERY_THRESHOLD_CONTENT_DESC = "Recovery Threshold info"
}

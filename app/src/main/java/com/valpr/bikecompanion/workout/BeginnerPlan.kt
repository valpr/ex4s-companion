package com.valpr.bikecompanion.workout

/**
 * Graduated beginner path for riders who are extremely new to biking.
 *
 * Design principles (sports-science conservative):
 * - Short durations first (15 → 20 → 25 → 30 min) so time-in-saddle adapts.
 * - Low peak intensity (55% → 60% → 65% → 70% FTP); nothing above Tempo.
 * - Easy cadence targets (70–80 RPM); new riders cannot comfortably hold 90.
 * - Level 1 includes a short [WorkoutSegment.FreeRide] so the rider learns
 *   manual electronic shifting before longer ERG blocks.
 * - Heavy coaching <textevent> cues: form, breathing, RPE, ERG behavior,
 *   and "The Clutch" bailout reassurance.
 *
 * Progression rule (see [PACING_GUIDANCE]): 2–3 rides per week with a rest
 * day between; do each level at least twice and move on when it can be
 * finished while still talking in the easy parts. After Level 4 the rider
 * graduates to the existing "Sweet Spot Intervals (30 min)" library workout.
 *
 * This object is deliberately framework-free so recommendation logic stays
 * plain-JUnit testable (AGENTS.md §8).
 */
object BeginnerPlan {

    data class BeginnerLevel(
        val level: Int,
        val filename: String,
        val title: String,
        val goal: String,
        val focus: String
    )

    val LEVELS: List<BeginnerLevel> = listOf(
        BeginnerLevel(
            level = 1,
            filename = "beginner_01_first_pedals.zwo",
            title = "First Pedals (15 min)",
            goal = "Do at least twice; finish talking comfortably and learn how ERG controls the bike.",
            focus = "Easy spinning + 2-min shifting practice"
        ),
        BeginnerLevel(
            level = 2,
            filename = "beginner_02_building_rhythm.zwo",
            title = "Building Rhythm (20 min)",
            goal = "Do at least twice without stopping; hold one steady rhythm.",
            focus = "Longer steady blocks at 55–60% FTP"
        ),
        BeginnerLevel(
            level = 3,
            filename = "beginner_03_steady_confidence.zwo",
            title = "Steady Confidence (25 min)",
            goal = "Do at least twice; complete both 6-min pushes at 65% FTP.",
            focus = "Intro to repeatable efforts + recoveries"
        ),
        BeginnerLevel(
            level = 4,
            filename = "beginner_04_ready_for_more.zwo",
            title = "Ready for More (30 min)",
            goal = "Do at least twice strong; complete 3 x 5 min at 65–70% FTP.",
            focus = "Graduation ride → Sweet Spot next"
        )
    )

    /** Filename of the graduation target already seeded in the library. */
    const val GRADUATION_FILENAME = "sweet_spot_intervals.zwo"

    /**
     * How to ride the path: frequency, rest, and the single rule for moving
     * on. Rendered verbatim by the Dashboard beginner card so the guidance
     * lives in exactly one place.
     */
    const val PACING_GUIDANCE =
        "Ride 2–3 times per week with a rest day between. " +
            "Do each level at least twice — move on when you can finish it " +
            "and still talk in the easy parts."

    const val pacingGuidance = PACING_GUIDANCE

    /** Rough timeline at the recommended frequency; shown under the path. */
    const val PACING_TIMELINE = "~4 weeks at 2–3 rides/week"

    const val pacingTimeline = PACING_TIMELINE

    fun levelForFilename(filename: String): BeginnerLevel? = LEVELS.find { it.filename.equals(filename, ignoreCase = true) }

    fun isBeginnerWorkout(filename: String): Boolean = levelForFilename(filename) != null

    /**
     * Returns the next recommended level given the set of completed beginner
     * filenames. Pure function of the completed set:
     * - Empty / none completed → Level 1.
     * - Otherwise the first level not yet completed (allows skipping ahead
     *   without getting stuck).
     * - All completed → Level 4 (repeat / consolidate) so the UI can suggest
     *   graduation to Sweet Spot alongside it.
     */
    fun recommendNext(completedFilenames: Set<String>): BeginnerLevel {
        val normalized = completedFilenames.map { it.lowercase() }.toSet()
        return LEVELS.firstOrNull { it.filename.lowercase() !in normalized }
            ?: LEVELS.last()
    }

    /**
     * True when the rider has finished the whole path and should be pointed
     * at [GRADUATION_FILENAME].
     */
    fun hasGraduated(completedFilenames: Set<String>): Boolean {
        val normalized = completedFilenames.map { it.lowercase() }.toSet()
        return LEVELS.all { it.filename.lowercase() in normalized }
    }
}

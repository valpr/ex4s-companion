package com.valpr.bikecompanion.history

import com.valpr.bikecompanion.workout.BeginnerPlan

/**
 * Resolves the source `.zwo` filename for a completed structured workout.
 *
 * [Workout] carries no filename on older sessions, so bundled samples are
 * matched by display-name prefix (`"First Pedals (15 min) - Beginner 1/4"`
 * starts with the level title) guarded by duration: attempts shorter than
 * half the expected length neither match nor count (quits and coincidentally
 * same-named custom rides stay unattributed). Unknown workouts return null
 * (still persisted, just unattributed). Framework-free and plain-JUnit testable.
 */
object BeginnerFilenameMatcher {
    /** Expected full durations in seconds for bundled workouts. */
    private val expectedDurationsSeconds: Map<String, Int> = buildMap {
        // Beginner levels: 15 / 20 / 25 / 30 min.
        BeginnerPlan.LEVELS.forEachIndexed { index, level ->
            put(level.filename.lowercase(), (15 + index * 5) * 60)
        }
        put(BeginnerPlan.GRADUATION_FILENAME.lowercase(), 2040)
        put("ftp_ramp_test.zwo", 1320)
    }

    fun filenameFor(workoutName: String, totalDurationSeconds: Int): String? {
        val level = BeginnerPlan.LEVELS.firstOrNull { workoutName.startsWith(it.title) }
        if (level != null) {
            return level.filename.takeIf { completesAttempt(it, totalDurationSeconds) }
        }
        if (workoutName.startsWith("Sweet Spot Intervals")) {
            return BeginnerPlan.GRADUATION_FILENAME.takeIf {
                completesAttempt(it, totalDurationSeconds)
            }
        }
        if (workoutName.startsWith("FTP Ramp Test")) {
            return "ftp_ramp_test.zwo".takeIf { completesAttempt(it, totalDurationSeconds) }
        }
        return null
    }

    private fun completesAttempt(filename: String, totalDurationSeconds: Int): Boolean {
        val expected = expectedDurationsSeconds[filename.lowercase()] ?: return true
        return totalDurationSeconds >= expected / 2
    }
}

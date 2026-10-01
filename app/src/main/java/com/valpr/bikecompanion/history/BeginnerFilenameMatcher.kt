package com.valpr.bikecompanion.history

import com.valpr.bikecompanion.workout.BeginnerPlan

/**
 * Resolves the source `.zwo` filename for a completed structured workout.
 *
 * [Workout] carries no filename, so bundled samples are matched by display-name
 * prefix (`"First Pedals (15 min) - Beginner 1/4"` starts with the level title)
 * plus duration as a guard against user imports that happen to share a prefix.
 * Unknown / user-imported workouts return null (still persisted, just unattributed).
 * Framework-free and plain-JUnit testable.
 */
object BeginnerFilenameMatcher {
    fun filenameFor(workoutName: String, totalDurationSeconds: Int): String? {
        val level = BeginnerPlan.LEVELS.firstOrNull { workoutName.startsWith(it.title) }
        if (level != null) {
            return level.filename
        }
        if (workoutName.startsWith("Sweet Spot Intervals")) {
            return BeginnerPlan.GRADUATION_FILENAME
        }
        if (workoutName.startsWith("FTP Ramp Test")) {
            return "ftp_ramp_test.zwo"
        }
        return null
    }
}

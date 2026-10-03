package com.valpr.bikecompanion.workout

/**
 * Pure presentation helper for workout metadata display (AGENTS.md §8).
 */
object WorkoutPresentation {

    /**
     * Formats the author string for display in workout cards, search, and preview sheets.
     * Bundled/default workouts (authored as "Default" or legacy "Echelon Companion")
     * are displayed simply as "Default" rather than "By Default" / "By Echelon Companion".
     * Other authors (e.g. "Zwift", "Coach Jack") are displayed with "By ".
     */
    fun formatAuthor(author: String): String {
        val trimmed = author.trim()
        if (trimmed.isEmpty() || trimmed.equals("By", ignoreCase = true)) return ""
        val clean = if (trimmed.startsWith("By ", ignoreCase = true)) {
            trimmed.substring(3).trim()
        } else {
            trimmed
        }
        if (clean.isEmpty() || clean.equals("By", ignoreCase = true)) return ""
        if (clean.equals("Default", ignoreCase = true) ||
            clean.equals("Echelon Companion", ignoreCase = true)
        ) {
            return "Default"
        }
        return "By $clean"
    }
}

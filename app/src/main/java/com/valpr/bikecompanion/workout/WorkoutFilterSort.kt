package com.valpr.bikecompanion.workout

/**
 * Standard indoor cycling taxonomy tags used across bundled workouts and suggestions.
 */
object WorkoutTags {
    val CANONICAL: List<String> = listOf(
        "Endurance",
        "SweetSpot",
        "Threshold",
        "VO2Max",
        "HIIT",
        "Tabata",
        "Climb",
        "Strength",
        "Recovery",
        "Beginner",
        "Test"
    )
}

/**
 * Available ordering criteria for the workout library.
 */
enum class WorkoutSortOption(val displayName: String) {
    RECENTLY_MODIFIED("Recently Added"),
    DURATION_ASC("Duration (Shortest)"),
    DURATION_DESC("Duration (Longest)"),
    TSS_ASC("Intensity (Lowest TSS)"),
    TSS_DESC("Intensity (Highest TSS)"),
    NAME_ASC("Name (A–Z)"),
    RECENTLY_RIDDEN("Recently Ridden")
}

/**
 * Quick duration brackets for filtering workouts by available training time.
 */
enum class WorkoutDurationBracket(val displayName: String) {
    ALL("Any Duration"),
    UNDER_30("< 30 min"),
    THIRTY_TO_45("30–45 min"),
    FORTY_FIVE_TO_60("45–60 min"),
    OVER_60("60+ min");

    fun matches(durationSeconds: Int): Boolean = when (this) {
        ALL -> true
        UNDER_30 -> durationSeconds in 1 until 1800
        THIRTY_TO_45 -> durationSeconds in 1800 until 2700
        FORTY_FIVE_TO_60 -> durationSeconds in 2700 until 3600
        OVER_60 -> durationSeconds >= 3600
    }
}

/**
 * Represents a tag and the number of workouts in the library carrying it.
 */
data class TagCount(val tag: String, val count: Int)

/**
 * Pure helper for filtering, sorting, and tag aggregation (AGENTS.md §8).
 * Framework-free so logic stays plain-JUnit testable.
 */
object WorkoutFilterSortHelper {

    /**
     * Aggregates and counts tags across [workouts], returning them ordered by
     * frequency descending, then alphabetically. De-duplicates case-insensitively
     * mapping to canonical or first-seen casing.
     */
    fun extractTagCounts(workouts: List<CachedWorkoutHeader>): List<TagCount> {
        val countMap = linkedMapOf<String, Int>()
        val canonicalDisplayMap = linkedMapOf<String, String>() // lowercase -> display tag

        for (w in workouts) {
            for (t in w.tags) {
                val trimmed = t.trim()
                if (trimmed.isEmpty()) continue
                val lower = trimmed.lowercase(java.util.Locale.ROOT)
                val displayKey = canonicalDisplayMap.getOrPut(lower) {
                    WorkoutTags.CANONICAL.find { it.equals(trimmed, ignoreCase = true) } ?: trimmed
                }
                countMap[displayKey] = (countMap[displayKey] ?: 0) + 1
            }
        }
        return countMap.entries
            .map { TagCount(it.key, it.value) }
            .sortedWith(
                compareByDescending<TagCount> { it.count }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.tag }
            )
    }

    /**
     * Extracts all unique tags from [workouts] and any [extraTags], de-duplicated
     * case-insensitively, with [canonicalTags] establishing preferred casing,
     * sorted alphabetically.
     */
    fun extractUniqueTags(
        workouts: List<CachedWorkoutHeader>,
        extraTags: List<String> = emptyList(),
        canonicalTags: List<String> = WorkoutTags.CANONICAL
    ): List<String> {
        val seen = linkedMapOf<String, String>()
        // Seed canonical first so canonical casing takes precedence
        for (tag in canonicalTags) {
            val trimmed = tag.trim()
            if (trimmed.isNotEmpty()) {
                seen.putIfAbsent(trimmed.lowercase(), trimmed)
            }
        }
        for (tag in extraTags) {
            val trimmed = tag.trim()
            if (trimmed.isNotEmpty()) {
                seen.putIfAbsent(trimmed.lowercase(), trimmed)
            }
        }
        for (w in workouts) {
            for (tag in w.tags) {
                val trimmed = tag.trim()
                if (trimmed.isNotEmpty()) {
                    seen.putIfAbsent(trimmed.lowercase(), trimmed)
                }
            }
        }
        return seen.values.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Filters [workouts] by [tagFilter], [durationBracket], and [searchQuery],
     * and sorts the result according to [sortOption].
     *
     * Workouts whose filenames match [favoriteFilenames] are pinned at the top,
     * maintaining relative sort order within both the pinned and non-pinned groups.
     * Tie-breaks on secondary keys (name, lastModified) for deterministic ordering.
     */
    fun filterAndSort(
        workouts: List<CachedWorkoutHeader>,
        tagFilter: String? = null,
        durationBracket: WorkoutDurationBracket = WorkoutDurationBracket.ALL,
        searchQuery: String? = null,
        sortOption: WorkoutSortOption = WorkoutSortOption.RECENTLY_MODIFIED,
        favoriteFilenames: Set<String> = emptySet(),
        recentRidesMap: Map<String, Long> = emptyMap()
    ): List<CachedWorkoutHeader> {
        // 1. Tag filtering
        val tagMatched = if (tagFilter.isNullOrBlank() || tagFilter.equals("All", ignoreCase = true)) {
            workouts
        } else {
            workouts.filter { w ->
                w.tags.any { it.equals(tagFilter, ignoreCase = true) }
            }
        }

        // 2. Duration bracket filtering
        val durationMatched = if (durationBracket == WorkoutDurationBracket.ALL) {
            tagMatched
        } else {
            tagMatched.filter { durationBracket.matches(it.durationSeconds) }
        }

        // 3. Search query filtering (multi-token search, case-insensitive)
        val searchMatched = if (searchQuery.isNullOrBlank()) {
            durationMatched
        } else {
            val tokens = searchQuery.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            durationMatched.filter { w ->
                tokens.all { token ->
                    w.name.contains(token, ignoreCase = true) ||
                        w.author.contains(token, ignoreCase = true) ||
                        w.description.contains(token, ignoreCase = true) ||
                        w.tags.any { it.contains(token, ignoreCase = true) }
                }
            }
        }

        // 4. Primary sort comparator with deterministic tie-breakers
        val sortComparator = when (sortOption) {
            WorkoutSortOption.RECENTLY_MODIFIED -> {
                compareByDescending<CachedWorkoutHeader> { it.lastModifiedMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.filename }
            }
            WorkoutSortOption.DURATION_ASC -> {
                compareBy<CachedWorkoutHeader> { it.durationSeconds }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.filename }
            }
            WorkoutSortOption.DURATION_DESC -> {
                compareByDescending<CachedWorkoutHeader> { it.durationSeconds }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.filename }
            }
            WorkoutSortOption.TSS_ASC -> {
                compareBy<CachedWorkoutHeader> { it.estimatedTss }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.filename }
            }
            WorkoutSortOption.TSS_DESC -> {
                compareByDescending<CachedWorkoutHeader> { it.estimatedTss }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.filename }
            }
            WorkoutSortOption.NAME_ASC -> {
                compareBy<CachedWorkoutHeader, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenByDescending { it.lastModifiedMs }
                    .thenBy { it.filename }
            }
            WorkoutSortOption.RECENTLY_RIDDEN -> {
                compareByDescending<CachedWorkoutHeader> { recentRidesMap[it.filename.lowercase()] ?: 0L }
                    .thenByDescending { it.lastModifiedMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.filename }
            }
        }

        // 5. Partition by favorites to pin starred items at the top
        val normalizedFavs = favoriteFilenames.map { it.lowercase() }.toSet()
        val (favorites, nonFavorites) = searchMatched.partition {
            normalizedFavs.contains(it.filename.lowercase())
        }

        return favorites.sortedWith(sortComparator) + nonFavorites.sortedWith(sortComparator)
    }
}

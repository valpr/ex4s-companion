package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.CachedWorkoutHeader
import com.valpr.bikecompanion.workout.WorkoutFilterSortHelper
import com.valpr.bikecompanion.workout.WorkoutSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutFilterSortTest {

    private val sampleWorkouts = listOf(
        CachedWorkoutHeader(
            filename = "endurance_60.zwo",
            name = "Aerobic Base (60 min)",
            author = "Echelon",
            description = "Long ride",
            durationSeconds = 3600,
            estimatedTss = 40.0,
            fileSizeBytes = 1000L,
            lastModifiedMs = 100L,
            tags = listOf("Endurance", "FatBurn")
        ),
        CachedWorkoutHeader(
            filename = "hiit_30.zwo",
            name = "HIIT Intervals (30 min)",
            author = "Echelon",
            description = "Hard intervals",
            durationSeconds = 1800,
            estimatedTss = 25.0,
            fileSizeBytes = 1000L,
            lastModifiedMs = 300L,
            tags = listOf("HIIT", "Intervals")
        ),
        CachedWorkoutHeader(
            filename = "recovery_20.zwo",
            name = "Low Impact Recovery (20 min)",
            author = "Echelon",
            description = "Easy spin",
            durationSeconds = 1200,
            estimatedTss = 8.0,
            fileSizeBytes = 1000L,
            lastModifiedMs = 200L,
            tags = listOf("Recovery", "LowImpact")
        ),
        CachedWorkoutHeader(
            filename = "climb_30.zwo",
            name = "Climb Strength (30 min)",
            author = "Echelon",
            description = "Seated climb",
            durationSeconds = 1800,
            estimatedTss = 22.0,
            fileSizeBytes = 1000L,
            lastModifiedMs = 150L,
            tags = listOf("Climb", "Strength")
        )
    )

    @Test
    fun filter_withNullOrAll_returnsAllWorkouts() {
        val resultNull = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = null,
            sortOption = WorkoutSortOption.NAME_ASC
        )
        assertEquals(4, resultNull.size)

        val resultAll = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = "All",
            sortOption = WorkoutSortOption.NAME_ASC
        )
        assertEquals(4, resultAll.size)
    }

    @Test
    fun filter_byTag_isCaseInsensitive() {
        val lowerResult = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = "hiit",
            sortOption = WorkoutSortOption.NAME_ASC
        )
        assertEquals(1, lowerResult.size)
        assertEquals("hiit_30.zwo", lowerResult[0].filename)

        val upperResult = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = "HIIT",
            sortOption = WorkoutSortOption.NAME_ASC
        )
        assertEquals(1, upperResult.size)
        assertEquals("hiit_30.zwo", upperResult[0].filename)
    }

    @Test
    fun filter_byUnknownTag_returnsEmpty() {
        val result = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = "Sprint",
            sortOption = WorkoutSortOption.NAME_ASC
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun sort_byDurationAscending_tieBreaksByName() {
        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = null,
            sortOption = WorkoutSortOption.DURATION_ASC
        )
        // Expected durations: 1200, 1800 ("Climb Strength"), 1800 ("HIIT Intervals"), 3600
        assertEquals("recovery_20.zwo", sorted[0].filename)
        assertEquals("climb_30.zwo", sorted[1].filename)
        assertEquals("hiit_30.zwo", sorted[2].filename)
        assertEquals("endurance_60.zwo", sorted[3].filename)
    }

    @Test
    fun sort_byDurationDescending() {
        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = null,
            sortOption = WorkoutSortOption.DURATION_DESC
        )
        assertEquals("endurance_60.zwo", sorted[0].filename)
        assertEquals("recovery_20.zwo", sorted[3].filename)
    }

    @Test
    fun sort_byTssAscending() {
        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = null,
            sortOption = WorkoutSortOption.TSS_ASC
        )
        assertEquals("recovery_20.zwo", sorted[0].filename) // TSS 8.0
        assertEquals("climb_30.zwo", sorted[1].filename) // TSS 22.0
        assertEquals("hiit_30.zwo", sorted[2].filename) // TSS 25.0
        assertEquals("endurance_60.zwo", sorted[3].filename) // TSS 40.0
    }

    @Test
    fun sort_byTssDescending() {
        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = null,
            sortOption = WorkoutSortOption.TSS_DESC
        )
        assertEquals("endurance_60.zwo", sorted[0].filename)
        assertEquals("recovery_20.zwo", sorted[3].filename)
    }

    @Test
    fun sort_byRecentlyModified() {
        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = null,
            sortOption = WorkoutSortOption.RECENTLY_MODIFIED
        )
        assertEquals("hiit_30.zwo", sorted[0].filename) // 300L
        assertEquals("recovery_20.zwo", sorted[1].filename) // 200L
        assertEquals("climb_30.zwo", sorted[2].filename) // 150L
        assertEquals("endurance_60.zwo", sorted[3].filename) // 100L
    }

    @Test
    fun sort_byNameAscending() {
        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            tagFilter = null,
            sortOption = WorkoutSortOption.NAME_ASC
        )
        assertEquals("endurance_60.zwo", sorted[0].filename) // Aerobic Base...
        assertEquals("climb_30.zwo", sorted[1].filename) // Climb Strength...
        assertEquals("hiit_30.zwo", sorted[2].filename) // HIIT Intervals...
        assertEquals("recovery_20.zwo", sorted[3].filename) // Low Impact...
    }

    @Test
    fun extractTagCounts_countsAndOrdersByFrequencyThenAlphabetical() {
        val withDuplicates = sampleWorkouts + CachedWorkoutHeader(
            filename = "climb_45.zwo",
            name = "Climb 45",
            author = "",
            description = "",
            durationSeconds = 2700,
            estimatedTss = 30.0,
            fileSizeBytes = 1000L,
            lastModifiedMs = 50L,
            tags = listOf("Climb")
        )

        val counts = WorkoutFilterSortHelper.extractTagCounts(withDuplicates)
        // Climb has 2 occurrences, others have 1
        assertEquals("Climb", counts[0].tag)
        assertEquals(2, counts[0].count)

        // Remaining counts with count=1 are sorted alphabetically
        val count1Tags = counts.filter { it.count == 1 }.map { it.tag }
        assertEquals(count1Tags.sortedWith(String.CASE_INSENSITIVE_ORDER), count1Tags)
    }

    @Test
    fun extractUniqueTags_mergesWithCanonicalAndDeDuplicatesCaseInsensitively() {
        val tags = WorkoutFilterSortHelper.extractUniqueTags(
            sampleWorkouts,
            extraTags = listOf("endurance", "CustomTag")
        )
        // Should contain canonical casing "Endurance", not duplicate "endurance"
        assertTrue(tags.contains("Endurance"))
        assertTrue(tags.contains("CustomTag"))
        assertEquals(tags.distinctBy { it.lowercase() }.size, tags.size)
    }

    @Test
    fun filter_byDurationBracket() {
        // UNDER_30 (< 1800s): recovery_20 (1200s)
        val under30 = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            durationBracket = com.valpr.bikecompanion.workout.WorkoutDurationBracket.UNDER_30
        )
        assertEquals(1, under30.size)
        assertEquals("recovery_20.zwo", under30[0].filename)

        // THIRTY_TO_45 (1800..2700s): hiit_30 (1800s), climb_30 (1800s)
        val thirtyTo45 = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            durationBracket = com.valpr.bikecompanion.workout.WorkoutDurationBracket.THIRTY_TO_45
        )
        assertEquals(2, thirtyTo45.size)

        // OVER_60 (>= 3600s): endurance_60 is exactly 3600s (60 min)
        val over60 = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            durationBracket = com.valpr.bikecompanion.workout.WorkoutDurationBracket.OVER_60
        )
        assertEquals(1, over60.size)
        assertEquals("endurance_60.zwo", over60[0].filename)
    }

    @Test
    fun durationBracket_boundaryConditions() {
        val bUnder30 = com.valpr.bikecompanion.workout.WorkoutDurationBracket.UNDER_30
        val b30to45 = com.valpr.bikecompanion.workout.WorkoutDurationBracket.THIRTY_TO_45
        val b45to60 = com.valpr.bikecompanion.workout.WorkoutDurationBracket.FORTY_FIVE_TO_60
        val bOver60 = com.valpr.bikecompanion.workout.WorkoutDurationBracket.OVER_60

        // < 30 min (1 until 1800)
        assertTrue(bUnder30.matches(1799))
        assertFalse(bUnder30.matches(1800))

        // 30–45 min (1800 until 2700)
        assertTrue(b30to45.matches(1800))
        assertTrue(b30to45.matches(2699))
        assertFalse(b30to45.matches(2700))

        // 45–60 min (2700 until 3600)
        assertTrue(b45to60.matches(2700))
        assertTrue(b45to60.matches(3599))
        assertFalse(b45to60.matches(3600))

        // 60+ min (>= 3600)
        assertTrue(bOver60.matches(3600))
        assertTrue(bOver60.matches(5400))
        assertFalse(bOver60.matches(3599))
    }

    @Test
    fun extractTagCounts_nonCanonicalCaseInsensitive_aggregatesCorrectly() {
        val workouts = listOf(
            CachedWorkoutHeader(
                filename = "w1.zwo",
                name = "W1",
                author = "A",
                description = "",
                durationSeconds = 1800,
                estimatedTss = 30.0,
                fileSizeBytes = 100L,
                lastModifiedMs = 100L,
                tags = listOf("hills", "tempo")
            ),
            CachedWorkoutHeader(
                filename = "w2.zwo",
                name = "W2",
                author = "A",
                description = "",
                durationSeconds = 1800,
                estimatedTss = 30.0,
                fileSizeBytes = 100L,
                lastModifiedMs = 100L,
                tags = listOf("Hills", "Tempo")
            )
        )

        val counts = WorkoutFilterSortHelper.extractTagCounts(workouts)
        assertEquals(2, counts.size)
        // Casing normalized, count = 2 for both
        assertEquals(2, counts.find { it.tag.equals("hills", ignoreCase = true) }?.count)
        assertEquals(2, counts.find { it.tag.equals("tempo", ignoreCase = true) }?.count)
    }

    @Test
    fun filter_bySearchQuery_matchesNameAuthorDescriptionAndTags() {
        // Matches name "Aerobic Base"
        val byName = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            searchQuery = "aerobic"
        )
        assertEquals(1, byName.size)
        assertEquals("endurance_60.zwo", byName[0].filename)

        // Matches description "Easy spin"
        val byDesc = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            searchQuery = "easy spin"
        )
        assertEquals(1, byDesc.size)
        assertEquals("recovery_20.zwo", byDesc[0].filename)

        // Matches tag "LowImpact"
        val byTag = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            searchQuery = "lowimpact"
        )
        assertEquals(1, byTag.size)
        assertEquals("recovery_20.zwo", byTag[0].filename)

        // Multi-token search across fields: "echelon aerobic" (author + name)
        val multiToken = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            searchQuery = "echelon aerobic"
        )
        assertEquals(1, multiToken.size)
        assertEquals("endurance_60.zwo", multiToken[0].filename)

        // Matches formatted author "Default" for legacy "Echelon Companion"
        val legacyDefault = listOf(
            CachedWorkoutHeader(
                filename = "legacy.zwo",
                name = "Legacy Ride",
                author = "Echelon Companion",
                description = "Legacy test",
                durationSeconds = 1800,
                estimatedTss = 30.0,
                fileSizeBytes = 500L,
                lastModifiedMs = 1000L
            )
        )
        val defaultSearch = WorkoutFilterSortHelper.filterAndSort(
            legacyDefault,
            searchQuery = "default"
        )
        assertEquals(1, defaultSearch.size)
        assertEquals("legacy.zwo", defaultSearch[0].filename)
    }

    @Test
    fun sort_withPinnedFavorites_pinsStarredWorkoutsAtTop() {
        // Without favorites, sorted by duration asc: recovery_20 (1200), climb_30 (1800), hiit_30 (1800), endurance_60 (3600)
        // Mark endurance_60 as favorite: it should be pinned at index 0 despite having longest duration!
        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            sortOption = WorkoutSortOption.DURATION_ASC,
            favoriteFilenames = setOf("endurance_60.zwo")
        )
        assertEquals("endurance_60.zwo", sorted[0].filename)
        assertEquals("recovery_20.zwo", sorted[1].filename)
        assertEquals("climb_30.zwo", sorted[2].filename)
        assertEquals("hiit_30.zwo", sorted[3].filename)
    }

    @Test
    fun sort_byRecentlyRidden() {
        val rideMap = mapOf(
            "climb_30.zwo" to 2000L,
            "hiit_30.zwo" to 5000L // Ridden most recently
        )

        val sorted = WorkoutFilterSortHelper.filterAndSort(
            sampleWorkouts,
            sortOption = WorkoutSortOption.RECENTLY_RIDDEN,
            recentRidesMap = rideMap
        )

        // HIIT 30 (5000L) first, Climb 30 (2000L) second, others (0L) follow
        assertEquals("hiit_30.zwo", sorted[0].filename)
        assertEquals("climb_30.zwo", sorted[1].filename)
    }
}

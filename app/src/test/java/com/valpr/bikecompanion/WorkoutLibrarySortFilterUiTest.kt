package com.valpr.bikecompanion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.valpr.bikecompanion.ui.dashboard.TagFilterRow
import com.valpr.bikecompanion.ui.dashboard.WorkoutItemCard
import com.valpr.bikecompanion.ui.dashboard.WorkoutLibraryHeader
import com.valpr.bikecompanion.workout.CachedWorkoutHeader
import com.valpr.bikecompanion.workout.TagCount
import com.valpr.bikecompanion.workout.WorkoutDurationBracket
import com.valpr.bikecompanion.workout.WorkoutSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutLibrarySortFilterUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun workoutLibraryHeader_displaysSortOption_andOpensDropdown() {
        var selectedOption = WorkoutSortOption.RECENTLY_MODIFIED

        composeRule.setContent {
            WorkoutLibraryHeader(
                onNewWorkout = {},
                onImportWorkout = {},
                selectedSortOption = selectedOption,
                onSelectSortOption = { selectedOption = it }
            )
        }

        // Verify header elements
        composeRule.onNodeWithText("Workout Library").assertIsDisplayed()
        composeRule.onNodeWithText("New").assertIsDisplayed()
        composeRule.onNodeWithText("Import .zwo").assertIsDisplayed()
        composeRule.onNodeWithText("Recently Added").assertIsDisplayed()

        // Open sort dropdown
        composeRule.onNodeWithText("Recently Added").performClick()

        // Select "Duration (Shortest)"
        composeRule.onNodeWithText("Duration (Shortest)").assertIsDisplayed().performClick()
        assertEquals(WorkoutSortOption.DURATION_ASC, selectedOption)
    }

    @Test
    fun tagFilterRow_rendersTagsAndTogglesSelection() {
        val tags = listOf(
            TagCount("Endurance", 3),
            TagCount("HIIT", 2)
        )

        var filterSelection by androidx.compose.runtime.mutableStateOf<String?>(null)

        composeRule.setContent {
            TagFilterRow(
                tags = tags,
                selectedTag = filterSelection,
                totalWorkoutsCount = 5,
                onSelectTag = { filterSelection = it }
            )
        }

        // All (5) is displayed
        composeRule.onNodeWithText("All (5)").assertIsDisplayed()
        composeRule.onNodeWithText("Endurance (3)").assertIsDisplayed()
        composeRule.onNodeWithText("HIIT (2)").assertIsDisplayed()

        // Select Endurance
        composeRule.onNodeWithText("Endurance (3)").performClick()
        assertEquals("Endurance", filterSelection)

        // Select All to clear filter
        composeRule.onNodeWithText("All (5)").performClick()
        assertNull(filterSelection)
    }

    @Test
    fun workoutItemCard_displaysTagBadges() {
        val header = CachedWorkoutHeader(
            filename = "sample.zwo",
            name = "Test Ride",
            author = "Coach",
            description = "Ride description",
            durationSeconds = 1800,
            estimatedTss = 30.0,
            fileSizeBytes = 500L,
            lastModifiedMs = 1000L,
            tags = listOf("Endurance", "FatBurn")
        )

        composeRule.setContent {
            WorkoutItemCard(
                header = header,
                onClick = {},
                onDelete = {}
            )
        }

        composeRule.onNodeWithText("Test Ride").assertIsDisplayed()
        composeRule.onNodeWithText("Endurance").assertIsDisplayed()
        composeRule.onNodeWithText("FatBurn").assertIsDisplayed()
    }

    @Test
    fun workoutItemCard_displaysDefaultAuthor_withoutByPrefix() {
        val header = CachedWorkoutHeader(
            filename = "sample.zwo",
            name = "Test Ride",
            author = "Default",
            description = "Ride description",
            durationSeconds = 1800,
            estimatedTss = 30.0,
            fileSizeBytes = 500L,
            lastModifiedMs = 1000L
        )

        composeRule.setContent {
            WorkoutItemCard(
                header = header,
                onClick = {},
                onDelete = {}
            )
        }

        composeRule.onNodeWithText("Default").assertIsDisplayed()
        composeRule.onNodeWithText("By Default").assertDoesNotExist()
        composeRule.onNodeWithText("By Echelon Companion").assertDoesNotExist()
    }

    @Test
    fun workoutItemCard_legacyEchelonCompanionAuthor_displaysDefault() {
        val header = CachedWorkoutHeader(
            filename = "sample.zwo",
            name = "Test Ride",
            author = "Echelon Companion",
            description = "Ride description",
            durationSeconds = 1800,
            estimatedTss = 30.0,
            fileSizeBytes = 500L,
            lastModifiedMs = 1000L
        )

        composeRule.setContent {
            WorkoutItemCard(
                header = header,
                onClick = {},
                onDelete = {}
            )
        }

        composeRule.onNodeWithText("Default").assertIsDisplayed()
        composeRule.onNodeWithText("By Echelon Companion").assertDoesNotExist()
    }

    @Test
    fun workoutLibraryHeader_searchField_updatesQuery_andClearsQuery() {
        var query by mutableStateOf("")

        composeRule.setContent {
            WorkoutLibraryHeader(
                onNewWorkout = {},
                onImportWorkout = {},
                searchQuery = query,
                onSearchQueryChange = { query = it }
            )
        }

        // Initially shows placeholder
        composeRule.onNodeWithText("Search workouts, tags, authors…").assertIsDisplayed()

        // Type search query
        composeRule.onNodeWithText("Search workouts, tags, authors…").performTextInput("FTP Builder")
        assertEquals("FTP Builder", query)

        // Clear button should be displayed and functional
        composeRule.onNodeWithContentDescription("Clear search").assertIsDisplayed().performClick()
        assertEquals("", query)
    }

    @Test
    fun workoutLibraryHeader_durationMenu_displaysOptions_andSelectsBracket() {
        var selectedBracket by mutableStateOf(WorkoutDurationBracket.ALL)

        composeRule.setContent {
            WorkoutLibraryHeader(
                onNewWorkout = {},
                onImportWorkout = {},
                selectedDurationBracket = selectedBracket,
                onSelectDurationBracket = { selectedBracket = it }
            )
        }

        // Button shows "Duration" when ALL is selected
        composeRule.onNodeWithText("Duration").assertIsDisplayed().performClick()

        // Verify dropdown items
        composeRule.onNodeWithText("Any Duration").assertIsDisplayed()
        composeRule.onNodeWithText("< 30 min").assertIsDisplayed()
        composeRule.onNodeWithText("30–45 min").assertIsDisplayed()
        composeRule.onNodeWithText("45–60 min").assertIsDisplayed()
        composeRule.onNodeWithText("60+ min").assertIsDisplayed()

        // Select "< 30 min"
        composeRule.onNodeWithText("< 30 min").performClick()
        assertEquals(WorkoutDurationBracket.UNDER_30, selectedBracket)
    }

    @Test
    fun workoutItemCard_favoriteStar_togglesFavoriteState() {
        var isFavorite by mutableStateOf(false)
        var toggleCount = 0
        val header = CachedWorkoutHeader(
            filename = "tempo.zwo",
            name = "Tempo Cadence",
            author = "Coach",
            description = "Solid tempo effort",
            durationSeconds = 2400,
            estimatedTss = 50.0,
            fileSizeBytes = 600L,
            lastModifiedMs = 2000L
        )

        composeRule.setContent {
            WorkoutItemCard(
                header = header,
                onClick = {},
                onDelete = {},
                isFavorite = isFavorite,
                onToggleFavorite = {
                    toggleCount++
                    isFavorite = !isFavorite
                }
            )
        }

        // Initially not favorited: shows "Add to favorites"
        composeRule.onNodeWithContentDescription("Add to favorites").assertIsDisplayed().performClick()
        assertEquals(1, toggleCount)
        assertTrue(isFavorite)

        // Recomposed with isFavorite = true: shows "Remove from favorites"
        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed().performClick()
        assertEquals(2, toggleCount)
        assertFalse(isFavorite)
    }

    @Test
    fun workoutItemCard_completionBadge_displaysAccurateCount() {
        var count by mutableIntStateOf(0)
        val header = CachedWorkoutHeader(
            filename = "intervals.zwo",
            name = "Tabata Intervals",
            author = "Coach",
            description = "High intensity",
            durationSeconds = 1200,
            estimatedTss = 45.0,
            fileSizeBytes = 400L,
            lastModifiedMs = 3000L
        )

        composeRule.setContent {
            WorkoutItemCard(
                header = header,
                onClick = {},
                onDelete = {},
                completionCount = count
            )
        }

        // Count = 0: no badge
        composeRule.onNodeWithText("✓ Completed").assertDoesNotExist()

        // Count = 1: shows "✓ Completed"
        count = 1
        composeRule.onNodeWithText("✓ Completed").assertIsDisplayed()

        // Count = 3: shows "✓ 3x"
        count = 3
        composeRule.onNodeWithText("✓ 3x").assertIsDisplayed()
    }
}

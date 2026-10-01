package com.valpr.bikecompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.valpr.bikecompanion.ui.dashboard.BeginnerPathCard
import com.valpr.bikecompanion.ui.dashboard.BeginnerPathRestoreRow
import com.valpr.bikecompanion.workout.CachedWorkoutHeader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Beginner Path card collapse/dismiss behavior (dashboard UX).
 *
 * Hosted on Robolectric (fast, no emulator); fakes only, no MockK
 * per AGENTS.md §8. Pinned to SDK 34 (cached android-all runtime).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BeginnerPathCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun headers() = listOf(
        CachedWorkoutHeader(
            filename = "beginner_01_first_pedals.zwo",
            name = "First Pedals (15 min) - Beginner 1/4",
            author = "",
            description = "",
            durationSeconds = 900,
            estimatedTss = 10.0,
            fileSizeBytes = 100,
            lastModifiedMs = 0
        )
    )

    @Test
    fun expanded_showsLevelsAndGuidance() {
        composeRule.setContent {
            BeginnerPathCard(
                cachedWorkouts = headers(),
                onLevelClick = {},
                isCollapsed = false
            )
        }

        composeRule.onNodeWithText("Beginner Path").assertIsDisplayed()
        composeRule.onNodeWithText("First Pedals (15 min)").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Collapse Beginner Path").assertIsDisplayed()
    }

    @Test
    fun collapsed_hidesLevels_keepsHeader() {
        composeRule.setContent {
            BeginnerPathCard(
                cachedWorkouts = headers(),
                onLevelClick = {},
                isCollapsed = true
            )
        }

        composeRule.onNodeWithText("Beginner Path").assertIsDisplayed()
        composeRule.onNodeWithText("First Pedals (15 min)").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Expand Beginner Path").assertIsDisplayed()
    }

    @Test
    fun dismissButton_invokesOnDismiss() {
        var dismissed = false
        composeRule.setContent {
            BeginnerPathCard(
                cachedWorkouts = headers(),
                onLevelClick = {},
                onDismiss = { dismissed = true }
            )
        }

        composeRule.onNodeWithContentDescription("Hide Beginner Path").performClick()
        composeRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun toggleButton_invokesOnToggleCollapsed() {
        var toggled = false
        composeRule.setContent {
            BeginnerPathCard(
                cachedWorkouts = headers(),
                onLevelClick = {},
                isCollapsed = true,
                onToggleCollapsed = { toggled = true }
            )
        }

        composeRule.onNodeWithContentDescription("Expand Beginner Path").performClick()
        composeRule.waitForIdle()
        assertTrue(toggled)
    }

    @Test
    fun restoreRow_invokesOnRestore() {
        var restored = false
        composeRule.setContent {
            BeginnerPathRestoreRow(onRestore = { restored = true })
        }

        composeRule.onNodeWithText("New to biking? Show Beginner Path").performClick()
        composeRule.waitForIdle()
        assertTrue(restored)
    }
}

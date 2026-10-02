package com.valpr.bikecompanion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.valpr.bikecompanion.ui.dashboard.WorkoutLibraryHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies WorkoutLibraryHeader responsive layout and interactions.
 * Hosted on Robolectric (fast, no emulator); pinned to SDK 34 per AGENTS.md §8.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutLibraryHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysTitleAndButtons() {
        composeRule.setContent {
            WorkoutLibraryHeader(
                onNewWorkout = {},
                onImportWorkout = {}
            )
        }

        composeRule.onNodeWithText("Workout Library").assertIsDisplayed()
        composeRule.onNodeWithText("New").assertIsDisplayed()
        composeRule.onNodeWithText("Import .zwo").assertIsDisplayed()
    }

    @Test
    fun clickingNew_invokesCallback() {
        var clicked = false
        composeRule.setContent {
            WorkoutLibraryHeader(
                onNewWorkout = { clicked = true },
                onImportWorkout = {}
            )
        }

        composeRule.onNodeWithText("New").performClick()
        assertTrue("onNewWorkout should have been called", clicked)
    }

    @Test
    fun clickingImport_invokesCallback() {
        var clicked = false
        composeRule.setContent {
            WorkoutLibraryHeader(
                onNewWorkout = {},
                onImportWorkout = { clicked = true }
            )
        }

        composeRule.onNodeWithText("Import .zwo").performClick()
        assertTrue("onImportWorkout should have been called", clicked)
    }

    @Test
    fun narrowWidth_rendersBothButtonsWithoutError() {
        var newClicks = 0
        var importClicks = 0

        composeRule.setContent {
            // Constrain width to 120dp to force FlowRow to overflow/wrap to the next line
            Box(modifier = Modifier.width(120.dp)) {
                WorkoutLibraryHeader(
                    onNewWorkout = { newClicks++ },
                    onImportWorkout = { importClicks++ }
                )
            }
        }

        composeRule.onNodeWithText("Workout Library").assertIsDisplayed()
        composeRule.onNodeWithText("New").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Import .zwo").assertIsDisplayed().performClick()

        assertEquals(1, newClicks)
        assertEquals(1, importClicks)
    }
}

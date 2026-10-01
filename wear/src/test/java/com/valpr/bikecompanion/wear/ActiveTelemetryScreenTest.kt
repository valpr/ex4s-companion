package com.valpr.bikecompanion.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.ui.screens.ActiveTelemetryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Paused cue on the wrist telemetry screen.
 *
 * Robolectric + compose rule (no emulator). Pinned to SDK 34 (cached
 * android-all runtime), matching the :app UI test setup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveTelemetryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state(status: Int) = WorkoutStateMessage(
        sessionStatus = status,
        elapsedSeconds = 125,
        targetWatts = 180,
        currentWatts = 175,
        cadenceRpm = 85,
        heartRateBpm = 140,
        isBailoutActive = false,
        isCadenceFloorActive = false,
        isHrCapped = false,
        workoutName = "Sweet Spot",
        athleteMaxHr = 190
    )

    @Test
    fun paused_showsPausedChip() {
        composeRule.setContent {
            ActiveTelemetryScreen(
                workoutState = state(WorkoutStateMessage.STATUS_PAUSED),
                currentHeartRate = 140,
                isAmbient = false,
                onBailoutTriggered = {}
            )
        }

        composeRule.onNodeWithText("PAUSED").assertIsDisplayed()
    }

    @Test
    fun running_hidesPausedChip() {
        composeRule.setContent {
            ActiveTelemetryScreen(
                workoutState = state(WorkoutStateMessage.STATUS_RUNNING),
                currentHeartRate = 140,
                isAmbient = false,
                onBailoutTriggered = {}
            )
        }

        composeRule.onNodeWithText("PAUSED").assertDoesNotExist()
    }

    @Test
    fun paused_showsResumeButton_hidesPauseButton() {
        composeRule.setContent {
            ActiveTelemetryScreen(
                workoutState = state(WorkoutStateMessage.STATUS_PAUSED),
                currentHeartRate = 140,
                isAmbient = false,
                onBailoutTriggered = {},
                onPauseTriggered = {},
                onResumeTriggered = {}
            )
        }

        composeRule.onNodeWithText("RESUME ▶").assertIsDisplayed()
        composeRule.onNodeWithText("PAUSE ❚❚").assertDoesNotExist()
        // Bailout stays available alongside resume.
        composeRule.onNodeWithText("BAILOUT ⚙").assertIsDisplayed()
    }

    @Test
    fun running_showsPauseButton_hidesResumeButton() {
        composeRule.setContent {
            ActiveTelemetryScreen(
                workoutState = state(WorkoutStateMessage.STATUS_RUNNING),
                currentHeartRate = 140,
                isAmbient = false,
                onBailoutTriggered = {},
                onPauseTriggered = {},
                onResumeTriggered = {}
            )
        }

        composeRule.onNodeWithText("PAUSE ❚❚").assertIsDisplayed()
        composeRule.onNodeWithText("RESUME ▶").assertDoesNotExist()
    }
}

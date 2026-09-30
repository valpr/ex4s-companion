package com.valpr.bikecompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.ui.dashboard.ActiveWorkoutCard
import com.valpr.bikecompanion.ui.summary.WorkoutSummaryScreen
import com.valpr.bikecompanion.ui.workout.ActiveWorkoutScreen
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import com.valpr.bikecompanion.workout.WorkoutSessionState
import com.valpr.bikecompanion.workout.WorkoutSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First UI tests: mode-asymmetry swaps invisible to unit tests (AGENTS.md §2).
 *
 * Hosted on Robolectric (fast, no emulator); fakes only, no MockK in this
 * class per AGENTS.md §8. Pinned to SDK 34 (cached android-all runtime).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutUiSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var managerScope: TestScope
    private lateinit var telemetryFlow: MutableStateFlow<BikeTelemetry>
    private lateinit var dispatchedResistance: MutableList<Int>

    private val profile = UserProfile(ftp = 200, weightKg = 75.0f)

    @Before
    fun setUp() {
        managerScope = TestScope()
        telemetryFlow = MutableStateFlow(
            BikeTelemetry(cadenceRpm = 85, estimatedWatts = 170, resistanceLevel = 10)
        )
        dispatchedResistance = mutableListOf()
    }

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    private fun createManager(): WorkoutSessionManager {
        return WorkoutSessionManager(
            telemetryFlow = telemetryFlow,
            onSetResistance = { dispatchedResistance.add(it) },
            userProfileFlow = flowOf(profile),
            ergController = ErgController(),
            scope = managerScope
        )
    }

    private fun structuredWorkout() = Workout(
        name = "Semantics",
        segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 600, power = 0.85f))
    )

    /** Advances the MANAGER's session loop clock (not the compose rule's). */
    private fun managerTime(ms: Long) {
        managerScope.testScheduler.advanceTimeBy(ms)
        managerScope.testScheduler.runCurrent()
    }

    @Test
    fun structured_showsIntensityAndClutch_hidesShifters() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(structuredWorkout())
        // One session tick so ergDecision resolves to ACTIVE (clutch label depends on it).
        managerTime(1100L)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("THE CLUTCH (BAILOUT)").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("-5%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+5%").assertIsDisplayed()
        composeRule.onNodeWithText("ELECTRONIC RESISTANCE SHIFTER").assertDoesNotExist()
        composeRule.onNodeWithText("-1 Res").assertDoesNotExist()
        composeRule.onNodeWithText("+1 Res").assertDoesNotExist()
    }

    @Test
    fun freeRide_showsShifters_hidesIntensityAndClutch() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("ELECTRONIC RESISTANCE SHIFTER").assertIsDisplayed()
        composeRule.onNodeWithText("-1 Res").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+1 Res").assertIsDisplayed()
        composeRule.onNodeWithText("THE CLUTCH (BAILOUT)").assertDoesNotExist()
        composeRule.onNodeWithText("-5%").assertDoesNotExist()
        composeRule.onNodeWithText("+5%").assertDoesNotExist()
    }

    @Test
    fun freeRide_plusOneRes_dispatchesIncrement() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("+1 Res").performScrollTo().performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(listOf(11), dispatchedResistance)
    }

    @Test
    fun activeSessionBanner_resumeAndEndActions() {
        var resumed = false
        var ended = false
        val state = WorkoutSessionState(
            status = SessionStatus.RUNNING,
            workout = structuredWorkout(),
            elapsedSeconds = 65,
            targetWatts = 170,
            latestTelemetry = BikeTelemetry(cadenceRpm = 85, estimatedWatts = 175, resistanceLevel = 12)
        )

        composeRule.setContent {
            ActiveWorkoutCard(
                sessionState = state,
                onResume = { resumed = true },
                onStop = { ended = true }
            )
        }

        composeRule.onNodeWithText("WORKOUT IN PROGRESS").assertIsDisplayed()
        composeRule.onNodeWithText("Resume Workout").assertIsDisplayed()
        composeRule.onNodeWithText("End").assertIsDisplayed()

        composeRule.onNodeWithText("Resume Workout").performClick()
        org.junit.Assert.assertTrue(resumed)
        org.junit.Assert.assertFalse(ended)

        composeRule.onNodeWithText("End").performClick()
        org.junit.Assert.assertTrue(ended)
    }

    @Test
    fun activeSessionBanner_pausedShowsPausedTitle() {
        val state = WorkoutSessionState(
            status = SessionStatus.PAUSED,
            elapsedSeconds = 30,
            latestTelemetry = BikeTelemetry(cadenceRpm = 0, estimatedWatts = 0, resistanceLevel = 8)
        )

        composeRule.setContent {
            ActiveWorkoutCard(sessionState = state, onResume = {}, onStop = {})
        }

        composeRule.onNodeWithText("WORKOUT PAUSED").assertIsDisplayed()
        composeRule.onNodeWithText("Free Ride").assertIsDisplayed()
    }

    @Test
    fun summary_emptySamples_showsPlaceholdersAndDone() {
        var done = false
        val summary = WorkoutSummary(
            workoutName = "Empty Ride",
            totalDurationSeconds = 0,
            totalDistanceKm = 0.0,
            avgWatts = 0,
            maxWatts = 0,
            avgCadence = 0,
            maxCadence = 0,
            totalWorkKj = 0.0,
            totalCaloriesKcal = 0,
            samples = emptyList()
        )

        composeRule.setContent {
            WorkoutSummaryScreen(summary = summary, onDone = { done = true })
        }

        composeRule.onNodeWithText("No telemetry samples recorded").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Heart Rate (BPM)").assertDoesNotExist()
        composeRule.onNodeWithText("Return to Dashboard").performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Return to Dashboard").performClick()
        org.junit.Assert.assertTrue(done)
    }
}

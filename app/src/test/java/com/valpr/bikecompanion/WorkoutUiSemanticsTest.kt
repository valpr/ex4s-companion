package com.valpr.bikecompanion

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.engine.ErgController
import com.valpr.bikecompanion.ui.dashboard.ActiveWorkoutCard
import com.valpr.bikecompanion.ui.dashboard.DeleteWorkoutDialog
import com.valpr.bikecompanion.ui.dashboard.WorkoutItemCard
import com.valpr.bikecompanion.ui.summary.WorkoutSummaryScreen
import com.valpr.bikecompanion.ui.workout.ActiveWorkoutScreen
import com.valpr.bikecompanion.wearable.WearableWatchState
import com.valpr.bikecompanion.workout.CachedWorkoutHeader
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun createManager(userProfile: UserProfile = profile): WorkoutSessionManager = WorkoutSessionManager(
        telemetryFlow = telemetryFlow,
        onSetResistance = { dispatchedResistance.add(it) },
        userProfileFlow = flowOf(userProfile),
        ergController = ErgController(),
        scope = managerScope
    )

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
    fun structured_intensityScaling_plusFivePercent_displays105Percent() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(structuredWorkout())
        managerTime(1100L)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("100%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+5%").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("105%").assertIsDisplayed()
        composeRule.onNodeWithText("104%").assertDoesNotExist()

        composeRule.onNodeWithText("-5%").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("100%").assertIsDisplayed()
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
    fun freeRide_showsIdealCadenceGuidance() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("IDEAL 80–90").assertIsDisplayed()
    }

    @Test
    fun structured_withTargetCadence_showsCadenceTarget() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        val workout = Workout(
            name = "Cadence Target",
            segments = listOf(WorkoutSegment.SteadyState(durationSeconds = 600, power = 0.85f, targetCadence = 80))
        )
        manager.startWorkout(workout)
        managerTime(1100L)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("TARGET 80").assertIsDisplayed()
    }

    @Test
    fun structured_freeRideSegment_showsShifters_hidesIntensityAndClutch() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        val workout = Workout(
            name = "Mixed",
            segments = listOf(
                WorkoutSegment.SteadyState(durationSeconds = 5, power = 0.5f),
                WorkoutSegment.FreeRide(durationSeconds = 120),
                WorkoutSegment.SteadyState(durationSeconds = 5, power = 0.5f)
            )
        )
        manager.startWorkout(workout)
        // Tick into the FreeRide segment (elapsed 6).
        managerTime(6100L)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("ELECTRONIC RESISTANCE SHIFTER").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("-1 Res").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+1 Res").assertIsDisplayed()
        composeRule.onNodeWithText("THE CLUTCH (BAILOUT)").assertDoesNotExist()
        composeRule.onNodeWithText("-5%").assertDoesNotExist()
        composeRule.onNodeWithText("+5%").assertDoesNotExist()
    }

    @Test
    fun structured_freeRideSegment_plusOneRes_dispatchesIncrement() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        val workout = Workout(
            name = "Mixed",
            segments = listOf(
                WorkoutSegment.SteadyState(durationSeconds = 5, power = 0.5f),
                WorkoutSegment.FreeRide(durationSeconds = 120),
                WorkoutSegment.SteadyState(durationSeconds = 5, power = 0.5f)
            )
        )
        manager.startWorkout(workout)
        managerTime(6100L)
        // Steady ticks dispatch ERG commands; isolate the manual click below.
        dispatchedResistance.clear()

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("+1 Res").performScrollTo().performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(listOf(11), dispatchedResistance)
    }

    @Test
    fun structured_maxEffortSegment_showsShifters_hidesIntensityAndClutch() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        val workout = Workout(
            name = "Mixed",
            segments = listOf(
                WorkoutSegment.SteadyState(durationSeconds = 5, power = 0.5f),
                WorkoutSegment.MaxEffort(durationSeconds = 120),
                WorkoutSegment.SteadyState(durationSeconds = 5, power = 0.5f)
            )
        )
        manager.startWorkout(workout)
        // Tick into the MaxEffort segment (elapsed 6).
        managerTime(6100L)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("ELECTRONIC RESISTANCE SHIFTER").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("-1 Res").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+1 Res").assertIsDisplayed()
        composeRule.onNodeWithText("THE CLUTCH (BAILOUT)").assertDoesNotExist()
        composeRule.onNodeWithText("-5%").assertDoesNotExist()
        composeRule.onNodeWithText("+5%").assertDoesNotExist()
    }

    @Test
    fun structured_ergSegment_afterFreeRide_restoresIntensityAndClutch() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        val workout = Workout(
            name = "Mixed",
            segments = listOf(
                WorkoutSegment.FreeRide(durationSeconds = 5),
                WorkoutSegment.SteadyState(durationSeconds = 600, power = 0.5f)
            )
        )
        manager.startWorkout(workout)
        // Tick past the opening FreeRide into the ERG segment (elapsed 6).
        managerTime(6100L)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithText("THE CLUTCH (BAILOUT)").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("-5%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+5%").assertIsDisplayed()
        composeRule.onNodeWithText("ELECTRONIC RESISTANCE SHIFTER").assertDoesNotExist()
    }

    @Test
    fun watchHrStale_marksHrTileAndStatusRow() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(100)
        val staleWatch = WearableWatchState(
            isConnected = true,
            nodeName = "Pixel Watch 3",
            nodeId = "node-1",
            lastHeartRateBpm = 100,
            lastHeartRateTimestampMs = System.currentTimeMillis() - 60_000L
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = staleWatch)
        }

        composeRule.onNodeWithText("HR stale", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("HEART RATE (STALE)").assertIsDisplayed()
    }

    @Test
    fun watchHrLive_showsLiveStatusRow() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(100)
        val liveWatch = WearableWatchState(
            isConnected = true,
            nodeName = "Pixel Watch 3",
            nodeId = "node-1",
            lastHeartRateBpm = 100,
            lastHeartRateTimestampMs = System.currentTimeMillis()
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = liveWatch)
        }

        composeRule.onNodeWithText("Live HR", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("HEART RATE").assertIsDisplayed()
        composeRule.onNodeWithText("HEART RATE (STALE)").assertDoesNotExist()
    }

    @Test
    fun hrTile_withHeartRate_showsZoneGauge() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(142)
        val liveWatch = WearableWatchState(
            isConnected = true,
            nodeName = "Pixel Watch 3",
            nodeId = "node-1",
            lastHeartRateBpm = 142,
            lastHeartRateTimestampMs = System.currentTimeMillis()
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = liveWatch)
        }

        composeRule.onNodeWithTag("hr_zone_gauge").assertIsDisplayed()
        composeRule.onNodeWithText("HEART RATE").assertIsDisplayed()
        composeRule.onNodeWithText("HEART RATE (OFFLINE)").assertDoesNotExist()
        // Lit (non-dimmed) gauge exposes its zone for accessibility.
        composeRule.onNodeWithContentDescription("zone 3 of 5, 142 BPM", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("dimmed", substring = true).assertDoesNotExist()
    }

    @Test
    fun hrTile_withoutHeartRate_hidesZoneGauge_showsResistance() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithTag("hr_zone_gauge").assertDoesNotExist()
        composeRule.onNodeWithText("RESISTANCE").assertIsDisplayed()
    }

    @Test
    fun hrTile_staleLink_keepsGaugeButDimsIt() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(142)
        val staleWatch = WearableWatchState(
            isConnected = true,
            nodeName = "Pixel Watch 3",
            nodeId = "node-1",
            lastHeartRateBpm = 142,
            lastHeartRateTimestampMs = System.currentTimeMillis() - 60_000L
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = staleWatch)
        }

        composeRule.onNodeWithText("HEART RATE (STALE)").assertIsDisplayed()
        composeRule.onNodeWithTag("hr_zone_gauge").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("dimmed", substring = true).assertIsDisplayed()
    }

    @Test
    fun hrTile_offlineLink_keepsGaugeButDimsIt() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(142)
        val offlineWatch = WearableWatchState(
            isConnected = false,
            nodeName = "",
            nodeId = "",
            lastHeartRateBpm = 142,
            lastHeartRateTimestampMs = System.currentTimeMillis()
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = offlineWatch)
        }

        composeRule.onNodeWithText("HEART RATE (OFFLINE)").assertIsDisplayed()
        composeRule.onNodeWithTag("hr_zone_gauge").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("dimmed", substring = true).assertIsDisplayed()
    }

    @Test
    fun hrTile_criticalHr_keepsZoneGauge_showsCappedLabel() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        // Default profile criticalHR is 181: 185 latches critical capping.
        manager.updateHeartRate(185)
        val liveWatch = WearableWatchState(
            isConnected = true,
            nodeName = "Pixel Watch 3",
            nodeId = "node-1",
            lastHeartRateBpm = 185,
            lastHeartRateTimestampMs = System.currentTimeMillis()
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = liveWatch)
        }

        composeRule.onNodeWithText("CRITICAL CAPPED", substring = true).assertIsDisplayed()
        // Critical recolors the number red but leaves the zone arcs zoned (not dimmed).
        composeRule.onNodeWithTag("hr_zone_gauge").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("dimmed", substring = true).assertDoesNotExist()
    }

    @Test
    fun hrTile_karvonenMode_showsZoneGauge() {
        val manager = createManager(profile.copy(useKarvonenZones = true))
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        // Karvonen (max 190, rest 60): Z2 spans 138–151, so the same 142 BPM
        // that reads Z3 under %max reads Z2 here — proving the mode switch.
        manager.updateHeartRate(142)
        val liveWatch = WearableWatchState(
            isConnected = true,
            nodeName = "Pixel Watch 3",
            nodeId = "node-1",
            lastHeartRateBpm = 142,
            lastHeartRateTimestampMs = System.currentTimeMillis()
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = liveWatch)
        }

        composeRule.onNodeWithTag("hr_zone_gauge").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("zone 2 of 5, 142 BPM", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun hrTile_landscape_showsZoneGauge() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(142)
        val landscape = android.content.res.Configuration().apply {
            orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }

        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides landscape
            ) {
                ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
            }
        }

        composeRule.onNodeWithTag("hr_zone_gauge").assertIsDisplayed()
    }

    @Test
    fun watchDisconnected_showsDisconnectedStatusRow() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                watchState = WearableWatchState(isConnected = false)
            )
        }

        composeRule.onNodeWithText("Watch disconnected").assertIsDisplayed()
    }

    @Test
    fun watchDisconnected_withFrozenHr_marksTileOffline() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(100)
        // Node dropped after delivering HR: bpm/timestamp retained, link gone.
        val offlineWatch = WearableWatchState(
            isConnected = false,
            nodeName = "",
            nodeId = "",
            lastHeartRateBpm = 100,
            lastHeartRateTimestampMs = System.currentTimeMillis()
        )

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {}, watchState = offlineWatch)
        }

        composeRule.onNodeWithText("Watch disconnected").assertIsDisplayed()
        composeRule.onNodeWithText("HEART RATE (OFFLINE)").assertIsDisplayed()
        composeRule.onNodeWithText("HEART RATE (STALE)").assertDoesNotExist()
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

    @Test
    fun activeWorkoutScreen_keepsScreenOnWhenEnabled_andRestoresOnDisposal() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null) // Free ride

        var showScreen by mutableStateOf(true)
        var keepOn by mutableStateOf(true)
        lateinit var hostView: View

        composeRule.setContent {
            hostView = LocalView.current
            if (showScreen) {
                ActiveWorkoutScreen(
                    sessionManager = manager,
                    onFinish = {},
                    keepScreenOn = keepOn
                )
            }
        }

        // ActiveWorkoutScreen in foreground with keepScreenOn=true
        org.junit.Assert.assertTrue(hostView.keepScreenOn)

        // Toggle setting off
        keepOn = false
        composeRule.waitForIdle()
        org.junit.Assert.assertFalse(hostView.keepScreenOn)

        // Toggle back on
        keepOn = true
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(hostView.keepScreenOn)

        // Navigate away (dispose ActiveWorkoutScreen)
        showScreen = false
        composeRule.waitForIdle()
        assertFalse(hostView.keepScreenOn)
    }

    @Test
    fun deleteWorkoutDialog_displaysWarningAndDispatchesConfirm() {
        var confirmed = false
        var dismissed = false

        composeRule.setContent {
            DeleteWorkoutDialog(
                workoutName = "VO2 Max Blast",
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true }
            )
        }

        composeRule.onNodeWithText("Delete Workout").assertIsDisplayed()
        composeRule.onNodeWithText("Are you sure you want to delete \"VO2 Max Blast\"?").assertIsDisplayed()

        composeRule.onNodeWithText("Delete").performClick()
        assertTrue(confirmed)
        assertFalse(dismissed)
    }

    @Test
    fun deleteWorkoutDialog_cancelDispatchesDismiss() {
        var confirmed = false
        var dismissed = false

        composeRule.setContent {
            DeleteWorkoutDialog(
                workoutName = "VO2 Max Blast",
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true }
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()
        assertFalse(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun workoutItemCard_clickDeleteDispatchesCallback() {
        var clicked = false
        var deleted = false
        val header = CachedWorkoutHeader(
            filename = "sample.zwo",
            name = "Test Workout",
            author = "Coach",
            description = "Test description",
            durationSeconds = 1200,
            estimatedTss = 45.0,
            fileSizeBytes = 1024L,
            lastModifiedMs = 1000L
        )

        composeRule.setContent {
            WorkoutItemCard(
                header = header,
                onClick = { clicked = true },
                onDelete = { deleted = true }
            )
        }

        composeRule.onNodeWithContentDescription("Delete").performClick()
        assertTrue(deleted)
        assertFalse(clicked)
    }

    @Test
    fun pipContent_displaysPowerCadenceAndStatus() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(structuredWorkout())
        managerTime(1100L)

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                isInPipMode = true
            )
        }

        composeRule.onNodeWithTag("pip_content").assertIsDisplayed()
        composeRule.onNodeWithTag("pip_power").assertIsDisplayed()
        composeRule.onNodeWithTag("pip_cadence").assertIsDisplayed()
        composeRule.onNodeWithTag("pip_timer").assertIsDisplayed()
        composeRule.onNodeWithTag("pip_status").assertIsDisplayed()
        composeRule.onNodeWithText("170").assertIsDisplayed()
        composeRule.onNodeWithText("85").assertIsDisplayed()
        composeRule.onNodeWithText("THE CLUTCH (BAILOUT)").assertDoesNotExist()
    }

    @Test
    fun pipContent_withHeartRate_displaysHr() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)
        manager.updateHeartRate(148)
        managerTime(1100L)

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                isInPipMode = true
            )
        }

        composeRule.onNodeWithTag("pip_hr").assertIsDisplayed()
        composeRule.onNodeWithText("♥ 148").assertIsDisplayed()
    }

    @Test
    fun activeWorkoutScreen_pipButton_triggersCallback() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        var pipTriggered = false
        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                onEnterPip = { pipTriggered = true }
            )
        }

        composeRule.onNodeWithContentDescription("Enter Picture-in-Picture").performClick()
        assertTrue(pipTriggered)
    }

    @Test
    fun activeWorkoutScreen_isInPipMode_disablesKeepScreenOn() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        var view: View? = null
        var inPip by mutableStateOf(false)

        composeRule.setContent {
            view = LocalView.current
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                keepScreenOn = true,
                isInPipMode = inPip
            )
        }

        assertTrue(view!!.keepScreenOn)

        inPip = true
        composeRule.waitForIdle()

        assertFalse(view!!.keepScreenOn)
    }

    @Test
    fun pipContent_whenPaused_displaysPausedStatus() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(structuredWorkout())
        managerTime(1100L)
        manager.pauseWorkout()

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                isInPipMode = true
            )
        }

        composeRule.onNodeWithText("PAUSED").assertIsDisplayed()
    }

    @Test
    fun pipContent_whenZeroHr_displaysSecondaryResistanceAndSpeed() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                isInPipMode = true
            )
        }

        composeRule.onNodeWithTag("pip_secondary").assertIsDisplayed()
        composeRule.onNodeWithText("L10").assertIsDisplayed()
    }

    @Test
    fun pipContent_whenBailout_displaysBailoutStatus() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(structuredWorkout())
        managerTime(1100L)
        manager.toggleClutch()
        managerTime(1100L)

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                isInPipMode = true
            )
        }

        composeRule.onNodeWithText("BAILOUT").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_safeAreaContent_displayedInStandardMode() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                isInPipMode = false
            )
        }

        composeRule.onNodeWithTag("active_workout_content").assertIsDisplayed()
        composeRule.onNodeWithTag("pip_content").assertDoesNotExist()
    }

    @Test
    fun activeWorkout_pipMode_hidesStandardSafeAreaContent() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkout(null)

        composeRule.setContent {
            ActiveWorkoutScreen(
                sessionManager = manager,
                onFinish = {},
                isInPipMode = true
            )
        }

        composeRule.onNodeWithTag("active_workout_content").assertDoesNotExist()
        composeRule.onNodeWithTag("pip_content").assertIsDisplayed()
    }

    @Test
    fun countdownOverlay_displaysSecondsAndButtons() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkoutWithCountdown(structuredWorkout(), countdownSeconds = 3)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithTag("workout_countdown_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("countdown_seconds_text").assertTextEquals("3")
        composeRule.onNodeWithTag("countdown_skip_button").assertIsDisplayed()
        composeRule.onNodeWithTag("countdown_cancel_button").assertIsDisplayed()
        composeRule.onNodeWithText("GET READY").assertIsDisplayed()
        composeRule.onNodeWithTag("countdown_title_text").assertTextEquals("Semantics")
        composeRule.onNodeWithTag("countdown_target_watts").assertIsDisplayed()
    }

    @Test
    fun countdownOverlay_skipButton_dismissesOverlayAndStartsWorkout() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkoutWithCountdown(structuredWorkout(), countdownSeconds = 3)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithTag("countdown_skip_button").performClick()

        assertEquals(SessionStatus.RUNNING, manager.sessionState.value.status)
        composeRule.onNodeWithTag("workout_countdown_overlay").assertDoesNotExist()
    }

    @Test
    fun countdownOverlay_cancelButton_resetsToIdle() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkoutWithCountdown(structuredWorkout(), countdownSeconds = 3)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithTag("countdown_cancel_button").performClick()

        assertEquals(SessionStatus.IDLE, manager.sessionState.value.status)
        composeRule.onNodeWithTag("workout_countdown_overlay").assertDoesNotExist()
    }

    @Test
    fun countdownOverlay_freeRide_displaysFreeRideTitle() {
        val manager = createManager()
        managerScope.testScheduler.advanceUntilIdle()
        manager.startWorkoutWithCountdown(null, countdownSeconds = 3)

        composeRule.setContent {
            ActiveWorkoutScreen(sessionManager = manager, onFinish = {})
        }

        composeRule.onNodeWithTag("workout_countdown_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("countdown_title_text").assertTextEquals("Free Ride")
        composeRule.onNodeWithTag("countdown_target_watts").assertDoesNotExist()
    }
}

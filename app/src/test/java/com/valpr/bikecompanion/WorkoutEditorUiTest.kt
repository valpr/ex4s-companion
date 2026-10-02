package com.valpr.bikecompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.valpr.bikecompanion.ui.editor.WorkoutEditorScreen
import com.valpr.bikecompanion.ui.editor.WorkoutEditorState
import com.valpr.bikecompanion.workout.WorkoutRepository
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
import java.io.File

/**
 * Editor semantics: pre-save validity is visible, errors highlight, and a
 * valid draft persists through the screen's Save path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var tempDir: File
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        tempDir = File.createTempFile("editor_ui_test_", "").apply {
            delete()
            mkdirs()
        }
        repository = WorkoutRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun newDraft_showsNameErrorAndDisabledSave() {
        val state = WorkoutEditorState(repository)
        state.loadNew()

        composeRule.setContent {
            WorkoutEditorScreen(state = state, onSaved = {}, onNavigateBack = {})
        }

        // Surfaced twice by design: field hint + footer summary.
        composeRule.onAllNodesWithText("Workout name is required")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Workout name is required")[1].assertIsDisplayed()
        composeRule.onNodeWithTag("editorSaveButton").assertIsNotEnabled()
    }

    @Test
    fun typingName_clearsErrorAndSavePersistsFile() {
        val state = WorkoutEditorState(repository)
        state.loadNew()
        var saved = false

        composeRule.setContent {
            WorkoutEditorScreen(state = state, onSaved = { saved = true }, onNavigateBack = {})
        }

        composeRule.onNodeWithTag("editorNameField").performTextInput("Lunch Ride")
        composeRule.onNodeWithText("Workout name is required").assertDoesNotExist()
        composeRule.onNodeWithTag("editorSaveButton").assertIsEnabled()
        composeRule.onNodeWithTag("editorSaveButton").performClick()
        composeRule.waitForIdle()

        assertTrue(saved)
        assertTrue(repository.workoutExists("lunch_ride.zwo"))
        val reloaded = repository.loadWorkout("lunch_ride.zwo").getOrThrow()
        assertTrue(reloaded.segments.isNotEmpty())
    }

    @Test
    fun invalidPower_showsFieldErrorAndBlocksSave() {
        val state = WorkoutEditorState(repository)
        state.loadNew()
        state.updateHeader(name = "Bad Power")

        composeRule.setContent {
            WorkoutEditorScreen(state = state, onSaved = {}, onNavigateBack = {})
        }

        state.updateSegment(0) { it.copy(powerPct = 0) }
        composeRule.waitForIdle()

        // Footer summary carries the step-prefixed message; the card-level
        // hint is the exact message on the first card.
        composeRule.onNodeWithText("Step 1: Power must be 1–200% FTP").assertIsDisplayed()
        composeRule.onNodeWithText("Power must be 1–200% FTP", substring = false)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("editorSaveButton").assertIsNotEnabled()
        assertFalse(state.canSave())
    }

    @Test
    fun editingIntervalWorkout_collapsesToSingleRow() {
        repository.ensureSampleWorkouts()
        val state = WorkoutEditorState(repository)
        assertTrue(state.load("hiit_30_intervals.zwo"))

        composeRule.setContent {
            WorkoutEditorScreen(state = state, onSaved = {}, onNavigateBack = {})
        }

        assertEquals(3, state.segments.size)
        composeRule.onNodeWithText("Repeats").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("On (%FTP)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editingSeededFile_showsResetAction() {
        repository.ensureSampleWorkouts()
        val state = WorkoutEditorState(repository)
        assertTrue(state.load("recovery_20_low_impact.zwo"))

        composeRule.setContent {
            WorkoutEditorScreen(state = state, onSaved = {}, onNavigateBack = {})
        }

        composeRule.onNodeWithText("Reset").assertIsDisplayed()
        composeRule.onNodeWithText("Step 1").performScrollTo().assertIsDisplayed()
        assertEquals(3, state.segments.size)
    }

    @Test
    fun overwriteCollision_showsDialogAndConfirms() {
        repository.ensureSampleWorkouts()
        val state = WorkoutEditorState(repository)
        state.loadNew()
        state.updateHeader(name = "Endurance 30 Fat Burn")
        var saved = false

        composeRule.setContent {
            WorkoutEditorScreen(state = state, onSaved = { saved = true }, onNavigateBack = {})
        }

        // "Endurance 30 Fat Burn" derives endurance_30_fat_burn.zwo, which exists.
        composeRule.onNodeWithTag("editorSaveButton").performClick()
        composeRule.waitForIdle()
        assertFalse(saved)
        composeRule.onNodeWithText("Overwrite existing file?").assertExists()
        composeRule.onNodeWithTag("confirmOverwriteButton").performClick()
        composeRule.waitForIdle()
        assertTrue(saved)
        assertEquals(
            "Endurance 30 Fat Burn",
            repository.loadWorkout("endurance_30_fat_burn.zwo").getOrThrow().name
        )
    }

    @Test
    fun resetDialog_restoresOriginalContent() {
        repository.ensureSampleWorkouts()
        val state = WorkoutEditorState(repository)
        assertTrue(state.load("recovery_20_low_impact.zwo"))
        state.updateSegment(0) { it.copy(durationSeconds = 999) }

        composeRule.setContent {
            WorkoutEditorScreen(state = state, onSaved = {}, onNavigateBack = {})
        }

        composeRule.onNodeWithText("Reset").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reset to original?").assertExists()
        composeRule.onNodeWithTag("confirmResetButton").performClick()
        composeRule.waitForIdle()
        assertEquals(240, state.segments[0].durationSeconds)
        assertFalse(state.isDirty)
    }
}

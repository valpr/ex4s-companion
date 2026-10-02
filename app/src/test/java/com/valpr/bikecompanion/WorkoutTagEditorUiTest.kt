package com.valpr.bikecompanion

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.valpr.bikecompanion.ui.editor.WorkoutTagEditorSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutTagEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysAppliedTagsAndSuggestions() {
        val applied = mutableStateListOf("Endurance")
        val suggestions = listOf("Endurance", "SweetSpot", "HIIT")

        composeRule.setContent {
            WorkoutTagEditorSection(
                appliedTags = applied,
                suggestedTags = suggestions,
                onAddTag = { applied.add(it) },
                onRemoveTag = { applied.remove(it) }
            )
        }

        // Applied tag is shown
        composeRule.onNodeWithText("Endurance").assertIsDisplayed()
        // Suggestions not yet applied are shown with "+"
        composeRule.onNodeWithText("+ SweetSpot").assertIsDisplayed()
        composeRule.onNodeWithText("+ HIIT").assertIsDisplayed()
    }

    @Test
    fun clickingSuggestion_addsTag() {
        val applied = mutableStateListOf("Endurance")
        val suggestions = listOf("Endurance", "SweetSpot")

        composeRule.setContent {
            WorkoutTagEditorSection(
                appliedTags = applied,
                suggestedTags = suggestions,
                onAddTag = { applied.add(it) },
                onRemoveTag = { applied.remove(it) }
            )
        }

        composeRule.onNodeWithText("+ SweetSpot").performClick()
        assertEquals(listOf("Endurance", "SweetSpot"), applied)
    }

    @Test
    fun clickingRemove_removesTag() {
        val applied = mutableStateListOf("Endurance", "SweetSpot")
        val suggestions = listOf("Endurance", "SweetSpot")

        composeRule.setContent {
            WorkoutTagEditorSection(
                appliedTags = applied,
                suggestedTags = suggestions,
                onAddTag = { applied.add(it) },
                onRemoveTag = { applied.remove(it) }
            )
        }

        composeRule.onNodeWithContentDescription("Remove SweetSpot").performClick()
        assertEquals(listOf("Endurance"), applied)
    }

    @Test
    fun deliberateAdd_addsNewTagAndClearsInput() {
        val applied = mutableStateListOf<String>()
        val suggestions = listOf("Endurance")

        composeRule.setContent {
            WorkoutTagEditorSection(
                appliedTags = applied,
                suggestedTags = suggestions,
                onAddTag = { applied.add(it) },
                onRemoveTag = { applied.remove(it) }
            )
        }

        // Initially "Add Tag" button is disabled (input is empty)
        composeRule.onNodeWithTag("editorAddTagButton").assertIsNotEnabled()

        // Type a new custom tag
        composeRule.onNodeWithTag("editorNewTagField").performTextInput("Gran Fondo")
        composeRule.onNodeWithTag("editorAddTagButton").assertIsEnabled().performClick()

        assertTrue(applied.contains("Gran Fondo"))
        // Input should be cleared and button disabled again
        composeRule.onNodeWithTag("editorAddTagButton").assertIsNotEnabled()
    }

    @Test
    fun duplicateTag_keepsAddButtonDisabled() {
        val applied = mutableStateListOf("Climb")
        val suggestions = listOf("Climb")

        composeRule.setContent {
            WorkoutTagEditorSection(
                appliedTags = applied,
                suggestedTags = suggestions,
                onAddTag = { applied.add(it) },
                onRemoveTag = { applied.remove(it) }
            )
        }

        // Typing existing tag (case-insensitive) should keep button disabled
        composeRule.onNodeWithTag("editorNewTagField").performTextInput("climb")
        composeRule.onNodeWithTag("editorAddTagButton").assertIsNotEnabled()
    }
}

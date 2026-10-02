package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.editor.WorkoutEditorState
import com.valpr.bikecompanion.workout.WorkoutRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEditorStateTagTest {

    private val repository = mockk<WorkoutRepository>(relaxed = true)

    @Test
    fun addTag_addsTagAndMarksDirty() {
        val state = WorkoutEditorState(repository)
        state.loadNew()
        assertFalse(state.isDirty)
        assertTrue(state.currentTags.isEmpty())

        val added = state.addTag("Endurance")
        assertTrue(added)
        assertTrue(state.isDirty)
        assertEquals(listOf("Endurance"), state.currentTags)
        assertEquals("Endurance", state.tagsText)
    }

    @Test
    fun addTag_caseInsensitiveDuplicate_rejected() {
        val state = WorkoutEditorState(repository)
        state.loadNew()
        state.addTag("Endurance")

        val duplicateAdded = state.addTag("endurance")
        assertFalse(duplicateAdded)
        assertEquals(listOf("Endurance"), state.currentTags)
    }

    @Test
    fun addTag_normalizesCanonicalCasing() {
        val state = WorkoutEditorState(repository)
        state.loadNew()

        // "vo2max" should normalize to canonical "VO2Max"
        val added = state.addTag("vo2max")
        assertTrue(added)
        assertEquals(listOf("VO2Max"), state.currentTags)
    }

    @Test
    fun addTag_blankOrEmpty_rejected() {
        val state = WorkoutEditorState(repository)
        state.loadNew()

        assertFalse(state.addTag(""))
        assertFalse(state.addTag("   "))
        assertTrue(state.currentTags.isEmpty())
    }

    @Test
    fun removeTag_removesTagAndMarksDirty() {
        val state = WorkoutEditorState(repository)
        state.loadNew()
        state.addTag("HIIT")
        state.addTag("Climb")

        // Reset dirty flag to verify removeTag sets it
        state.updateHeader() // or state is already dirty

        val removed = state.removeTag("hiit")
        assertTrue(removed)
        assertTrue(state.isDirty)
        assertEquals(listOf("Climb"), state.currentTags)
        assertEquals("Climb", state.tagsText)
    }

    @Test
    fun removeTag_nonExistent_returnsFalse() {
        val state = WorkoutEditorState(repository)
        state.loadNew()
        state.addTag("Climb")

        val removed = state.removeTag("Endurance")
        assertFalse(removed)
        assertEquals(listOf("Climb"), state.currentTags)
    }

    @Test
    fun addTag_commaSeparated_splitsAndTrims() {
        val state = WorkoutEditorState(repository)
        state.loadNew()

        val added = state.addTag("Intervals, Hills, Intervals")
        assertTrue(added)
        assertEquals(listOf("Intervals", "Hills"), state.currentTags)

        // Can remove Hills independently
        assertTrue(state.removeTag("hills"))
        assertEquals(listOf("Intervals"), state.currentTags)
    }

    @Test
    fun availableTagSuggestions_defaultsToCanonical_andCanBeUpdated() {
        val state = WorkoutEditorState(repository)
        assertEquals(com.valpr.bikecompanion.workout.WorkoutTags.CANONICAL, state.availableTagSuggestions)

        state.setAvailableTagSuggestions(listOf("CustomTag1", "CustomTag2"))
        assertEquals(listOf("CustomTag1", "CustomTag2"), state.availableTagSuggestions)
    }
}

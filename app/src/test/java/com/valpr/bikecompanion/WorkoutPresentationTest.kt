package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.WorkoutPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutPresentationTest {

    @Test
    fun formatAuthor_echelonCompanion_returnsDefault() {
        assertEquals("Default", WorkoutPresentation.formatAuthor("Echelon Companion"))
        assertEquals("Default", WorkoutPresentation.formatAuthor("echelon companion"))
        assertEquals("Default", WorkoutPresentation.formatAuthor("By Echelon Companion"))
        assertEquals("Default", WorkoutPresentation.formatAuthor("by echelon companion"))
    }

    @Test
    fun formatAuthor_default_returnsDefault() {
        assertEquals("Default", WorkoutPresentation.formatAuthor("Default"))
        assertEquals("Default", WorkoutPresentation.formatAuthor("default"))
        assertEquals("Default", WorkoutPresentation.formatAuthor("By Default"))
        assertEquals("Default", WorkoutPresentation.formatAuthor("by default"))
    }

    @Test
    fun formatAuthor_customAuthor_returnsByAuthor() {
        assertEquals("By Zwift", WorkoutPresentation.formatAuthor("Zwift"))
        assertEquals("By Coach Jack", WorkoutPresentation.formatAuthor("Coach Jack"))
        assertEquals("By Coach Jack", WorkoutPresentation.formatAuthor("By Coach Jack"))
    }

    @Test
    fun formatAuthor_blank_returnsEmpty() {
        assertEquals("", WorkoutPresentation.formatAuthor(""))
        assertEquals("", WorkoutPresentation.formatAuthor("   "))
        assertEquals("", WorkoutPresentation.formatAuthor("By"))
        assertEquals("", WorkoutPresentation.formatAuthor("by"))
        assertEquals("", WorkoutPresentation.formatAuthor("By "))
        assertEquals("", WorkoutPresentation.formatAuthor("by  "))
    }
}

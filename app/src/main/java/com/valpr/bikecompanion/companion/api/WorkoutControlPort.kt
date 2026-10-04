package com.valpr.bikecompanion.companion.api

/**
 * Control interface exposing session actions that can be commanded remotely
 * (e.g. from a companion watch or hardware remote).
 */
interface WorkoutControlPort {
    fun toggleClutch()
    fun resumeManually()
    fun adjustIntensity(delta: Float)
    fun pauseWorkout()
    fun resumeWorkout()
    fun stopWorkout()
}

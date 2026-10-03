package com.valpr.bikecompanion.ui.workout

import com.valpr.bikecompanion.ui.navigation.AppScreen
import com.valpr.bikecompanion.workout.SessionStatus

/**
 * Pure helper governing whether the app should enter Picture-in-Picture mode
 * automatically when backgrounded or navigated away from.
 *
 * Plain-JUnit testable per AGENTS.md §8.
 */
object PipHelper {
    fun shouldAutoEnterPip(
        status: SessionStatus,
        currentScreen: AppScreen,
        autoEnterPipEnabled: Boolean
    ): Boolean {
        if (!autoEnterPipEnabled) return false
        if (currentScreen != AppScreen.ACTIVE_WORKOUT) return false
        return status == SessionStatus.RUNNING || status == SessionStatus.PAUSED
    }
}

package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.navigation.AppScreen
import com.valpr.bikecompanion.ui.workout.PipHelper
import com.valpr.bikecompanion.workout.SessionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipHelperTest {

    @Test
    fun shouldAutoEnterPip_whenDisabledInSettings_returnsFalse() {
        assertFalse(
            PipHelper.shouldAutoEnterPip(
                status = SessionStatus.RUNNING,
                currentScreen = AppScreen.ACTIVE_WORKOUT,
                autoEnterPipEnabled = false
            )
        )
        assertFalse(
            PipHelper.shouldAutoEnterPip(
                status = SessionStatus.PAUSED,
                currentScreen = AppScreen.ACTIVE_WORKOUT,
                autoEnterPipEnabled = false
            )
        )
    }

    @Test
    fun shouldAutoEnterPip_whenNotActiveWorkoutScreen_returnsFalse() {
        val nonWorkoutScreens = AppScreen.entries.filter { it != AppScreen.ACTIVE_WORKOUT }
        for (screen in nonWorkoutScreens) {
            assertFalse(
                "Screen $screen must not trigger auto-PiP",
                PipHelper.shouldAutoEnterPip(
                    status = SessionStatus.RUNNING,
                    currentScreen = screen,
                    autoEnterPipEnabled = true
                )
            )
        }
    }

    @Test
    fun shouldAutoEnterPip_whenActiveWorkoutAndRunning_returnsTrue() {
        assertTrue(
            PipHelper.shouldAutoEnterPip(
                status = SessionStatus.RUNNING,
                currentScreen = AppScreen.ACTIVE_WORKOUT,
                autoEnterPipEnabled = true
            )
        )
    }

    @Test
    fun shouldAutoEnterPip_whenActiveWorkoutAndPaused_returnsTrue() {
        assertTrue(
            PipHelper.shouldAutoEnterPip(
                status = SessionStatus.PAUSED,
                currentScreen = AppScreen.ACTIVE_WORKOUT,
                autoEnterPipEnabled = true
            )
        )
    }

    @Test
    fun shouldAutoEnterPip_whenActiveWorkoutAndIdle_returnsFalse() {
        assertFalse(
            PipHelper.shouldAutoEnterPip(
                status = SessionStatus.IDLE,
                currentScreen = AppScreen.ACTIVE_WORKOUT,
                autoEnterPipEnabled = true
            )
        )
    }

    @Test
    fun shouldAutoEnterPip_whenActiveWorkoutAndCompleted_returnsFalse() {
        assertFalse(
            PipHelper.shouldAutoEnterPip(
                status = SessionStatus.COMPLETED,
                currentScreen = AppScreen.ACTIVE_WORKOUT,
                autoEnterPipEnabled = true
            )
        )
    }
}

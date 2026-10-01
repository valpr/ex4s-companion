package com.valpr.bikecompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.valpr.bikecompanion.ui.athletestats.AthleteStatsScreen
import com.valpr.bikecompanion.ui.athletestats.AthleteStatsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AthleteStatsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun athleteStatsScreen_displaysSectionsAndTogglesCollapsibleHardware() {
        val app = ApplicationProvider.getApplicationContext<BikeApplication>()
        val viewModel = AthleteStatsViewModel(app)

        composeRule.setContent {
            AthleteStatsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Check Top Bar & Core cards
        composeRule.onNodeWithText("Athlete Profile & Stats").assertIsDisplayed()
        composeRule.onNodeWithText("Athlete Biometrics").assertIsDisplayed()
        composeRule.onNodeWithText("Power Performance & FTP").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("POWER-TO-WEIGHT").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Coggan 7-Zone Power Targets").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cardiovascular & Heart Rate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("HR Training Zones").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bike & Hardware Settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Display & Screen Settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Keep Screen Awake During Workouts").performScrollTo().assertIsDisplayed()

        // Hardware section is initially collapsed
        composeRule.onNodeWithText("Bluetooth Setup").assertDoesNotExist()

        // Click to expand hardware section
        composeRule.onNodeWithText("Bike & Hardware Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bluetooth Setup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Advanced Engine Tuning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reset to Defaults").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Firmware Lockdown Advisory").assertDoesNotExist()
    }

    @Test
    fun saveVitals_showsSnackbarConfirmation() {
        val app = ApplicationProvider.getApplicationContext<BikeApplication>()
        val viewModel = AthleteStatsViewModel(app)

        composeRule.setContent {
            AthleteStatsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeRule.onNodeWithText("Save Vitals").performScrollTo().performClick()
        // DataStore write + snackbar are asynchronous; poll until visible.
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Vitals saved").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Vitals saved").assertIsDisplayed()
    }

    @Test
    fun savePower_showsSnackbarConfirmation() {
        val app = ApplicationProvider.getApplicationContext<BikeApplication>()
        val viewModel = AthleteStatsViewModel(app)

        composeRule.setContent {
            AthleteStatsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeRule.onNodeWithText("Save Power").performScrollTo().performClick()
        // DataStore write + snackbar are asynchronous; poll until visible.
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Power settings saved").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Power settings saved").assertIsDisplayed()
    }
}

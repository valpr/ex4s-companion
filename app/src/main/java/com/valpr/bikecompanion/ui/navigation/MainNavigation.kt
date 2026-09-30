package com.valpr.bikecompanion.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.health.HealthConnectManager
import com.valpr.bikecompanion.service.WorkoutTrackingService
import com.valpr.bikecompanion.ui.athletestats.AthleteStatsScreen
import com.valpr.bikecompanion.ui.athletestats.AthleteStatsViewModel
import com.valpr.bikecompanion.ui.dashboard.DashboardScreen
import com.valpr.bikecompanion.ui.dashboard.DashboardViewModel
import com.valpr.bikecompanion.ui.summary.WorkoutSummaryScreen
import com.valpr.bikecompanion.ui.workout.ActiveWorkoutScreen
import com.valpr.bikecompanion.workout.SessionStatus

enum class AppScreen {
    DASHBOARD,
    ACTIVE_WORKOUT,
    WORKOUT_SUMMARY,
    ATHLETE_STATS
}

@Composable
fun MainNavigation(onRequestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as BikeApplication
    val sessionManager = app.workoutSessionManager
    val sessionState by sessionManager.sessionState.collectAsState()

    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }

    // If session transitions to COMPLETED, navigate to summary
    if (sessionState.status == SessionStatus.COMPLETED && currentScreen != AppScreen.WORKOUT_SUMMARY) {
        currentScreen = AppScreen.WORKOUT_SUMMARY
    }

    when (currentScreen) {
        AppScreen.DASHBOARD -> {
            val dashboardVm: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = dashboardVm,
                onStartWorkout = {
                    WorkoutTrackingService.startService(context)
                    currentScreen = AppScreen.ACTIVE_WORKOUT
                },
                onResumeWorkout = {
                    WorkoutTrackingService.startService(context)
                    currentScreen = AppScreen.ACTIVE_WORKOUT
                },
                onNavigateToAthleteStats = { currentScreen = AppScreen.ATHLETE_STATS },
                modifier = modifier
            )
        }

        AppScreen.ACTIVE_WORKOUT -> {
            BackHandler {
                // Return to dashboard but leave workout running in foreground service
                currentScreen = AppScreen.DASHBOARD
            }
            ActiveWorkoutScreen(
                sessionManager = sessionManager,
                onFinish = {
                    currentScreen = AppScreen.WORKOUT_SUMMARY
                },
                modifier = modifier
            )
        }

        AppScreen.WORKOUT_SUMMARY -> {
            BackHandler {
                WorkoutTrackingService.stopService(context)
                sessionManager.resetToIdle()
                currentScreen = AppScreen.DASHBOARD
            }
            val healthManager = app.healthConnectManager
            val syncState by healthManager.syncState.collectAsState()
            val userProfile by app.userProfileRepository.userProfileFlow.collectAsState(
                initial = UserProfile()
            )
            val permissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { granted ->
                if (granted.containsAll(HealthConnectManager.requiredPermissions())) {
                    healthManager.retry()
                }
            }
            sessionState.summary?.let { summary ->
                // Batch-write to Health Connect once per completed session.
                LaunchedEffect(summary) {
                    healthManager.syncWorkout(summary, userProfile.weightKg)
                }
                WorkoutSummaryScreen(
                    summary = summary,
                    onDone = {
                        WorkoutTrackingService.stopService(context)
                        sessionManager.resetToIdle()
                        currentScreen = AppScreen.DASHBOARD
                    },
                    healthSyncState = syncState,
                    onSyncRetry = { healthManager.retry() },
                    onSyncConnect = {
                        permissionLauncher.launch(HealthConnectManager.requiredPermissions())
                    },
                    modifier = modifier
                )
            } ?: run {
                currentScreen = AppScreen.DASHBOARD
            }
        }

        AppScreen.ATHLETE_STATS -> {
            BackHandler { currentScreen = AppScreen.DASHBOARD }
            val athleteStatsVm: AthleteStatsViewModel = viewModel()
            val healthManager = app.healthConnectManager
            val healthStatus by healthManager.connectionStatus.collectAsState()
            val healthSync by healthManager.syncState.collectAsState()
            val healthPermissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) {
                healthManager.refreshConnectionStatus()
            }
            LaunchedEffect(Unit) {
                healthManager.refreshConnectionStatus()
            }
            AthleteStatsScreen(
                viewModel = athleteStatsVm,
                onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                healthStatus = healthStatus,
                healthSyncState = healthSync,
                onHealthConnectClick = {
                    healthPermissionLauncher.launch(HealthConnectManager.requiredPermissions())
                },
                modifier = modifier
            )
        }
    }
}

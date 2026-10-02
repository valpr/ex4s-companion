package com.valpr.bikecompanion.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    ATHLETE_STATS,
    RIDE_HISTORY,
    RIDE_DETAIL,
    WORKOUT_EDITOR
}

@Composable
fun MainNavigation(onRequestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as BikeApplication
    val sessionManager = app.workoutSessionManager
    val sessionState by sessionManager.sessionState.collectAsState()

    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
    var editorFilename by rememberSaveable { mutableStateOf<String?>(null) }

    // Editor draft owner hoisted so dashboard-initiated navigation can guard
    // against silently dropping unsaved edits (same instance the editor
    // screen observes; Activity-scoped like DashboardViewModel).
    val editorVm: com.valpr.bikecompanion.ui.editor.WorkoutEditorViewModel = viewModel()
    var pendingEditorFilename by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditorSwitchDialog by rememberSaveable { mutableStateOf(false) }

    fun navigateToEditor(filename: String?) {
        if (editorVm.hasUnsavedChanges() && filename != editorVm.openFilename) {
            pendingEditorFilename = filename
            showEditorSwitchDialog = true
        } else {
            editorFilename = filename
            currentScreen = AppScreen.WORKOUT_EDITOR
        }
    }

    if (showEditorSwitchDialog) {
        AlertDialog(
            onDismissRequest = {
                showEditorSwitchDialog = false
                pendingEditorFilename = null
            },
            title = { Text("Discard unsaved edits?") },
            text = { Text("You have unsaved changes. Discard them and open the selected workout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pendingEditorFilename
                        showEditorSwitchDialog = false
                        pendingEditorFilename = null
                        editorVm.discardAndOpen(target)
                        editorFilename = target
                        currentScreen = AppScreen.WORKOUT_EDITOR
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditorSwitchDialog = false
                        pendingEditorFilename = null
                    }
                ) { Text("Keep editing") }
            }
        )
    }

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
                onNavigateToHistory = { currentScreen = AppScreen.RIDE_HISTORY },
                onEditWorkout = { filename -> navigateToEditor(filename) },
                modifier = modifier
            )
        }

        AppScreen.WORKOUT_EDITOR -> {
            // Clean drafts pop via this handler; dirty drafts are intercepted
            // by the editor screen's own BackHandler (discard dialog).
            BackHandler(enabled = !editorVm.editorState.isDirty) {
                currentScreen = AppScreen.DASHBOARD
            }
            val dashboardVm: DashboardViewModel = viewModel()
            LaunchedEffect(editorFilename) {
                if (!editorVm.open(editorFilename)) {
                    // Guarded paths should prevent this; bounce back to the
                    // open draft and offer discard-and-switch instead of
                    // silently dropping edits.
                    pendingEditorFilename = editorFilename
                    editorFilename = editorVm.openFilename
                    showEditorSwitchDialog = true
                }
            }
            com.valpr.bikecompanion.ui.editor.WorkoutEditorScreen(
                state = editorVm.editorState,
                onSaved = {
                    dashboardVm.loadWorkouts()
                    currentScreen = AppScreen.DASHBOARD
                },
                onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                modifier = modifier
            )
        }

        AppScreen.RIDE_HISTORY -> {
            BackHandler { currentScreen = AppScreen.DASHBOARD }
            val historyVm: com.valpr.bikecompanion.ui.history.RideHistoryViewModel = viewModel()
            com.valpr.bikecompanion.ui.history.RideHistoryScreen(
                viewModel = historyVm,
                onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                onRideClick = { id ->
                    historyVm.selectRide(id)
                    currentScreen = AppScreen.RIDE_DETAIL
                },
                modifier = modifier
            )
        }

        AppScreen.RIDE_DETAIL -> {
            val historyVm: com.valpr.bikecompanion.ui.history.RideHistoryViewModel = viewModel()
            BackHandler {
                historyVm.clearSelection()
                currentScreen = AppScreen.RIDE_HISTORY
            }
            com.valpr.bikecompanion.ui.history.RideDetailScreen(
                viewModel = historyVm,
                onNavigateBack = {
                    historyVm.clearSelection()
                    currentScreen = AppScreen.RIDE_HISTORY
                },
                modifier = modifier
            )
        }

        AppScreen.ACTIVE_WORKOUT -> {
            BackHandler {
                // Return to dashboard but leave workout running in foreground service
                currentScreen = AppScreen.DASHBOARD
            }
            val userProfile by app.userProfileRepository.userProfileFlow.collectAsState(
                initial = UserProfile()
            )
            val watchState by app.phoneWearableManager.watchState.collectAsState(
                initial = com.valpr.bikecompanion.wearable.WearableWatchState()
            )
            ActiveWorkoutScreen(
                sessionManager = sessionManager,
                onFinish = {
                    currentScreen = AppScreen.WORKOUT_SUMMARY
                },
                keepScreenOn = userProfile.keepScreenOn,
                watchState = watchState,
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
                // Persist to local ride history (idempotent on start epoch).
                LaunchedEffect(summary) {
                    healthManager.syncWorkout(summary, userProfile.weightKg)
                    try {
                        // Prefer the real library filename threaded through startWorkout();
                        // fall back to name-based matching for sessions started before it existed.
                        val filename = sessionState.sourceWorkoutFilename
                            ?: sessionState.workout?.let {
                                com.valpr.bikecompanion.history.BeginnerFilenameMatcher.filenameFor(
                                    it.name,
                                    it.totalDurationSeconds
                                )
                            }
                        app.workoutHistoryRepository.save(summary, filename)
                    } catch (_: Exception) {
                        // History is best-effort; Health Connect sync must not be affected.
                    }
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
                    onExportTcx = {
                        try {
                            com.valpr.bikecompanion.history.TcxShareHelper.shareSummary(context, summary)
                        } catch (_: Exception) {
                            // Share sheet unavailable; summary remains usable.
                        }
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

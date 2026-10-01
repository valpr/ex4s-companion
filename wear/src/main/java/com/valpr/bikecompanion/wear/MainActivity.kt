package com.valpr.bikecompanion.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.wear.ambient.AmbientLifecycleObserver
import com.valpr.bikecompanion.wear.haptics.WatchHapticManager
import com.valpr.bikecompanion.wear.health.HealthServicesManager
import com.valpr.bikecompanion.wear.messaging.WearMessageManager
import com.valpr.bikecompanion.wear.service.WearWorkoutTrackingService
import com.valpr.bikecompanion.wear.ui.screens.ActiveTelemetryScreen
import com.valpr.bikecompanion.wear.ui.screens.BailoutOverlay
import com.valpr.bikecompanion.wear.ui.screens.ResumeSlapOverlay
import com.valpr.bikecompanion.wear.ui.screens.StandbyScreen
import com.valpr.bikecompanion.wear.ui.theme.BikeCompanionWearTheme

class MainActivity : ComponentActivity() {

    private val app by lazy { application as WearBikeApplication }
    private val hapticManager: WatchHapticManager by lazy { app.hapticManager }
    private val messageManager: WearMessageManager by lazy { app.messageManager }
    private val healthServicesManager: HealthServicesManager by lazy { app.healthServicesManager }

    private var isAmbientMode by mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbientMode = true
            healthServicesManager.setAmbientMode(true)
        }

        override fun onExitAmbient() {
            isAmbientMode = false
            healthServicesManager.setAmbientMode(false)
        }

        override fun onUpdateAmbient() {}
    }

    private val ambientObserver by lazy { AmbientLifecycleObserver(this, ambientCallback) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycle.addObserver(ambientObserver)

        setContent {
            BikeCompanionWearTheme {
                WearApp(
                    messageManager = messageManager,
                    healthServicesManager = healthServicesManager,
                    isAmbient = isAmbientMode
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        messageManager.refreshConnectedPhone()
        messageManager.requestWorkoutState()
        // When active UI returns to foreground, prioritize low-latency batching
        healthServicesManager.setAmbientMode(isAmbientMode)
    }

    override fun onPause() {
        super.onPause()
        // When navigating away from foreground, switch to background-friendly batch interval to save power
        healthServicesManager.setAmbientMode(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: Managers are Application singletons maintained by WearBikeApplication and
        // WearWorkoutTrackingService. We do NOT call onDestroy() here, keeping background
        // tracking alive when the user navigates away or dismisses the Activity.
    }
}

@Composable
fun WearApp(messageManager: WearMessageManager, healthServicesManager: HealthServicesManager, isAmbient: Boolean) {
    val context = LocalContext.current
    val isPhoneConnected by messageManager.isPhoneConnected.collectAsState()
    val workoutState by messageManager.workoutState.collectAsState()
    val liveHr by healthServicesManager.currentHeartRate.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val rotaryBailout = remember { com.valpr.bikecompanion.shared.RotaryBailoutAccumulator() }

    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.BODY_SENSORS)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val sensorsGranted = results[Manifest.permission.BODY_SENSORS] == true
        if (sensorsGranted && (workoutState?.isRunning == true || workoutState?.isPaused == true)) {
            WearWorkoutTrackingService.start(context)
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
        focusRequester.requestFocus()
    }

    // Auto-start foreground tracking service when workout becomes active on phone, stop when explicitly idle/completed.
    // Invariant (AGENTS.md §5): null state represents uninitialized transition window and must NOT stop service.
    LaunchedEffect(workoutState?.sessionStatus) {
        val state = workoutState ?: return@LaunchedEffect
        if (state.isRunning || state.isPaused) {
            WearWorkoutTrackingService.start(context)
        } else if (state.isIdle || state.isCompleted) {
            WearWorkoutTrackingService.stop(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                // Rotary Crown Bailout Gesture (backward flick only, see RotaryBailoutAccumulator).
                if (rotaryBailout.onScroll(event.verticalScrollPixels)) {
                    messageManager.sendRotaryBailout()
                    true
                } else {
                    false
                }
            }
    ) {
        val state = workoutState

        when {
            // Cadence floor collapse -> Gross-motor "Resume Slap" target
            state != null && state.isCadenceFloorActive -> {
                ResumeSlapOverlay(
                    onTapToResume = { messageManager.sendResumeSlap() },
                    currentCadence = state.cadenceRpm
                )
            }

            // Manual ERG Bailout ("The Clutch" active) -> Bailout amber/red overlay
            state != null && state.isBailoutActive -> {
                BailoutOverlay(
                    onResumeTapped = { messageManager.sendResumeSlap() }
                )
            }

            // Active or Paused Workout -> Telemetry view
            state != null && (state.isRunning || state.isPaused) -> {
                ActiveTelemetryScreen(
                    workoutState = state,
                    currentHeartRate = if (liveHr > 0) liveHr else state.heartRateBpm,
                    isAmbient = isAmbient,
                    onBailoutTriggered = { messageManager.sendRotaryBailout() }
                )
            }

            // Default: Standby
            else -> {
                StandbyScreen(
                    isPhoneConnected = isPhoneConnected,
                    currentHeartRate = liveHr,
                    onSyncRequested = { messageManager.requestWorkoutState() }
                )
            }
        }
    }
}

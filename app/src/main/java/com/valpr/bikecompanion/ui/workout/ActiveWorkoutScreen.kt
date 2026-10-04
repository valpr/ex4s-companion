package com.valpr.bikecompanion.ui.workout

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.CadenceEvaluator
import com.valpr.bikecompanion.shared.CadenceState
import com.valpr.bikecompanion.ui.components.WorkoutCanvasProfile
import com.valpr.bikecompanion.wearable.PhoneWearableManager
import com.valpr.bikecompanion.wearable.WatchHrStatus
import com.valpr.bikecompanion.wearable.WearableWatchState
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ActiveWorkoutScreen(
    sessionManager: WorkoutSessionManager,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    keepScreenOn: Boolean = true,
    watchState: WearableWatchState = WearableWatchState(),
    isInPipMode: Boolean = false,
    onEnterPip: (() -> Unit)? = null
) {
    val sessionState by sessionManager.sessionState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentView = LocalView.current
    val shouldKeepScreenOn = keepScreenOn && !isInPipMode
    DisposableEffect(shouldKeepScreenOn) {
        currentView.keepScreenOn = shouldKeepScreenOn
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    if (isInPipMode) {
        PipWorkoutContent(
            state = sessionState,
            modifier = modifier
        )
    } else {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color(0xFF0E1117)
        ) {
            if (isLandscape) {
                LandscapeWorkoutContent(
                    state = sessionState,
                    sessionManager = sessionManager,
                    onFinish = onFinish,
                    watchState = watchState,
                    onEnterPip = onEnterPip
                )
            } else {
                PortraitWorkoutContent(
                    state = sessionState,
                    sessionManager = sessionManager,
                    onFinish = onFinish,
                    watchState = watchState,
                    onEnterPip = onEnterPip
                )
            }
        }
    }
}

/**
 * True when the rider must control resistance manually: full Free Ride
 * (`workout == null`) or a `FreeRide`/`MaxEffort` segment inside a structured
 * workout (ERG disabled). Intensity chips are a no-op in these states, so the
 * UI swaps them for `-1/+1 Res` + the electronic shifter (AGENTS.md §2).
 *
 * Framework-free on [WorkoutSessionState] so it stays testable from plain
 * JUnit via [WorkoutSessionState] construction plus Robolectric semantics.
 */
internal fun isManualResistanceControl(state: WorkoutSessionState): Boolean {
    if (state.workout == null) return true
    state.currentPosition?.let { return !it.segment.isErgEnabled }
    // Pre-tick fallback: playhead hasn't emitted a position yet.
    state.workout.getSegmentAtTime(state.elapsedSeconds)?.let { return !it.segment.isErgEnabled }
    // Past the end (finished) with no ERG decision: nothing to scale, so show
    // manual controls rather than dead intensity chips (subagent review m5).
    if (state.status == SessionStatus.COMPLETED) return true
    return state.ergDecision?.state == ErgState.FREE_RIDE
}

@Composable
private fun PortraitWorkoutContent(
    state: WorkoutSessionState,
    sessionManager: WorkoutSessionManager,
    onFinish: () -> Unit,
    watchState: WearableWatchState = WearableWatchState(),
    onEnterPip: (() -> Unit)? = null
) {
    val manualControl = isManualResistanceControl(state)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Status
        WorkoutHeaderBar(state = state, onEnterPip = onEnterPip)

        // Watch HR link status (live / stale / disconnected)
        WatchHrStatusRow(watchState = watchState)

        // Active Coaching Cue Banner
        CoachingCueBanner(state = state)

        // The Big Three (Power, Cadence, HR/Resistance)
        TheBigThree(state = state, watchState = watchState)

        if (state.workout != null) {
            // Target vs Actual Gauge (auto-hides when target is null, e.g. FreeRide segment)
            TargetVsActualBar(state = state)

            // The Canvas Profile (for structured workouts)
            WorkoutCanvasProfile(
                workout = state.workout,
                elapsedSeconds = state.elapsedSeconds,
                modifier = Modifier.fillMaxWidth(),
                height = 130.dp
            )

            if (manualControl) {
                // FreeRide / MaxEffort segment inside a structured workout:
                // ERG is off so intensity chips are a no-op — expose the
                // manual electronic shifter here (AGENTS.md §2).
                FreeRideResistancePanel(
                    currentResistance = state.latestTelemetry.resistanceLevel,
                    onResistanceChange = { sessionManager.setManualResistance(it) },
                    resistanceRange = state.bikeCapabilities.resistanceRange
                )
            } else {
                // The Clutch (Manual ERG Bailout Button)
                ClutchButton(state = state, onToggleClutch = { sessionManager.toggleClutch() })
            }
        } else {
            // Free Ride: Electronic Resistance Shifter & Live Stats Panel
            FreeRideResistancePanel(
                currentResistance = state.latestTelemetry.resistanceLevel,
                onResistanceChange = { sessionManager.setManualResistance(it) },
                resistanceRange = state.bikeCapabilities.resistanceRange
            )
        }

        // Controls (Pause, Resume, Stop, Intensity / Resistance)
        WorkoutControlsBar(
            state = state,
            sessionManager = sessionManager,
            onFinish = onFinish
        )
    }
}

@Composable
private fun LandscapeWorkoutContent(
    state: WorkoutSessionState,
    sessionManager: WorkoutSessionManager,
    onFinish: () -> Unit,
    watchState: WearableWatchState = WearableWatchState(),
    onEnterPip: (() -> Unit)? = null
) {
    val manualControl = isManualResistanceControl(state)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Column: Big Three + (Clutch if structured, or ride stats if free ride)
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            WorkoutHeaderBar(state = state, onEnterPip = onEnterPip)
            WatchHrStatusRow(watchState = watchState)
            TheBigThree(state = state, watchState = watchState)
            if (state.workout != null && !manualControl) {
                ClutchButton(state = state, onToggleClutch = { sessionManager.toggleClutch() })
            } else {
                FreeRideStatsRow(
                    distanceKm = state.latestTelemetry.distanceKm,
                    speedKmh = state.latestTelemetry.speedKmh,
                    watts = state.latestTelemetry.estimatedWatts
                )
            }
        }

        // Right Column: Canvas + Target Bar + Controls (or FreeRide Resistance Panel + Controls)
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (state.workout != null) {
                CoachingCueBanner(state = state)
                TargetVsActualBar(state = state)
                WorkoutCanvasProfile(
                    workout = state.workout,
                    elapsedSeconds = state.elapsedSeconds,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    height = 110.dp
                )
                if (manualControl) {
                    FreeRideResistancePanel(
                        currentResistance = state.latestTelemetry.resistanceLevel,
                        onResistanceChange = { sessionManager.setManualResistance(it) },
                        modifier = Modifier.weight(1f),
                        resistanceRange = state.bikeCapabilities.resistanceRange,
                        enableInnerScroll = true
                    )
                }
            } else {
                FreeRideResistancePanel(
                    currentResistance = state.latestTelemetry.resistanceLevel,
                    onResistanceChange = { sessionManager.setManualResistance(it) },
                    modifier = Modifier.weight(1f),
                    resistanceRange = state.bikeCapabilities.resistanceRange,
                    enableInnerScroll = true
                )
            }

            WorkoutControlsBar(
                state = state,
                sessionManager = sessionManager,
                onFinish = onFinish
            )
        }
    }
}

@Composable
private fun WorkoutHeaderBar(
    state: WorkoutSessionState,
    onEnterPip: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                state.workout?.name ?: "Free Ride",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val segmentInfo = state.currentPosition?.let {
                "Step ${it.segmentIndex + 1}/${it.totalSegments} • ${it.segmentRemainingSeconds}s remaining"
            } ?: if (state.totalSeconds > 0) "${state.formattedRemainingTime} remaining" else "Open session"
            Text(
                segmentInfo,
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Time Counter & PiP Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                state.formattedElapsedTime,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00E676),
                maxLines = 1,
                softWrap = false
            )
            if (onEnterPip != null) {
                IconButton(
                    onClick = onEnterPip,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Enter Picture-in-Picture",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun WatchHrStatusRow(watchState: WearableWatchState, modifier: Modifier = Modifier) {
    // 1s ticker so LIVE flips to STALE even when no new batches arrive to
    // trigger recomposition. Gated to the only case that changes with time.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val needsTicker = watchState.isConnected && watchState.lastHeartRateTimestampMs > 0L
    LaunchedEffect(needsTicker, watchState.lastHeartRateTimestampMs) {
        nowMs = System.currentTimeMillis()
        if (!needsTicker) return@LaunchedEffect
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val status = PhoneWearableManager.resolveWatchHrStatus(watchState, nowMs)
    val (dotColor, text, textColor) = when (status) {
        WatchHrStatus.LIVE -> Triple(
            Color(0xFF00E676),
            "${watchState.nodeName.ifBlank { "Watch" }} • Live HR",
            Color(0xFF00E676)
        )
        WatchHrStatus.STALE -> {
            val ageSec = ((nowMs - watchState.lastHeartRateTimestampMs) / 1000L).coerceAtLeast(0L)
            Triple(
                Color(0xFFFFB300),
                "HR stale • last update ${ageSec}s ago",
                Color(0xFFFFB300)
            )
        }
        WatchHrStatus.NO_DATA -> Triple(
            Color(0xFF29B6F6),
            "${watchState.nodeName.ifBlank { "Watch" }} connected • waiting for HR…",
            Color(0xFFB0BEC5)
        )
        WatchHrStatus.DISCONNECTED -> Triple(
            Color(0xFF666666),
            "Watch disconnected",
            Color(0xFF888888)
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun CoachingCueBanner(state: WorkoutSessionState) {
    AnimatedVisibility(
        visible = state.activeCues.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val cue = state.activeCues.firstOrNull() ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF004D40))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00E676))
                Spacer(modifier = Modifier.width(8.dp))
                Text(cue.message, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TheBigThree(state: WorkoutSessionState, watchState: WearableWatchState = WearableWatchState()) {
    val telem = state.latestTelemetry
    val targetWatts = state.targetWatts

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Live Power
        BigMetricTile(
            label = "POWER",
            value = "${telem.estimatedWatts}",
            unit = "W",
            target = targetWatts?.let { "TARGET ${it}W" },
            accentColor = Color(0xFF00E676),
            modifier = Modifier.weight(1f)
        )

        // 2. Live Cadence
        val cadenceFeedback = CadenceEvaluator.evaluate(
            actualCadence = telem.cadenceRpm,
            targetCadence = state.targetCadence,
            preferredCadence = state.athletePreferredCadence
        )
        val cadenceColor = when (cadenceFeedback.colorToken) {
            "GREEN" -> Color(0xFF00E676)
            "AMBER" -> Color(0xFFFFB300)
            "CYAN" -> Color(0xFF29B6F6)
            else -> Color(0xFF888888)
        }
        val cadenceTargetColor = when (cadenceFeedback.state) {
            CadenceState.TOO_SLOW -> Color(0xFFFFB300)
            CadenceState.ON_TARGET -> Color(0xFF00E676)
            else -> Color.White.copy(alpha = 0.7f)
        }
        BigMetricTile(
            label = "CADENCE",
            value = "${telem.cadenceRpm}",
            unit = "RPM",
            target = cadenceFeedback.displayLabel,
            accentColor = cadenceColor,
            targetColor = cadenceTargetColor,
            modifier = Modifier.weight(1f)
        )

        // 3. Heart Rate or Speed / Resistance
        if (state.currentHeartRate > 0) {
            // Shared zone thresholds (same as watch) based on athlete HR parameters and Karvonen preference.
            val zone = com.valpr.bikecompanion.shared.HrZone.zoneNumber(
                bpm = state.currentHeartRate,
                maxHr = state.athleteMaxHr,
                restingHr = state.athleteRestingHr,
                useKarvonen = state.useKarvonenZones
            )
            // A frozen HR number must never present as live: dim the tile and
            // qualify it whenever the link is not actively delivering batches
            // (STALE after the threshold, OFFLINE once the node drops).
            val hrStatus = PhoneWearableManager.resolveWatchHrStatus(
                watchState,
                System.currentTimeMillis()
            )
            val hrQualifier = when (hrStatus) {
                WatchHrStatus.STALE -> "STALE"
                WatchHrStatus.DISCONNECTED -> "OFFLINE"
                else -> null
            }
            val hrColor = when {
                hrQualifier != null -> Color(0xFF888888)
                state.isCriticalHrActive -> Color(0xFFFF1744) // Critical Alert Red
                zone >= 5 -> Color(0xFFFF5252) // Zone 5 / Max Effort
                zone == 4 -> Color(0xFFFFAB00) // Zone 4 / Threshold
                zone == 3 -> Color(0xFF00E676) // Zone 3 / Tempo
                else -> Color(0xFF29B6F6) // Zone 1-2 / Aerobic
            }
            val baseLabel = if (state.isCriticalHrActive) "CRITICAL CAPPED" else "L${telem.resistanceLevel} • %.0f km/h".format(telem.speedKmh)
            val targetLabel = if (hrQualifier != null) "$hrQualifier • $baseLabel" else baseLabel
            BigMetricTile(
                label = if (hrQualifier != null) "HEART RATE ($hrQualifier)" else "HEART RATE",
                value = "${state.currentHeartRate}",
                unit = "BPM",
                target = targetLabel,
                accentColor = hrColor,
                modifier = Modifier.weight(1f)
            )
        } else {
            BigMetricTile(
                label = "RESISTANCE",
                value = "L${telem.resistanceLevel}",
                unit = "${state.bikeCapabilities.resistanceRange.last}",
                target = "%.1f km/h".format(telem.speedKmh),
                accentColor = Color(0xFFFFB300),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BigMetricTile(
    label: String,
    value: String,
    unit: String,
    target: String?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    targetColor: Color = Color.White.copy(alpha = 0.7f)
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232A)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(unit, fontSize = 10.sp, color = Color(0xFF888888), modifier = Modifier.padding(bottom = 6.dp))
            }

            if (target != null) {
                Text(
                    target,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = targetColor
                )
            }
        }
    }
}

@Composable
private fun TargetVsActualBar(state: WorkoutSessionState) {
    val target = state.targetWatts ?: return
    val actual = state.latestTelemetry.estimatedWatts
    val diff = actual - target

    val barColor = when {
        abs(diff) <= 8 -> Color(0xFF00E676) // Spot on (Green)
        diff > 8 -> Color(0xFFFF7043) // Too high (Orange)
        else -> Color(0xFFFFB300) // Too low (Yellow)
    }

    val progressFraction = (actual.toFloat() / (target * 1.5f)).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TARGET vs ACTUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            val diffText = if (diff >= 0) "+${diff}W" else "${diff}W"
            Text(diffText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = barColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFF262C36)
        )
    }
}

@Composable
private fun ClutchButton(
    state: WorkoutSessionState,
    onToggleClutch: () -> Unit
) {
    val ergState = state.ergDecision?.state ?: ErgState.INACTIVE

    val (buttonColor, textColor, text) = when (ergState) {
        ErgState.MANUAL_BAILOUT -> Triple(
            Color(0xFF00E676),
            Color.Black,
            "RESUME ERG MODE"
        )
        ErgState.CADENCE_FLOOR_BAILOUT -> Triple(
            Color(0xFFFF9100),
            Color.Black,
            "BAILOUT ACTIVE — SPIN >75 RPM (${state.ergDecision?.consecutiveRecoverySeconds ?: 0}/3s)"
        )
        ErgState.ACTIVE -> Triple(
            Color(0xFFD32F2F),
            Color.White,
            "THE CLUTCH (BAILOUT)"
        )
        else -> Triple(
            Color(0xFF37474F),
            Color.White,
            "FREE RIDE"
        )
    }

    Button(
        onClick = onToggleClutch,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Black, color = textColor)
    }
}

@Composable
private fun WorkoutControlsBar(
    state: WorkoutSessionState,
    sessionManager: WorkoutSessionManager,
    onFinish: () -> Unit
) {
    val manualControl = isManualResistanceControl(state)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!manualControl && state.workout != null) {
            // Structured workout: Intensity scaling chips
            OutlinedButton(
                onClick = { sessionManager.adjustIntensity(-0.05f) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("-5%", fontSize = 12.sp, maxLines = 1)
            }

            val scalePercent = (state.intensityScale * 100).roundToInt()
            Text(
                "$scalePercent%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )

            OutlinedButton(
                onClick = { sessionManager.adjustIntensity(0.05f) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("+5%", fontSize = 12.sp, maxLines = 1)
            }
        } else {
            // Free Ride: Quick Resistance shift buttons
            OutlinedButton(
                onClick = { sessionManager.adjustManualResistance(-1) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text("-1 Res", fontSize = 12.sp, maxLines = 1)
            }

            Text(
                "L${state.latestTelemetry.resistanceLevel}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFB300),
                maxLines = 1
            )

            OutlinedButton(
                onClick = { sessionManager.adjustManualResistance(1) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text("+1 Res", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Play/Pause
        if (state.status == SessionStatus.RUNNING) {
            Button(
                onClick = { sessionManager.pauseWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
        } else if (state.status == SessionStatus.PAUSED) {
            Button(
                onClick = { sessionManager.resumeWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black)
            }
        }

        // Finish / Stop — never allow shrink/clip at the row end
        Button(
            onClick = {
                sessionManager.stopWorkout()
                onFinish()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeRideResistancePanel(
    currentResistance: Int,
    onResistanceChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    resistanceRange: IntRange = 1..32,
    enableInnerScroll: Boolean = false
) {
    val presets = ResistancePresets.forRange(resistanceRange)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232A)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = if (enableInnerScroll) {
                Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.padding(12.dp)
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ELECTRONIC RESISTANCE SHIFTER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Text(
                    "LEVEL $currentResistance",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFB300)
                )
            }

            // Gross motor +/- buttons & current level display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onResistanceChange((currentResistance - 1).coerceAtLeast(resistanceRange.first)) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C323D))
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease Resistance", tint = Color.White)
                }

                Text(
                    "L$currentResistance",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFB300)
                )

                IconButton(
                    onClick = { onResistanceChange((currentResistance + 1).coerceAtMost(resistanceRange.last)) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C323D))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase Resistance", tint = Color.White)
                }
            }

            // Continuous discrete slider
            Slider(
                value = currentResistance.toFloat().coerceIn(resistanceRange.first.toFloat(), resistanceRange.last.toFloat()),
                onValueChange = { onResistanceChange(it.roundToInt().coerceIn(resistanceRange)) },
                valueRange = resistanceRange.first.toFloat()..resistanceRange.last.toFloat(),
                steps = (resistanceRange.last - resistanceRange.first - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB300),
                    activeTrackColor = Color(0xFFFFB300),
                    inactiveTrackColor = Color(0xFF2C323D)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Preset Quick-Tap Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { level ->
                    val isSelected = currentResistance == level
                    Button(
                        onClick = { onResistanceChange(level) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFFFFB300) else Color(0xFF2C323D),
                            contentColor = if (isSelected) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Text("L$level", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeRideStatsRow(
    distanceKm: Double,
    speedKmh: Double,
    watts: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232A)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DISTANCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("%.2f km".format(distanceKm), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPEED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("%.1f km/h".format(speedKmh), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WORK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("${watts}W", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
            }
        }
    }
}

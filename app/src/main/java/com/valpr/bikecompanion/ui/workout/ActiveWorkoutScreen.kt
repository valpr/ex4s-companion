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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.ui.components.WorkoutCanvasProfile
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ActiveWorkoutScreen(
    sessionManager: WorkoutSessionManager,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionState by sessionManager.sessionState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0E1117)
    ) {
        if (isLandscape) {
            LandscapeWorkoutContent(
                state = sessionState,
                sessionManager = sessionManager,
                onFinish = onFinish
            )
        } else {
            PortraitWorkoutContent(
                state = sessionState,
                sessionManager = sessionManager,
                onFinish = onFinish
            )
        }
    }
}

@Composable
private fun PortraitWorkoutContent(
    state: WorkoutSessionState,
    sessionManager: WorkoutSessionManager,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Status
        WorkoutHeaderBar(state = state)

        // Active Coaching Cue Banner
        CoachingCueBanner(state = state)

        // The Big Three (Power, Cadence, HR/Resistance)
        TheBigThree(state = state)

        if (state.workout != null) {
            // Target vs Actual Gauge
            TargetVsActualBar(state = state)

            // The Canvas Profile (for structured workouts)
            WorkoutCanvasProfile(
                workout = state.workout,
                elapsedSeconds = state.elapsedSeconds,
                modifier = Modifier.fillMaxWidth(),
                height = 130.dp
            )

            // The Clutch (Manual ERG Bailout Button)
            ClutchButton(state = state, onToggleClutch = { sessionManager.toggleClutch() })
        } else {
            // Free Ride: Electronic Resistance Shifter & Live Stats Panel
            FreeRideResistancePanel(
                currentResistance = state.latestTelemetry.resistanceLevel,
                onResistanceChange = { sessionManager.setManualResistance(it) }
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
    onFinish: () -> Unit
) {
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
            WorkoutHeaderBar(state = state)
            TheBigThree(state = state)
            if (state.workout != null) {
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
            } else {
                FreeRideResistancePanel(
                    currentResistance = state.latestTelemetry.resistanceLevel,
                    onResistanceChange = { sessionManager.setManualResistance(it) },
                    modifier = Modifier.weight(1f)
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
private fun WorkoutHeaderBar(state: WorkoutSessionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                state.workout?.name ?: "Free Ride",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            val segmentInfo = state.currentPosition?.let {
                "Step ${it.segmentIndex + 1}/${it.totalSegments} • ${it.segmentRemainingSeconds}s remaining"
            } ?: if (state.totalSeconds > 0) "${state.formattedRemainingTime} remaining" else "Open session"
            Text(
                segmentInfo,
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA)
            )
        }

        // Time Counter
        Text(
            state.formattedElapsedTime,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E676)
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
private fun TheBigThree(state: WorkoutSessionState) {
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
        BigMetricTile(
            label = "CADENCE",
            value = "${telem.cadenceRpm}",
            unit = "RPM",
            target = state.targetCadence?.let { "TARGET ${it}" },
            accentColor = Color(0xFF29B6F6),
            modifier = Modifier.weight(1f)
        )

        // 3. Heart Rate or Speed / Resistance
        if (state.currentHeartRate > 0) {
            // Shared zone thresholds (same as watch) based on athlete max HR.
            val zone = com.valpr.bikecompanion.shared.HrZone.zoneNumber(
                state.currentHeartRate, state.athleteMaxHr
            )
            val hrColor = when {
                state.isCriticalHrActive -> Color(0xFFFF1744) // Critical Alert Red
                zone >= 5 -> Color(0xFFFF5252) // Zone 5 / Max Effort
                zone == 4 -> Color(0xFFFFAB00) // Zone 4 / Threshold
                zone == 3 -> Color(0xFF00E676) // Zone 3 / Tempo
                else -> Color(0xFF29B6F6) // Zone 1-2 / Aerobic
            }
            val targetLabel = if (state.isCriticalHrActive) "CRITICAL CAPPED" else "L${telem.resistanceLevel} • %.0f km/h".format(telem.speedKmh)
            BigMetricTile(
                label = "HEART RATE",
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
                unit = "32",
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
    modifier: Modifier = Modifier
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
                    color = Color.White.copy(alpha = 0.7f)
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
        abs(diff) <= 8 -> Color(0xFF00E676)  // Spot on (Green)
        diff > 8 -> Color(0xFFFF7043)        // Too high (Orange)
        else -> Color(0xFFFFB300)            // Too low (Yellow)
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
            trackColor = Color(0xFF262C36),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.workout != null) {
            // Structured workout: Intensity scaling chips
            OutlinedButton(
                onClick = { sessionManager.adjustIntensity(-0.05f) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("-5%", fontSize = 12.sp)
            }

            val scalePercent = (state.intensityScale * 100).toInt()
            Text(
                "$scalePercent%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            OutlinedButton(
                onClick = { sessionManager.adjustIntensity(0.05f) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("+5%", fontSize = 12.sp)
            }
        } else {
            // Free Ride: Quick Resistance shift buttons
            OutlinedButton(
                onClick = { sessionManager.adjustManualResistance(-1) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("-1 Res", fontSize = 12.sp)
            }

            Text(
                "L${state.latestTelemetry.resistanceLevel}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFB300)
            )

            OutlinedButton(
                onClick = { sessionManager.adjustManualResistance(1) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("+1 Res", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Play/Pause
        if (state.status == SessionStatus.RUNNING) {
            Button(
                onClick = { sessionManager.pauseWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
            ) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
        } else if (state.status == SessionStatus.PAUSED) {
            Button(
                onClick = { sessionManager.resumeWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black)
            }
        }

        // Finish / Stop
        Button(
            onClick = {
                sessionManager.stopWorkout()
                onFinish()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
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
    modifier: Modifier = Modifier
) {
    val presets = listOf(1, 4, 8, 12, 16, 20, 24, 28, 32)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232A)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                    onClick = { onResistanceChange((currentResistance - 1).coerceAtLeast(1)) },
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
                    onClick = { onResistanceChange((currentResistance + 1).coerceAtMost(32)) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C323D))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase Resistance", tint = Color.White)
                }
            }

            // Continuous discrete slider 1..32
            Slider(
                value = currentResistance.toFloat(),
                onValueChange = { onResistanceChange(it.roundToInt().coerceIn(1, 32)) },
                valueRange = 1f..32f,
                steps = 30,
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
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("L$level", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

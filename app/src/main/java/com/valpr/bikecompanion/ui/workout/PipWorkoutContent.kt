package com.valpr.bikecompanion.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.shared.HrZone
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlin.math.abs

@Composable
fun PipWorkoutContent(
    state: WorkoutSessionState,
    modifier: Modifier = Modifier
) {
    val telem = state.latestTelemetry
    val isManual = isManualResistanceControl(state)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1117))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("pip_content"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Row 1: Power + Cadence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Power (display-smoothed; recording stays raw)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.testTag("pip_power")
                ) {
                    Text(
                        text = "${state.displayWattsOrRaw}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E676),
                        maxLines = 1
                    )
                    Text(
                        text = "W",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676).copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                        maxLines = 1
                    )
                }

                // Cadence
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.testTag("pip_cadence")
                ) {
                    Text(
                        text = "${telem.cadenceRpm}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "RPM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                        maxLines = 1
                    )
                }
            }

            // Row 2: Heart Rate (or Resistance if no HR) + Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // HR or Resistance
                if (state.currentHeartRate > 0) {
                    val zone = HrZone.zoneNumber(
                        bpm = state.currentHeartRate,
                        maxHr = state.athleteMaxHr,
                        restingHr = state.athleteRestingHr,
                        useKarvonen = state.useKarvonenZones
                    )
                    val hrColor = when {
                        state.isCriticalHrActive -> Color(0xFFFF1744) // Critical Alert Red
                        zone >= 5 -> Color(0xFFFF5252) // Zone 5 / Max Effort
                        zone == 4 -> Color(0xFFFFAB00) // Zone 4 / Threshold
                        zone == 3 -> Color(0xFF00E676) // Zone 3 / Tempo
                        else -> Color(0xFF29B6F6) // Zone 1-2 / Aerobic
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("pip_hr")
                    ) {
                        Text(
                            text = "♥ ${state.currentHeartRate}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = hrColor,
                            maxLines = 1
                        )
                        Text(
                            text = " BPM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = hrColor.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("pip_secondary")
                    ) {
                        Text(
                            text = "L${telem.resistanceLevel}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFFB300),
                            maxLines = 1
                        )
                        Text(
                            text = " • %.0f km/h".format(telem.speedKmh),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                }

                // Timer
                Text(
                    text = state.formattedElapsedTime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.testTag("pip_timer")
                )
            }

            // Row 3: Target delta / Status / Bailout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText: String
                val statusColor: Color

                val isBailout = state.ergDecision?.state == ErgState.MANUAL_BAILOUT ||
                    state.ergDecision?.state == ErgState.CADENCE_FLOOR_BAILOUT

                if (state.status == SessionStatus.COMPLETED) {
                    statusText = "COMPLETED"
                    statusColor = Color(0xFF00E676)
                } else if (state.status == SessionStatus.PAUSED) {
                    statusText = "PAUSED"
                    statusColor = Color(0xFFFFB300)
                } else if (isBailout) {
                    statusText = "BAILOUT"
                    statusColor = Color(0xFFFF1744)
                } else if (state.isCriticalHrActive) {
                    statusText = "CRITICAL HR"
                    statusColor = Color(0xFFFF1744)
                } else if (state.targetWatts != null && !isManual) {
                    val target = state.targetWatts
                    val actual = state.displayWattsOrRaw
                    val diff = actual - target
                    val diffStr = if (diff >= 0) "+${diff}W" else "${diff}W"
                    statusText = "TARGET ${target}W ($diffStr)"
                    statusColor = when {
                        abs(diff) <= 8 -> Color(0xFF00E676)
                        diff > 8 -> Color(0xFFFF7043)
                        else -> Color(0xFFFFB300)
                    }
                } else {
                    // Free Ride or manual resistance segment
                    statusText = state.workout?.name ?: "FREE RIDE"
                    statusColor = Color(0xFFAAAAAA)
                }

                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 4.dp)
                        .testTag("pip_status")
                )

                // Remaining time or distance if available
                val subText = if (state.totalSeconds > 0) {
                    "-${state.formattedRemainingTime}"
                } else {
                    "%.1f km".format(telem.distanceKm)
                }
                Text(
                    text = subText,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

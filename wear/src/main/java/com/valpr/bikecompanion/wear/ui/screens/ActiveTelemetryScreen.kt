package com.valpr.bikecompanion.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.valpr.bikecompanion.shared.WorkoutStateMessage
import com.valpr.bikecompanion.wear.data.HrZone

/**
 * 2. Active Telemetry View (Ambient Mode Supported)
 * Minimal pixel illumination on black OLED background to maximize battery life.
 * Displays large HR typography colored by HR Zone, with elapsed time and ERG bailout trigger.
 */
@Composable
fun ActiveTelemetryScreen(
    workoutState: WorkoutStateMessage,
    currentHeartRate: Int,
    isAmbient: Boolean,
    onBailoutTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hrZone = HrZone.fromBpm(currentHeartRate, workoutState.athleteMaxHr)
    val displayHr = if (currentHeartRate > 0) "$currentHeartRate" else "—"

    // In ambient mode, use monochrome white/gray to preserve battery; in active, use Zone color
    val hrColor = if (isAmbient) {
        Color.White
    } else {
        if (workoutState.isHrCapped) Color(0xFFFF1744) else hrZone.color
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Elapsed Time & Workout Name
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = workoutState.formattedElapsedTime,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isAmbient) Color.LightGray else Color(0xFF00E676)
                )

                if (!isAmbient && workoutState.workoutName.isNotBlank()) {
                    Text(
                        text = workoutState.workoutName,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }

            // Center: Massive Heart Rate Metric
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayHr,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = hrColor,
                    textAlign = TextAlign.Center
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BPM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAmbient) Color.Gray else hrColor.copy(alpha = 0.8f)
                    )

                    if (!isAmbient && currentHeartRate > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${hrZone.label.uppercase()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = hrColor.copy(alpha = 0.9f)
                        )
                    }
                }

                if (!isAmbient && workoutState.targetWatts > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${workoutState.currentWatts}W / ${workoutState.targetWatts}W",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }

            // Bottom: Bailout Button / Rotary Crown Hint (Hidden in ambient to save pixels)
            if (!isAmbient) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF261200))
                        .clickable { onBailoutTriggered() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BAILOUT ⚙",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

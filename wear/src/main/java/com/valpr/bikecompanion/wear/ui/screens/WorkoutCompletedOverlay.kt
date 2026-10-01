package com.valpr.bikecompanion.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.valpr.bikecompanion.shared.WorkoutStateMessage

/**
 * Workout Completed Overlay:
 * Displayed when a structured or free-ride workout ends.
 * Shows completion banner, elapsed duration, and dismiss prompt.
 */
@Composable
fun WorkoutCompletedOverlay(
    workoutState: WorkoutStateMessage,
    currentHeartRate: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF002B11)) // Dark emerald background
            .clickable(role = Role.Button, onClickLabel = "Dismiss workout complete") { onDismiss() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WORKOUT COMPLETE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00E676),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            val displayName = workoutState.workoutName.ifBlank { "Ride Finished" }
            Text(
                text = displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = workoutState.formattedElapsedTime,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )

            if (currentHeartRate > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "♥ $currentHeartRate BPM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF29B6F6)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap to dismiss",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

package com.valpr.bikecompanion.ui.workout

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pre-workout countdown overlay displayed over [ActiveWorkoutScreen] prior to session start.
 * Gives the rider a 3-2-1 window to clip in, observe initial target power, and prepare.
 *
 * Supports skipping ("Start Now") to enter the workout immediately or cancelling to return
 * to the dashboard cleanly without saving a 0-duration session.
 */
@Composable
fun WorkoutCountdownOverlay(
    countdownSeconds: Int,
    workoutName: String,
    targetWatts: Int?,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val podSize = if (isLandscape) 96.dp else 130.dp
    val numberFontSize = if (isLandscape) 56.sp else 76.sp

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("workout_countdown_overlay"),
        color = Color(0xF20E1117)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "GET READY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676),
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = workoutName,
                    fontSize = if (isLandscape) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("countdown_title_text")
                )

                if (targetWatts != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Starting Target: ${targetWatts}W",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB0BEC5),
                        modifier = Modifier.testTag("countdown_target_watts")
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 14.dp else 24.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(podSize)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2638))
                        .border(2.dp, Color(0xFF00E676).copy(alpha = 0.8f), CircleShape)
                ) {
                    Text(
                        text = "$countdownSeconds",
                        fontSize = numberFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E676),
                        modifier = Modifier.testTag("countdown_seconds_text")
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 28.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        modifier = Modifier.testTag("countdown_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel")
                    }

                    Button(
                        onClick = onSkip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("countdown_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

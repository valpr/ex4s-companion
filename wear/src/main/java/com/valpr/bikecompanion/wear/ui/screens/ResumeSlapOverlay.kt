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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/**
 * 3. The "Resume Slap" Target:
 * When the phone detects a cadence crash (or ERG suspension),
 * the watch UI is entirely replaced by a massive, high-contrast, bright green
 * "TAP TO RESUME" target allowing for a gross-motor hand slap to re-engage the workout.
 */
@Composable
fun ResumeSlapOverlay(onTapToResume: () -> Unit, currentCadence: Int = 0, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF00E676)) // Full-screen intense green
            .clickable { onTapToResume() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CADENCE DROP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "TAP TO\nRESUME",
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "CADENCE $currentCadence RPM — SPIN UP PAST 75",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Spun down to recovery • Slap screen to re-engage",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

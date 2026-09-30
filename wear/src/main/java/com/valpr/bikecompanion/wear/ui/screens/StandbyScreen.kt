package com.valpr.bikecompanion.wear.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/**
 * 1. Standby View:
 * Simple text indicating "Waiting for Phone" or "Ready."
 * Prevents accidental workout starts from the watch.
 */
@Composable
fun StandbyScreen(
    isPhoneConnected: Boolean,
    currentHeartRate: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Connection Dot & App Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isPhoneConnected) Color(0xFF00E676) else Color(0xFFFF1744))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EX-4S COMPANION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Status Text
            val statusTitle = if (isPhoneConnected) "PHONE READY" else "WAITING FOR PHONE"
            val statusColor = if (isPhoneConnected) Color.White else Color.Gray
            Text(
                text = statusTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = statusColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            val helperText = if (isPhoneConnected) {
                "Select a workout or Quick Start on your phone"
            } else {
                "Ensure phone app is running nearby"
            }
            Text(
                text = helperText,
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            if (currentHeartRate > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "♥ $currentHeartRate BPM",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF29B6F6)
                )
            }
        }
    }
}

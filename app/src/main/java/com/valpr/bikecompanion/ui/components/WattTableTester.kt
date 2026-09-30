package com.valpr.bikecompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.data.EchelonWattTable
import com.valpr.bikecompanion.ui.theme.AccentCyan
import com.valpr.bikecompanion.ui.theme.AccentGreen
import com.valpr.bikecompanion.ui.theme.DarkSurface
import com.valpr.bikecompanion.ui.theme.DarkSurfaceVariant
import com.valpr.bikecompanion.ui.theme.TextMuted

@Composable
fun WattTableTester(
    modifier: Modifier = Modifier
) {
    var testResistance by remember { mutableIntStateOf(16) }
    var testCadence by remember { mutableFloatStateOf(85f) }

    val calculatedWatts = EchelonWattTable.calculateWatts(testResistance, testCadence.toDouble())

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WATT TABLE VERIFICATION (33×11)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextMuted
                )
                Text(
                    text = "%.1f W".format(calculatedWatts),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Test Resistance Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Test Resistance: Level $testResistance", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = testResistance.toFloat(),
                onValueChange = { testResistance = it.toInt() },
                valueRange = 1f..32f,
                steps = 30,
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )

            // Test Cadence Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Test Cadence: ${testCadence.toInt()} RPM", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = testCadence,
                onValueChange = { testCadence = it },
                valueRange = 0f..120f,
                steps = 119,
                colors = SliderDefaults.colors(
                    thumbColor = AccentGreen,
                    activeTrackColor = AccentGreen,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )
        }
    }
}

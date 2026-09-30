package com.valpr.bikecompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.ui.theme.AccentAmber
import com.valpr.bikecompanion.ui.theme.DarkBackground
import com.valpr.bikecompanion.ui.theme.DarkSurface
import com.valpr.bikecompanion.ui.theme.DarkSurfaceVariant
import com.valpr.bikecompanion.ui.theme.TextMuted
import com.valpr.bikecompanion.ui.theme.TextPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResistanceControl(
    currentResistance: Int,
    onResistanceChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(1, 4, 8, 12, 16, 20, 24, 28, 32)

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
                    text = "RESISTANCE CONTROL",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextMuted
                )
                Text(
                    text = "LEVEL $currentResistance",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = AccentAmber
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gross motor +/- buttons and active value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onResistanceChange((currentResistance - 1).coerceAtLeast(1)) },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease Resistance",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "$currentResistance",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = AccentAmber
                )

                IconButton(
                    onClick = { onResistanceChange((currentResistance + 1).coerceAtMost(32)) },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase Resistance",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Continuous discrete slider (1..32)
            Slider(
                value = currentResistance.toFloat(),
                onValueChange = { onResistanceChange(it.toInt().coerceIn(1, 32)) },
                valueRange = 1f..32f,
                steps = 30, // 30 intermediate steps between 1 and 32
                colors = SliderDefaults.colors(
                    thumbColor = AccentAmber,
                    activeTrackColor = AccentAmber,
                    inactiveTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Preset Quick-Tap Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { level ->
                    val isSelected = currentResistance == level
                    Button(
                        onClick = { onResistanceChange(level) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) AccentAmber else DarkSurfaceVariant,
                            contentColor = if (isSelected) DarkBackground else TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "L$level",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

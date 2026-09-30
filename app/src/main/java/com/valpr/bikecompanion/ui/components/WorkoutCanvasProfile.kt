package com.valpr.bikecompanion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSegment

/**
 * Returns a high-contrast zone color corresponding to the given intensity factor (% of FTP).
 */
fun getZoneColor(intensityFactor: Float): Color {
    return when {
        intensityFactor <= 0.55f -> Color(0xFF64B5F6) // Zone 1 Recovery (Light Blue)
        intensityFactor <= 0.75f -> Color(0xFF2196F3) // Zone 2 Endurance (Blue)
        intensityFactor <= 0.90f -> Color(0xFF4CAF50) // Zone 3 Tempo (Green)
        intensityFactor <= 1.05f -> Color(0xFFFFB300) // Zone 4 Threshold / SweetSpot (Yellow/Amber)
        intensityFactor <= 1.20f -> Color(0xFFFF7043) // Zone 5 VO2 Max (Orange)
        else -> Color(0xFFE91E63)                     // Zone 6+ Anaerobic (Magenta/Red)
    }
}

/**
 * Canvas drawing the structured workout power profile with real-time moving playhead.
 */
@Composable
fun WorkoutCanvasProfile(
    workout: Workout,
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    showPlayhead: Boolean = true
) {
    val totalSeconds = workout.totalDurationSeconds.coerceAtLeast(1)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val maxIntensity = 1.35f // Normalize 135% FTP to top of canvas

            var currentX = 0f

            for (segment in workout.segments) {
                val segmentFraction = segment.durationSeconds.toFloat() / totalSeconds
                val segmentWidth = segmentFraction * canvasWidth

                when (segment) {
                    is WorkoutSegment.SteadyState -> {
                        val normalizedHeight = (segment.power / maxIntensity).coerceIn(0.1f, 1.0f) * canvasHeight
                        val topY = canvasHeight - normalizedHeight
                        val color = getZoneColor(segment.power)

                        drawRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft = Offset(currentX, topY),
                            size = Size(segmentWidth.coerceAtLeast(1f), normalizedHeight)
                        )
                    }
                    is WorkoutSegment.Warmup -> {
                        drawRamp(
                            startX = currentX,
                            width = segmentWidth,
                            powerStart = segment.powerLow,
                            powerEnd = segment.powerHigh,
                            maxIntensity = maxIntensity,
                            canvasHeight = canvasHeight
                        )
                    }
                    is WorkoutSegment.Cooldown -> {
                        drawRamp(
                            startX = currentX,
                            width = segmentWidth,
                            powerStart = segment.powerLow,
                            powerEnd = segment.powerHigh,
                            maxIntensity = maxIntensity,
                            canvasHeight = canvasHeight
                        )
                    }
                    is WorkoutSegment.Ramp -> {
                        drawRamp(
                            startX = currentX,
                            width = segmentWidth,
                            powerStart = segment.powerLow,
                            powerEnd = segment.powerHigh,
                            maxIntensity = maxIntensity,
                            canvasHeight = canvasHeight
                        )
                    }
                    is WorkoutSegment.FreeRide -> {
                        val normalizedHeight = 0.50f * canvasHeight
                        drawRect(
                            color = Color(0xFF78909C).copy(alpha = 0.5f),
                            topLeft = Offset(currentX, canvasHeight - normalizedHeight),
                            size = Size(segmentWidth.coerceAtLeast(1f), normalizedHeight)
                        )
                    }
                    is WorkoutSegment.MaxEffort -> {
                        val normalizedHeight = 0.95f * canvasHeight
                        drawRect(
                            color = Color(0xFFD500F9).copy(alpha = 0.7f),
                            topLeft = Offset(currentX, canvasHeight - normalizedHeight),
                            size = Size(segmentWidth.coerceAtLeast(1f), normalizedHeight)
                        )
                    }
                }

                // Vertical interval boundary divider
                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(currentX + segmentWidth, 0f),
                    end = Offset(currentX + segmentWidth, canvasHeight),
                    strokeWidth = 1f
                )

                currentX += segmentWidth
            }

            // Draw Real-time Playhead
            if (showPlayhead) {
                val playheadFraction = (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
                val playheadX = playheadFraction * canvasWidth

                // Shaded completed overlay
                drawRect(
                    color = Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset(0f, 0f),
                    size = Size(playheadX, canvasHeight)
                )

                // Neon playhead vertical needle
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, canvasHeight),
                    strokeWidth = 3f
                )

                // Top indicator dot
                drawCircle(
                    color = Color(0xFF00E676),
                    radius = 5f,
                    center = Offset(playheadX, 6f)
                )
            }
        }
    }
}

private fun DrawScope.drawRamp(
    startX: Float,
    width: Float,
    powerStart: Float,
    powerEnd: Float,
    maxIntensity: Float,
    canvasHeight: Float
) {
    val hStart = (powerStart / maxIntensity).coerceIn(0.1f, 1.0f) * canvasHeight
    val hEnd = (powerEnd / maxIntensity).coerceIn(0.1f, 1.0f) * canvasHeight
    val avgPower = (powerStart + powerEnd) / 2f
    val color = getZoneColor(avgPower)

    val path = Path().apply {
        moveTo(startX, canvasHeight)
        lineTo(startX, canvasHeight - hStart)
        lineTo(startX + width, canvasHeight - hEnd)
        lineTo(startX + width, canvasHeight)
        close()
    }

    drawPath(
        path = path,
        color = color.copy(alpha = 0.85f),
        style = Fill
    )
}

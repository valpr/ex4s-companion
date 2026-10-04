package com.valpr.bikecompanion.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.valpr.bikecompanion.shared.HrZone

/**
 * Personal HR zone semicircle drawn over the live BPM number.
 *
 * Five arcs proportional to the athlete's real BPM span ([HrZoneGaugeLogic]);
 * every zone at or below the current BPM lights up, the active zone partially.
 * No needle, no zone text label — the lit arcs plus the colored number carry it.
 *
 * @param isDimmed grays the whole gauge for STALE/OFFLINE links so a frozen
 * BPM never presents as live.
 */
@Composable
fun HrZoneGauge(
    bpm: Int,
    maxHr: Int,
    restingHr: Int,
    useKarvonenZones: Boolean,
    isDimmed: Boolean,
    modifier: Modifier = Modifier,
    zoneColors: List<Color> = HrZoneGaugeDefaults.zoneColors
) {
    val segments = remember(maxHr, restingHr, useKarvonenZones) {
        HrZoneGaugeLogic.computeSegments(maxHr, restingHr, useKarvonenZones)
    }
    val fills = remember(bpm, maxHr, restingHr, useKarvonenZones) {
        HrZoneGaugeLogic.fillFractions(bpm, maxHr, restingHr, useKarvonenZones)
    }
    val litColors = remember(isDimmed, zoneColors) {
        if (isDimmed) List(5) { HrZoneGaugeDefaults.dimmedLit } else zoneColors
    }
    val trackColors = remember(isDimmed, zoneColors) {
        if (isDimmed) {
            List(5) { HrZoneGaugeDefaults.dimmedTrack }
        } else {
            zoneColors.map { it.copy(alpha = 0.22f) }
        }
    }

    // Zone for accessibility/dimming assertions, from the same SSoT as the number color.
    val zone = remember(bpm, maxHr, restingHr, useKarvonenZones) {
        HrZone.zoneNumber(bpm, maxHr, restingHr, useKarvonenZones)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("hr_zone_gauge")
            .semantics {
                contentDescription =
                    if (isDimmed) {
                        "Heart rate zone gauge, dimmed, zone $zone of 5, $bpm BPM"
                    } else {
                        "Heart rate zone gauge, zone $zone of 5, $bpm BPM"
                    }
            }
    ) {
        val strokeWidthPx = 8.dp.toPx()
        val gapDegrees = 2f
        // Inset by half the stroke so round joins never clip at the canvas edge.
        val inset = strokeWidthPx / 2f + 1f
        val arcSize = Size(size.width - inset * 2f, (size.height - inset) * 2f)
        val arcTopLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        var startAngle = 180f
        segments.forEachIndexed { index, segment ->
            val zoneSweep = segment.sweepFraction * 180f
            if (zoneSweep <= 0f) {
                return@forEachIndexed
            }
            // Gaps sit between arcs, never at the outer 180°/0° ends.
            val leadingGap = if (index == 0) 0f else gapDegrees / 2f
            val trailingGap = if (index == segments.lastIndex) 0f else gapDegrees / 2f
            val barStart = startAngle + leadingGap
            val barSweep = (zoneSweep - leadingGap - trailingGap).coerceAtLeast(0f)
            if (barSweep > 0f) {
                drawArc(
                    color = trackColors.getOrElse(index) { Color.Gray },
                    startAngle = barStart,
                    sweepAngle = barSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                )
                val litSweep = barSweep * fills.getOrElse(index) { 0f }
                if (litSweep > 0.1f) {
                    drawArc(
                        color = litColors.getOrElse(index) { Color.Gray },
                        startAngle = barStart,
                        sweepAngle = litSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                    )
                }
            }
            startAngle += zoneSweep
        }
    }
}

object HrZoneGaugeDefaults {
    // Blue family for Z1/Z2 (matches the HR number, which uses one blue for
    // both aerobic zones), then the existing workout green/amber/red.
    val zoneColors: List<Color> = listOf(
        Color(0xFF81D4FA),
        Color(0xFF29B6F6),
        Color(0xFF00E676),
        Color(0xFFFFAB00),
        Color(0xFFFF5252)
    )
    val dimmedTrack: Color = Color(0xFF3A4048)
    val dimmedLit: Color = Color(0xFF888888)
}

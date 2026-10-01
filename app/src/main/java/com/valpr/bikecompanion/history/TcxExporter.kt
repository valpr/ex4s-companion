package com.valpr.bikecompanion.history

import com.valpr.bikecompanion.workout.WorkoutSummary
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Pure TCX builder (framework-free, plain-JUnit testable).
 * Emits a Garmin Training Center Database document importable by Strava,
 * Garmin Connect, Intervals.icu and TrainingPeaks.
 */
object TcxExporter {
    private val isoFormat: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun fileNameFor(ride: CompletedRide): String {
        val date = Instant.ofEpochMilli(ride.startTimeEpochMs.coerceAtLeast(0L))
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        val slug = ride.workoutName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "ride" }
        return "$date-$slug.tcx"
    }

    fun export(ride: CompletedRide): String = export(
        workoutName = ride.workoutName,
        startTimeEpochMs = ride.startTimeEpochMs,
        durationSeconds = ride.totalDurationSeconds,
        distanceKm = ride.totalDistanceKm,
        caloriesKcal = ride.totalCaloriesKcal,
        avgWatts = ride.avgWatts,
        maxWatts = ride.maxWatts,
        samples = ride.samples.map {
            TrackSample(
                offsetSeconds = it.elapsedSeconds,
                watts = it.watts,
                cadenceRpm = it.cadenceRpm,
                heartRateBpm = it.heartRateBpm,
                speedKmh = it.speedKmh
            )
        }
    )

    fun export(summary: WorkoutSummary): String = export(
        workoutName = summary.workoutName,
        startTimeEpochMs = summary.startTimeEpochMs,
        durationSeconds = summary.totalDurationSeconds,
        distanceKm = summary.totalDistanceKm,
        caloriesKcal = summary.totalCaloriesKcal,
        avgWatts = summary.avgWatts,
        maxWatts = summary.maxWatts,
        samples = summary.samples.map {
            TrackSample(
                offsetSeconds = it.elapsedSeconds,
                watts = it.watts,
                cadenceRpm = it.cadenceRpm,
                heartRateBpm = it.heartRateBpm,
                speedKmh = it.speedKmh
            )
        }
    )

    data class TrackSample(
        val offsetSeconds: Int = 0,
        val watts: Int = 0,
        val cadenceRpm: Int = 0,
        val heartRateBpm: Int = 0,
        val speedKmh: Double = 0.0
    )

    fun export(
        workoutName: String,
        startTimeEpochMs: Long,
        durationSeconds: Int,
        distanceKm: Double,
        caloriesKcal: Int,
        avgWatts: Int,
        maxWatts: Int,
        samples: List<TrackSample>
    ): String {
        val startMs = if (startTimeEpochMs > 0L) startTimeEpochMs else System.currentTimeMillis()
        val duration = durationSeconds.coerceAtLeast(1)
        val endMs = startMs + duration * 1000L
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\"")
        sb.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        sb.append(" xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2")
        sb.append(" http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\">\n")
        sb.append("  <Activities>\n")
        sb.append("    <Activity Sport=\"Biking\">\n")
        sb.append("      <Id>").append(isoInstant(startMs)).append("</Id>\n")
        sb.append("      <Lap StartTime=\"").append(isoInstant(startMs)).append("\">\n")
        sb.append("        <TotalTimeSeconds>").append(duration).append("</TotalTimeSeconds>\n")
        sb.append("        <DistanceMeters>")
            .append("%.2f".format(distanceKm.coerceAtLeast(0.0) * 1000.0))
            .append("</DistanceMeters>\n")
        sb.append("        <Calories>").append(caloriesKcal.coerceAtLeast(0)).append("</Calories>\n")
        sb.append("        <Intensity>Active</Intensity>\n")
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        sb.append("        <Track>\n")
        if (samples.isEmpty()) {
            // Zero-length guard: at least one trackpoint so parsers accept the file.
            appendTrackpoint(sb, startMs, endMs, startMs, 0.0, 0, 0, 0)
        } else {
            // Clamp every sample into [start, end] and collapse duplicate offsets.
            // Distance integrates speed over each sample's actual time delta so
            // gappy data stays consistent with the lap-level odometer total.
            var lastOffset = -1
            var distanceMeters = 0.0
            var emitted = 0
            val sorted = samples.sortedBy { it.offsetSeconds }
            for (sample in sorted) {
                val offset = sample.offsetSeconds.coerceIn(0, duration)
                if (offset <= lastOffset) {
                    continue
                }
                // First point integrates from session start; later points from the previous one.
                val dtSeconds = if (lastOffset < 0) offset else (offset - lastOffset).coerceAtLeast(0)
                lastOffset = offset
                // Approximate cumulative distance from speed when the bike odometer
                // is unavailable per-sample (indoor, no GPS lat/lon by design).
                distanceMeters += sample.speedKmh.coerceAtLeast(0.0) * 1000.0 * dtSeconds / 3600.0
                appendTrackpoint(
                    sb,
                    startMs,
                    endMs,
                    startMs + offset * 1000L,
                    distanceMeters,
                    sample.cadenceRpm,
                    sample.heartRateBpm,
                    sample.watts
                )
                emitted++
            }
            if (emitted == 0 && sorted.isNotEmpty()) {
                // Every sample collapsed (e.g. all offsets out-of-window): emit a
                // single end-of-ride point from the last sample instead of nothing.
                val last = sorted.last()
                appendTrackpoint(
                    sb,
                    startMs,
                    endMs,
                    endMs,
                    last.speedKmh.coerceAtLeast(0.0) * 1000.0 * duration / 3600.0,
                    last.cadenceRpm,
                    last.heartRateBpm,
                    last.watts
                )
            }
        }
        sb.append("        </Track>\n")
        sb.append("        <Extensions>\n")
        sb.append("          <LX xmlns=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\">\n")
        sb.append("            <AvgWatts>").append(avgWatts.coerceAtLeast(0)).append("</AvgWatts>\n")
        sb.append("            <MaxWatts>").append(maxWatts.coerceAtLeast(0)).append("</MaxWatts>\n")
        sb.append("          </LX>\n")
        sb.append("        </Extensions>\n")
        sb.append("      </Lap>\n")
        sb.append("      <Notes>").append(escapeXml(workoutName.ifBlank { "Free Ride" })).append("</Notes>\n")
        sb.append("    </Activity>\n")
        sb.append("  </Activities>\n")
        sb.append("</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    private fun appendTrackpoint(
        sb: StringBuilder,
        startMs: Long,
        endMs: Long,
        sampleMs: Long,
        distanceMeters: Double,
        cadenceRpm: Int,
        heartRateBpm: Int,
        watts: Int
    ) {
        val clampedMs = sampleMs.coerceIn(startMs, endMs)
        sb.append("          <Trackpoint>\n")
        sb.append("            <Time>").append(isoInstant(clampedMs)).append("</Time>\n")
        sb.append("            <DistanceMeters>").append("%.2f".format(distanceMeters)).append("</DistanceMeters>\n")
        if (cadenceRpm > 0) {
            sb.append("            <Cadence>").append(cadenceRpm.coerceIn(0, 300)).append("</Cadence>\n")
        }
        // Out-of-range HR (sensor noise) is omitted, never clamped into existence.
        if (heartRateBpm in 30..250) {
            sb.append("            <HeartRateBpm><Value>")
                .append(heartRateBpm)
                .append("</Value></HeartRateBpm>\n")
        }
        if (watts > 0) {
            sb.append("            <Extensions>\n")
            sb.append("              <TPX xmlns=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\">\n")
            sb.append("                <Watts>").append(watts.coerceAtLeast(0)).append("</Watts>\n")
            sb.append("              </TPX>\n")
            sb.append("            </Extensions>\n")
        }
        sb.append("          </Trackpoint>\n")
    }

    private fun isoInstant(epochMs: Long): String = Instant.ofEpochMilli(epochMs.coerceAtLeast(0L)).atOffset(ZoneOffset.UTC).format(isoFormat)

    fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

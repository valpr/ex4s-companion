package com.valpr.bikecompanion.ui.summary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.health.HealthSyncState
import com.valpr.bikecompanion.ui.components.MetricCard
import com.valpr.bikecompanion.workout.WorkoutMetricSample
import com.valpr.bikecompanion.workout.WorkoutSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    healthSyncState: HealthSyncState = HealthSyncState.Idle,
    onSyncRetry: () -> Unit = {},
    onSyncConnect: () -> Unit = {},
    onExportTcx: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Complete") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Celebration Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            summary.workoutName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val minutes = summary.totalDurationSeconds / 60
                        val seconds = summary.totalDurationSeconds % 60
                        Text(
                            "Completed in %02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Power History Line Chart
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Power Profile (Watts)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PowerHistoryChart(samples = summary.samples, modifier = Modifier.fillMaxWidth().height(160.dp))
                }
            }

            // Heart Rate History Line Chart (only when HR was recorded)
            if (summary.samples.any { it.heartRateBpm > 0 }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Heart Rate (BPM)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HrHistoryChart(samples = summary.samples, modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }

            // Key Metrics Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Power",
                    value = "${summary.avgWatts}",
                    unit = "W",
                    accentColor = Color(0xFF00E676),
                    secondaryText = "Avg • Max: ${summary.maxWatts} W",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Cadence",
                    value = "${summary.avgCadence}",
                    unit = "RPM",
                    accentColor = Color(0xFF29B6F6),
                    secondaryText = "Avg • Max: ${summary.maxCadence} RPM",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Energy",
                    value = "${summary.totalCaloriesKcal}",
                    unit = "kcal",
                    accentColor = Color(0xFFFF9100),
                    secondaryText = "Work: %.1f kJ".format(summary.totalWorkKj),
                    modifier = Modifier.weight(1f)
                )
                val summaryMinutes = summary.totalDurationSeconds / 60
                val summarySeconds = summary.totalDurationSeconds % 60
                MetricCard(
                    label = "Distance",
                    value = "%.2f".format(summary.totalDistanceKm),
                    unit = "km",
                    accentColor = Color(0xFFAB47BC),
                    secondaryText = "Time: %02d:%02d".format(summaryMinutes, summarySeconds),
                    modifier = Modifier.weight(1f)
                )
            }

            // Heart Rate (avg + peak) — only when HR was recorded
            if (summary.avgHeartRate > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = "Heart Rate",
                        value = "${summary.avgHeartRate}",
                        unit = "BPM",
                        accentColor = Color(0xFFFF5252),
                        secondaryText = "Avg • Max: ${summary.maxHeartRate} BPM",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Health Connect sync status + retry
            HealthSyncCard(
                summary = summary,
                state = healthSyncState,
                onRetry = onSyncRetry,
                onConnect = onSyncConnect
            )

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Return to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onExportTcx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Export .tcx (Strava / Garmin)", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HealthSyncCard(
    summary: WorkoutSummary,
    state: HealthSyncState,
    onRetry: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val iconTint: Color
    val title: String
    val subtitle: String
    when (state) {
        is HealthSyncState.Success -> {
            containerColor = Color(0xFF00331C)
            icon = Icons.Default.CheckCircle
            iconTint = Color(0xFF00E676)
            title = "Synced to Health Connect"
            subtitle = "Session, HR, power, cadence, speed & calories saved"
        }
        is HealthSyncState.Syncing -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            icon = Icons.Default.Sync
            iconTint = MaterialTheme.colorScheme.primary
            title = "Syncing to Health Connect…"
            subtitle = "Writing exercise session records"
        }
        is HealthSyncState.Failed -> {
            containerColor = Color(0xFF3E1414)
            icon = Icons.Default.Warning
            iconTint = Color(0xFFFF8A80)
            title = "Health Connect sync failed"
            subtitle = state.reason
        }
        is HealthSyncState.PermissionRequired -> {
            containerColor = Color(0xFF2C2210)
            icon = Icons.Default.Warning
            iconTint = Color(0xFFFFB300)
            title = "Health Connect permissions needed"
            subtitle = "Grant access to save this workout"
        }
        is HealthSyncState.NotAvailable -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            icon = Icons.Default.Warning
            iconTint = Color.Gray
            title = "Health Connect unavailable"
            subtitle = "Install or enable Health Connect to sync"
        }
        is HealthSyncState.Idle -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            icon = Icons.Default.Sync
            iconTint = Color.Gray
            title = "Health Connect"
            subtitle = "Sync this workout on completion"
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state is HealthSyncState.Syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(subtitle, fontSize = 12.sp, color = Color.Gray)
                }
                when (state) {
                    is HealthSyncState.Failed -> Button(onClick = onRetry) { Text("Retry") }
                    is HealthSyncState.PermissionRequired -> Button(onClick = onConnect) { Text("Grant") }
                    else -> Unit
                }
            }

            if (state is HealthSyncState.Success) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chips = buildList {
                        add("Stationary Bike")
                        if (summary.samples.any { it.heartRateBpm > 0 }) add("HR")
                        if (summary.samples.any { it.watts > 0 }) add("Power")
                        if (summary.samples.any { it.cadenceRpm > 0 }) add("Cadence")
                        if (summary.samples.any { it.speedKmh > 0.0 }) add("Speed")
                        if (summary.totalDistanceKm > 0.0) add("%.1f km".format(summary.totalDistanceKm))
                        if (summary.totalCaloriesKcal > 0) add("${summary.totalCaloriesKcal} kcal")
                    }
                    chips.forEach { chipText ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF004D2C))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(chipText, fontSize = 10.sp, color = Color(0xFFA7F3D0))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Cardio Load requires HR recorded directly by a paired Pixel Watch or Fitbit. Health Connect import alone may show 0 cardio load.",
                    fontSize = 11.sp,
                    color = Color(0xFF81C784),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun PowerHistoryChart(samples: List<WorkoutMetricSample>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22))
    ) {
        if (samples.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No telemetry samples recorded", color = Color.Gray, fontSize = 12.sp)
            }
            return
        }

        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val maxWatts = (samples.maxOfOrNull { it.watts } ?: 200).coerceAtLeast(100).toFloat()
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Horizontal grid guide lines
            val gridSteps = 3
            for (i in 0..gridSteps) {
                val y = canvasHeight * (i.toFloat() / gridSteps)
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
            }

            // Power line path
            val path = Path()
            val stepX = canvasWidth / (samples.size - 1).coerceAtLeast(1)

            samples.forEachIndexed { index, sample ->
                val x = index * stepX
                val normalizedY = (sample.watts / maxWatts).coerceIn(0f, 1f)
                val y = canvasHeight - (normalizedY * canvasHeight)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFF00E676),
                style = Stroke(width = 3f)
            )
        }
    }
}

@Composable
fun HrHistoryChart(samples: List<WorkoutMetricSample>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22))
    ) {
        val hrSamples = samples.filter { it.heartRateBpm > 0 }
        if (hrSamples.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No heart rate samples recorded", color = Color.Gray, fontSize = 12.sp)
            }
            return
        }

        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val scale = HrChartScaling.computeScale(hrSamples.map { it.heartRateBpm }) ?: return@Canvas
            val canvasWidth = size.width
            val canvasHeight = size.height

            for (i in 0..2) {
                val y = canvasHeight * (i.toFloat() / 2)
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
            }

            val path = Path()
            val stepX = HrChartScaling.stepX(canvasWidth, hrSamples.size)

            hrSamples.forEachIndexed { index, sample ->
                val x = index * stepX
                val normalizedY = HrChartScaling.normalizedY(sample.heartRateBpm, scale)
                val y = canvasHeight - (normalizedY * canvasHeight)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFFFF5252),
                style = Stroke(width = 3f)
            )
        }
    }
}

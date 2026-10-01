package com.valpr.bikecompanion.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.ui.components.MetricCard
import com.valpr.bikecompanion.ui.summary.HrHistoryChart
import com.valpr.bikecompanion.ui.summary.PowerHistoryChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    viewModel: RideHistoryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ride by viewModel.selectedRide.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ride Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ride?.let { current ->
                        IconButton(onClick = { viewModel.shareRide(current) }) {
                            Icon(Icons.Default.Share, contentDescription = "Export .tcx")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        val current = ride
        val isLoading by viewModel.isLoadingRide.collectAsState()
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading ride…", fontSize = 13.sp, color = Color.Gray)
            }
            return@Scaffold
        }
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text("Ride not found", fontWeight = FontWeight.Bold)
                Text("It may have been deleted.", fontSize = 13.sp, color = Color.Gray)
            }
            return@Scaffold
        }
        val summary = current.toSummary()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        current.workoutName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        RideHistoryViewModel.formatRideDate(current.startTimeEpochMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Power Profile (Watts)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PowerHistoryChart(samples = summary.samples, modifier = Modifier.fillMaxWidth().height(160.dp))
                }
            }

            if (summary.samples.any { it.heartRateBpm > 0 }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Heart Rate (BPM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        HrHistoryChart(samples = summary.samples, modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }

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
                val minutes = summary.totalDurationSeconds / 60
                val seconds = summary.totalDurationSeconds % 60
                MetricCard(
                    label = "Distance",
                    value = "%.2f".format(summary.totalDistanceKm),
                    unit = "km",
                    accentColor = Color(0xFFAB47BC),
                    secondaryText = "Time: %02d:%02d".format(minutes, seconds),
                    modifier = Modifier.weight(1f)
                )
            }

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

            Button(
                onClick = { viewModel.shareRide(current) },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("  Export .tcx (Strava / Garmin)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

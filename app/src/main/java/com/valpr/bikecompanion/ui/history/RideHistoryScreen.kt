package com.valpr.bikecompanion.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.valpr.bikecompanion.history.HistoryStats
import com.valpr.bikecompanion.history.RideHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel,
    onNavigateBack: () -> Unit,
    onRideClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val headers by viewModel.headers.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val bests = HistoryStats.personalBests(headers)
    val totals = HistoryStats.totals(headers)
    val weekMs = 7L * 24 * 60 * 60 * 1000
    val nowMs = System.currentTimeMillis()
    val weekStart = nowMs - (nowMs % weekMs)
    val weekly = HistoryStats.weeklyVolume(headers, weekStart)

    pendingDelete?.let { header ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete ride?") },
            text = { Text("Delete \"${header.workoutName}\" from history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Delete", color = Color(0xFFFF8A80))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ride History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (headers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No rides yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Finish a workout and it will appear here with full power, HR and export.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                HistoryStatsCard(
                    rideCount = totals.rideCount,
                    totalTime = HistoryStats.formatDuration(totals.totalSeconds),
                    totalKj = totals.totalKj,
                    weekRides = weekly.rideCount,
                    weekKj = weekly.totalKj,
                    bestMaxWatts = bests.bestMaxPowerW,
                    longestRide = HistoryStats.formatDuration(bests.longestRideSeconds)
                )
            }

            items(headers, key = { it.id }) { header ->
                RideHistoryRow(
                    header = header,
                    onClick = { onRideClick(header.id) },
                    onDelete = { viewModel.requestDelete(header) }
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun HistoryStatsCard(
    rideCount: Int,
    totalTime: String,
    totalKj: Double,
    weekRides: Int,
    weekKj: Double,
    bestMaxWatts: Int,
    longestRide: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2E1F)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "TRAINING OVERVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(value = "$rideCount", label = "Rides")
                StatItem(value = totalTime, label = "Total time")
                StatItem(value = "%.0f".format(totalKj), label = "kJ total")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(value = "$weekRides", label = "Rides / 7d")
                StatItem(value = "%.0f".format(weekKj), label = "kJ / 7d")
                StatItem(value = "${bestMaxWatts}W", label = "Best power")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Longest ride: $longestRide", fontSize = 11.sp, color = Color(0xFF78909C))
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column {
        Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color(0xFFB0BEC5))
    }
}

@Composable
private fun RideHistoryRow(header: RideHeader, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(header.workoutName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    RideHistoryViewModel.formatRideDate(header.startTimeEpochMs),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        RideHistoryViewModel.formatDuration(header.totalDurationSeconds),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${header.avgWatts}W avg",
                        fontSize = 12.sp,
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "%.0f kJ".format(header.totalWorkKj),
                        fontSize = 12.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ride", tint = Color.Gray)
            }
        }
    }
}

@Composable
internal fun CompactHistoryEntry(
    rideCount: Int,
    lastRideName: String?,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onViewAll)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsBike,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Ride History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (rideCount > 0) {
                        "$rideCount rides • Last: ${lastRideName ?: "—"}"
                    } else {
                        "Past rides, bests and TCX export"
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Text("View all", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

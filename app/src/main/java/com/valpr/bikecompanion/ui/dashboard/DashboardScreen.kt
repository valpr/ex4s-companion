package com.valpr.bikecompanion.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.ui.components.DeviceScanDialog
import com.valpr.bikecompanion.ui.components.WorkoutCanvasProfile
import com.valpr.bikecompanion.workout.BeginnerPlan
import com.valpr.bikecompanion.workout.CachedWorkoutHeader
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onStartWorkout: () -> Unit,
    onNavigateToAthleteStats: () -> Unit,
    modifier: Modifier = Modifier,
    onResumeWorkout: () -> Unit = onStartWorkout,
    onNavigateToSettings: () -> Unit = onNavigateToAthleteStats
) {
    val bleState by viewModel.bleManager.connectionState.collectAsState()
    val telemetry by viewModel.bleManager.telemetry.collectAsState()
    val discoveredDevices by viewModel.bleManager.discoveredDevices.collectAsState()
    val cachedWorkouts by viewModel.cachedWorkouts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val watchState by viewModel.watchState.collectAsState()
    val selectedPreview by viewModel.selectedWorkoutPreview.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showScanDialog by remember { mutableStateOf(false) }
    var showFtpPromptDialog by remember { mutableStateOf(false) }
    var ftpInputValue by remember { mutableStateOf("") }
    var pendingWorkoutToStart by remember { mutableStateOf<Workout?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val isBikeConnected = WorkoutStartGate.canStartWorkout(bleState)

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // SAF Document Picker for .zwo files
    val importPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importWorkoutFile(it) }
    }

    if (showScanDialog) {
        DeviceScanDialog(
            isScanning = bleState is BleConnectionState.Scanning,
            devices = discoveredDevices,
            onStartScan = { viewModel.bleManager.startScan() },
            onStopScan = { viewModel.bleManager.stopScan() },
            onSelectDevice = { device ->
                viewModel.bleManager.connect(device.device)
                showScanDialog = false
            },
            onDismiss = {
                viewModel.bleManager.stopScan()
                showScanDialog = false
            }
        )
    }

    // FTP Requirement Dialog
    if (showFtpPromptDialog) {
        AlertDialog(
            onDismissRequest = { showFtpPromptDialog = false },
            title = { Text("Set Athlete FTP") },
            text = {
                Column {
                    Text(
                        "Structured workouts calculate target wattage as a fraction of your Functional Threshold Power (FTP). Please set your FTP before starting.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ftpInputValue,
                        onValueChange = { ftpInputValue = it },
                        label = { Text("FTP in Watts") },
                        placeholder = { Text("e.g. 200") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val enteredFtp = ftpInputValue.toIntOrNull() ?: 0
                        if (enteredFtp > 0) {
                            viewModel.updateFtp(enteredFtp)
                            showFtpPromptDialog = false
                            pendingWorkoutToStart?.let { workout ->
                                viewModel.refreshWatchConnection()
                                val result = viewModel.sessionManager.startWorkout(workout)
                                if (result.isSuccess) {
                                    onStartWorkout()
                                }
                            }
                        }
                    }
                ) {
                    Text("Save & Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFtpPromptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Workout Detail Preview Modal
    selectedPreview?.let { workout ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearWorkoutPreview() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(workout.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (workout.author.isNotBlank()) {
                    Text("By ${workout.author}", fontSize = 13.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(8.dp))

                val minutes = workout.totalDurationSeconds / 60
                val seconds = workout.totalDurationSeconds % 60
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Duration: %02d:%02d".format(minutes, seconds), fontWeight = FontWeight.SemiBold)
                    Text(
                        "TSS: %.1f".format(workout.estimatedTss),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (workout.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(workout.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas Profile Preview
                WorkoutCanvasProfile(
                    workout = workout,
                    elapsedSeconds = 0,
                    showPlayhead = false,
                    modifier = Modifier.fillMaxWidth(),
                    height = 140.dp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        viewModel.clearWorkoutPreview()
                        if (!userProfile.isFtpConfigured) {
                            pendingWorkoutToStart = workout
                            showFtpPromptDialog = true
                        } else {
                            viewModel.refreshWatchConnection()
                            val result = viewModel.sessionManager.startWorkout(workout)
                            if (result.isSuccess) {
                                onStartWorkout()
                            }
                        }
                    },
                    enabled = isBikeConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Workout", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (!isBikeConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Connect your bike to start a workout",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EX-4S Companion", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToAthleteStats) {
                        Icon(Icons.Default.Person, contentDescription = "Athlete Profile & Stats")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Connection Status Banner
            item {
                BikeConnectionCard(
                    state = bleState,
                    telemetryWatts = telemetry.estimatedWatts,
                    telemetryCadence = telemetry.cadenceRpm,
                    onConnectClick = {
                        showScanDialog = true
                        viewModel.bleManager.startScan()
                    }
                )
            }

            // Active Session in Progress Banner (if active or paused)
            if (sessionState.status == SessionStatus.RUNNING || sessionState.status == SessionStatus.PAUSED) {
                item {
                    ActiveWorkoutCard(
                        sessionState = sessionState,
                        onResume = onResumeWorkout,
                        onStop = {
                            viewModel.sessionManager.stopWorkout()
                            onResumeWorkout()
                        }
                    )
                }
            }

            // 2. Pixel Watch Status Pill
            item {
                PixelWatchStatusCard(
                    watchState = watchState,
                    onRefresh = { viewModel.refreshWatchConnection() }
                )
            }

            // 3. Quick Start (Free Ride)
            item {
                QuickStartCard(
                    enabled = isBikeConnected,
                    onStartFreeRide = {
                        viewModel.refreshWatchConnection()
                        val result = viewModel.sessionManager.startWorkout(null)
                        if (result.isSuccess) {
                            onStartWorkout()
                        }
                    }
                )
            }

            // 4. Beginner Path (graduated recommendations for brand-new riders).
            // Dismissable for experienced riders; a compact restore row brings it back.
            if (userProfile.beginnerPathDismissed) {
                item {
                    BeginnerPathRestoreRow(
                        onRestore = { viewModel.setBeginnerPathDismissed(false) }
                    )
                }
            } else {
                item {
                    BeginnerPathCard(
                        cachedWorkouts = cachedWorkouts,
                        isCollapsed = userProfile.beginnerPathCollapsed,
                        onToggleCollapsed = { viewModel.setBeginnerPathCollapsed(!userProfile.beginnerPathCollapsed) },
                        onDismiss = { viewModel.setBeginnerPathDismissed(true) },
                        onLevelClick = { filename -> viewModel.selectWorkoutForPreview(filename) }
                    )
                }
            }

            // 5. Workout Library Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Workout Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = {
                            importPickerLauncher.launch(
                                arrayOf(
                                    "application/xml",
                                    "text/xml",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .zwo", fontSize = 12.sp)
                    }
                }
            }

            // Workout Cards
            if (cachedWorkouts.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No workouts found", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Import a .zwo file or load default samples.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            } else {
                items(cachedWorkouts, key = { it.filename }) { header ->
                    WorkoutItemCard(
                        header = header,
                        onClick = { viewModel.selectWorkoutForPreview(header.filename) },
                        onDelete = { viewModel.deleteWorkout(header.filename) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun BikeConnectionCard(
    state: BleConnectionState,
    telemetryWatts: Int,
    telemetryCadence: Int,
    onConnectClick: () -> Unit
) {
    val isConnected = state is BleConnectionState.Connected
    val containerColor = if (isConnected) Color(0xFF00331C) else MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnected, onClick = onConnectClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (isConnected) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val title = when (state) {
                    is BleConnectionState.Connected -> state.deviceName
                    is BleConnectionState.Connecting -> "Connecting..."
                    is BleConnectionState.Handshaking -> "Handshaking with EX-4S..."
                    is BleConnectionState.Scanning -> "Searching for bike..."
                    else -> "Echelon EX-4S Disconnected"
                }

                val subtitle = if (isConnected) {
                    "$telemetryWatts W  •  $telemetryCadence RPM  •  Ready to ride"
                } else {
                    "Tap to search & connect via BLE"
                }

                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = if (isConnected) Color(0xFFB0FFD0) else Color.Gray)
            }

            if (!isConnected) {
                Button(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Connect", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PixelWatchStatusCard(
    watchState: com.valpr.bikecompanion.wearable.WearableWatchState,
    onRefresh: () -> Unit = {}
) {
    // 1s ticker so LIVE flips to STALE even when no new batches arrive to
    // trigger recomposition. Gated to the only case that changes with time.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val needsTicker = watchState.isConnected && watchState.lastHeartRateTimestampMs > 0L
    LaunchedEffect(needsTicker, watchState.lastHeartRateTimestampMs) {
        nowMs = System.currentTimeMillis()
        if (!needsTicker) return@LaunchedEffect
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val hrStatus = com.valpr.bikecompanion.wearable.PhoneWearableManager.resolveWatchHrStatus(watchState, nowMs)
    val isConnected = watchState.isConnected
    val containerColor = if (isConnected) {
        Color(
            0xFF00331C
        )
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val iconColor = if (isConnected) Color(0xFF00E676) else Color(0xFF29B6F6)

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRefresh() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Watch, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            val title = if (isConnected) {
                "${watchState.nodeName} Connected"
            } else {
                "Pixel Watch: Standby"
            }
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            // Freshness-gated: a frozen BPM with a reachable node presents as
            // STALE, never "Live" (lastHeartRateBpm alone cannot prove liveness).
            val statusText = when (hrStatus) {
                com.valpr.bikecompanion.wearable.WatchHrStatus.LIVE ->
                    "Live HR: ${watchState.lastHeartRateBpm} BPM"
                com.valpr.bikecompanion.wearable.WatchHrStatus.STALE -> {
                    val ageSec = ((nowMs - watchState.lastHeartRateTimestampMs) / 1000L).coerceAtLeast(0L)
                    "HR stale • ${ageSec}s ago"
                }
                com.valpr.bikecompanion.wearable.WatchHrStatus.NO_DATA -> "Waiting for watch HR…"
                com.valpr.bikecompanion.wearable.WatchHrStatus.DISCONNECTED -> "Waiting for Watch"
            }
            val statusColor = when (hrStatus) {
                com.valpr.bikecompanion.wearable.WatchHrStatus.LIVE -> Color(0xFF00E676)
                com.valpr.bikecompanion.wearable.WatchHrStatus.STALE -> Color(0xFFFFB300)
                else -> Color.Gray
            }
            Text(
                statusText,
                fontSize = 11.sp,
                color = statusColor,
                fontWeight = if (hrStatus == com.valpr.bikecompanion.wearable.WatchHrStatus.LIVE) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun QuickStartCard(enabled: Boolean, onStartFreeRide: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onStartFreeRide)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Quick Start (Free Ride)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                    Text(
                        if (enabled) {
                            "Open ride with electronic resistance control"
                        } else {
                            "Connect your bike to start a ride"
                        },
                        fontSize = 12.sp,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            Color.Gray
                        }
                    )
                }
            }

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }
    }
}

@Composable
internal fun BeginnerPathRestoreRow(onRestore: () -> Unit) {
    OutlinedButton(
        onClick = onRestore,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("New to biking? Show Beginner Path", fontSize = 12.sp)
    }
}

@Composable
internal fun BeginnerPathCard(
    cachedWorkouts: List<CachedWorkoutHeader>,
    onLevelClick: (String) -> Unit,
    isCollapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val headersByFile = remember(cachedWorkouts) {
        cachedWorkouts.associateBy { it.filename.lowercase() }
    }
    // Without persisted ride history yet, always highlight Level 1 as the
    // entry point; the ordered list itself communicates the progression.
    val recommended = remember { BeginnerPlan.recommendNext(emptySet()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2E1F)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggleCollapsed)
                ) {
                    Text(
                        "NEW TO BIKING? START HERE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Beginner Path",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onToggleCollapsed) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (isCollapsed) "Expand Beginner Path" else "Collapse Beginner Path",
                        tint = Color(0xFF00E676)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hide Beginner Path",
                        tint = Color(0xFF78909C)
                    )
                }
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column {
                    Text(
                        BeginnerPlan.PACING_GUIDANCE,
                        fontSize = 12.sp,
                        color = Color(0xFFB0BEC5)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    BeginnerPlan.LEVELS.forEach { level ->
                        val header = headersByFile[level.filename.lowercase()]
                        val isRecommended = level.filename.equals(recommended.filename, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = header != null) { onLevelClick(level.filename) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF00E676), CircleShape)
                            ) {
                                Text(
                                    "${level.level}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        level.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    if (isRecommended && header != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .background(Color(0xFF00E676), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "START HERE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                                Text(
                                    level.focus,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB0BEC5)
                                )
                                if (header != null) {
                                    val minutes = header.durationSeconds / 60
                                    Text(
                                        "$minutes min • TSS %.0f".format(header.estimatedTss),
                                        fontSize = 11.sp,
                                        color = Color(0xFF00E676),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        "Loading…",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start ${level.title}",
                                tint = if (header != null) Color(0xFF00E676) else Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Graduation: Sweet Spot Intervals (30 min) (${BeginnerPlan.PACING_TIMELINE}). Easy efforts first — fitness builds week to week.",
                        fontSize = 11.sp,
                        color = Color(0xFF78909C)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutItemCard(header: CachedWorkoutHeader, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(header.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                val minutes = header.durationSeconds / 60
                val seconds = header.durationSeconds % 60
                val timeStr = "%02d:%02d".format(minutes, seconds)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        timeStr,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "TSS %.0f".format(header.estimatedTss),
                        fontSize = 12.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (header.author.isNotBlank()) {
                        Text(header.author, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun ActiveWorkoutCard(sessionState: WorkoutSessionState, onResume: () -> Unit, onStop: () -> Unit) {
    val isPaused = sessionState.status == SessionStatus.PAUSED
    val telem = sessionState.latestTelemetry
    val workoutName = sessionState.workout?.name ?: "Free Ride"

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isPaused) Color(0xFF263238) else Color(0xFF003822)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onResume)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = null,
                        tint = if (isPaused) Color(0xFFFFB300) else Color(0xFF00E676),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isPaused) "WORKOUT PAUSED" else "WORKOUT IN PROGRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) Color(0xFFFFB300) else Color(0xFF00E676),
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    sessionState.formattedElapsedTime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                workoutName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            val targetText = sessionState.targetWatts?.let { " • Target: ${it}W" } ?: ""
            Text(
                "${telem.estimatedWatts}W • ${telem.cadenceRpm} RPM • L${telem.resistanceLevel}$targetText",
                fontSize = 13.sp,
                color = Color(0xFFB0BEC5)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resume Workout", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("End", color = Color(0xFFFF8A80), fontSize = 13.sp)
                }
            }
        }
    }
}

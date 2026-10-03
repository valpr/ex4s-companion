package com.valpr.bikecompanion.ui.dashboard

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.ui.components.DeviceScanDialog
import com.valpr.bikecompanion.ui.components.WorkoutCanvasProfile
import com.valpr.bikecompanion.workout.BeginnerPlan
import com.valpr.bikecompanion.workout.CachedWorkoutHeader
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.TagCount
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutDurationBracket
import com.valpr.bikecompanion.workout.WorkoutSessionState
import com.valpr.bikecompanion.workout.WorkoutSortOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onStartWorkout: () -> Unit,
    onNavigateToAthleteStats: () -> Unit,
    modifier: Modifier = Modifier,
    onResumeWorkout: () -> Unit = onStartWorkout,
    onNavigateToSettings: () -> Unit = onNavigateToAthleteStats,
    onNavigateToHistory: () -> Unit = {},
    onEditWorkout: (String?) -> Unit = {}
) {
    val bleState by viewModel.bleManager.connectionState.collectAsState()
    val telemetry by viewModel.bleManager.telemetry.collectAsState()
    val discoveredDevices by viewModel.bleManager.discoveredDevices.collectAsState()
    val cachedWorkouts by viewModel.cachedWorkouts.collectAsState()
    val displayedWorkouts by viewModel.displayedWorkouts.collectAsState()
    val selectedTagFilter by viewModel.selectedTagFilter.collectAsState()
    val selectedSortOption by viewModel.selectedSortOption.collectAsState()
    val selectedDurationBracket by viewModel.selectedDurationBracket.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val availableTagCounts by viewModel.availableTagCounts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val watchState by viewModel.watchState.collectAsState()
    val selectedPreview by viewModel.selectedWorkoutPreview.collectAsState()
    val selectedPreviewFilename by viewModel.selectedWorkoutFilename.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val historyHeaders by viewModel.historyHeaders.collectAsState()
    val completedFilenames by viewModel.completedFilenames.collectAsState()
    val completionCountMap by viewModel.completionCountMap.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    var showScanDialog by remember { mutableStateOf(false) }
    var showFtpPromptDialog by remember { mutableStateOf(false) }
    var ftpInputValue by remember { mutableStateOf("") }
    var pendingWorkoutToStart by remember { mutableStateOf<Pair<Workout, String?>?>(null) }
    var workoutToDelete by remember { mutableStateOf<CachedWorkoutHeader?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val dashboardScope = rememberCoroutineScope()

    val isBikeConnected = WorkoutStartGate.canStartWorkout(bleState)
    val isBluetoothEnabled by viewModel.bleManager.isBluetoothEnabled.collectAsState()

    // System prompt to switch phone Bluetooth on (BLUETOOTH_CONNECT-gated on S+;
    // SecurityException falls back to a Settings snackbar below).
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.bleManager.startScan()
        } else {
            dashboardScope.launch {
                snackbarHostState.showSnackbar("Bluetooth is still off — the bike needs Bluetooth on.")
            }
        }
    }

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
            onDismissRequest = {
                pendingWorkoutToStart = null
                showFtpPromptDialog = false
            },
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
                            pendingWorkoutToStart?.let { (workout, filename) ->
                                viewModel.refreshWatchConnection()
                                val result = viewModel.sessionManager.startWorkout(workout, filename)
                                if (result.isSuccess) {
                                    pendingWorkoutToStart = null
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
                TextButton(
                    onClick = {
                        pendingWorkoutToStart = null
                        showFtpPromptDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Workout Deletion Confirmation Dialog
    workoutToDelete?.let { target ->
        DeleteWorkoutDialog(
            workoutName = target.name,
            onConfirm = {
                viewModel.deleteWorkout(target.filename)
                workoutToDelete = null
            },
            onDismiss = { workoutToDelete = null }
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
                val tssTooltipState = rememberTooltipState(isPersistent = true)
                val tooltipScope = rememberCoroutineScope()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Duration: %02d:%02d".format(minutes, seconds), fontWeight = FontWeight.SemiBold)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(
                                    "Training Stress Score (TSS): Estimates total physiological load based on workout intensity and duration relative to your FTP (1 hr @ 100% FTP = 100 TSS). <50: Light, 50–100: Moderate, 100+: Demanding.",
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        },
                        state = tssTooltipState
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                tooltipScope.launch { tssTooltipState.show() }
                            }
                        ) {
                            Text(
                                "TSS: %.1f".format(workout.estimatedTss),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "TSS info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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
                        val filename = selectedPreviewFilename
                        viewModel.clearWorkoutPreview()
                        if (!userProfile.isFtpConfigured) {
                            pendingWorkoutToStart = workout to filename
                            showFtpPromptDialog = true
                        } else {
                            viewModel.refreshWatchConnection()
                            val result = viewModel.sessionManager.startWorkout(workout, filename)
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val filename = selectedPreviewFilename
                        viewModel.clearWorkoutPreview()
                        onEditWorkout(filename)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit workout", fontSize = 14.sp)
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
                    ProfileSwitcher(
                        profiles = profiles,
                        activeProfile = activeProfile,
                        sessionBlocked = sessionState.status == SessionStatus.RUNNING ||
                            sessionState.status == SessionStatus.PAUSED,
                        onSwitch = { viewModel.switchProfile(it) },
                        onCreate = { name, color -> viewModel.createProfile(name, color) },
                        onRename = { id, name -> viewModel.renameProfile(id, name) },
                        onColor = { id, color -> viewModel.setProfileColor(id, color) },
                        onDelete = { viewModel.deleteProfile(it) }
                    )
                    IconButton(onClick = onNavigateToAthleteStats) {
                        Icon(Icons.Default.Person, contentDescription = "Stats")
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

            // Phone Bluetooth off: prompt to switch it on before anything BLE.
            if (!isBluetoothEnabled) {
                item {
                    BluetoothDisabledCard(
                        hasAdapter = viewModel.bleManager.hasBluetoothAdapter,
                        onEnableClick = {
                            try {
                                enableBluetoothLauncher.launch(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                )
                            } catch (e: SecurityException) {
                                dashboardScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Bluetooth permission needed — please switch Bluetooth on in system Settings."
                                    )
                                }
                            } catch (e: Exception) {
                                dashboardScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Couldn't open Bluetooth settings — please switch Bluetooth on manually."
                                    )
                                }
                            }
                        }
                    )
                }
            }

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
                    sessionStatus = sessionState.status,
                    onRefresh = { viewModel.testWatchConnection() }
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

            // 4. Ride History entry (persisted completed rides + TCX export)
            item {
                com.valpr.bikecompanion.ui.history.CompactHistoryEntry(
                    rideCount = historyHeaders.size,
                    lastRideName = historyHeaders.firstOrNull()?.workoutName,
                    onViewAll = onNavigateToHistory
                )
            }

            // 5. Beginner Path (graduated recommendations for brand-new riders).
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
                        completedFilenames = completedFilenames,
                        isCollapsed = userProfile.beginnerPathCollapsed,
                        onToggleCollapsed = { viewModel.setBeginnerPathCollapsed(!userProfile.beginnerPathCollapsed) },
                        onDismiss = { viewModel.setBeginnerPathDismissed(true) },
                        onLevelClick = { filename -> viewModel.selectWorkoutForPreview(filename) }
                    )
                }
            }

            // 5. Workout Library Header & Actions
            item {
                WorkoutLibraryHeader(
                    onNewWorkout = { onEditWorkout(null) },
                    onImportWorkout = {
                        importPickerLauncher.launch(
                            arrayOf(
                                "application/xml",
                                "text/xml",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    selectedSortOption = selectedSortOption,
                    onSelectSortOption = { viewModel.setSortOption(it) },
                    selectedDurationBracket = selectedDurationBracket,
                    onSelectDurationBracket = { viewModel.setDurationBracket(it) },
                    showFilters = cachedWorkouts.isNotEmpty()
                )
            }

            // 5b. Tag Filter Chips Row (shown if workouts exist)
            if (cachedWorkouts.isNotEmpty() && availableTagCounts.isNotEmpty()) {
                item {
                    TagFilterRow(
                        tags = availableTagCounts,
                        selectedTag = selectedTagFilter,
                        totalWorkoutsCount = cachedWorkouts.size,
                        onSelectTag = { viewModel.setTagFilter(it) }
                    )
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
            } else if (displayedWorkouts.isEmpty()) {
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
                            val emptyMsg = when {
                                searchQuery.isNotBlank() -> "No workouts match \"$searchQuery\""
                                selectedTagFilter != null -> "No workouts match \"$selectedTagFilter\""
                                selectedDurationBracket != WorkoutDurationBracket.ALL -> "No workouts match duration \"${selectedDurationBracket.displayName}\""
                                else -> "No workouts match current filters"
                            }
                            Text(emptyMsg, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.setTagFilter(null)
                                    viewModel.setSearchQuery("")
                                    viewModel.setDurationBracket(WorkoutDurationBracket.ALL)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear Filters", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                items(displayedWorkouts, key = { it.filename }) { header ->
                    WorkoutItemCard(
                        header = header,
                        isFavorite = userProfile.favoriteWorkoutFilenames.any { it.equals(header.filename, ignoreCase = true) },
                        completionCount = completionCountMap[header.filename.lowercase()] ?: 0,
                        onClick = { viewModel.selectWorkoutForPreview(header.filename) },
                        onDelete = { workoutToDelete = header },
                        onToggleFavorite = { viewModel.toggleFavoriteWorkout(header.filename) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun BluetoothDisabledCard(
    hasAdapter: Boolean,
    onEnableClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A0E0E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = Color(0xFFFF8A80),
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Phone Bluetooth is off", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    if (hasAdapter) {
                        "Turn it on to find and stay connected to your EX-4S."
                    } else {
                        "This device reports no Bluetooth adapter."
                    },
                    fontSize = 12.sp,
                    color = Color(0xFFFFCDD2)
                )
            }

            if (hasAdapter) {
                Button(
                    onClick = onEnableClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Turn On", fontSize = 12.sp)
                }
            }
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
                    "Tap to search & connect"
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
    sessionStatus: com.valpr.bikecompanion.workout.SessionStatus,
    onRefresh: () -> Unit = {}
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val uiModel = remember(watchState, sessionStatus, nowMs) {
        WatchDashboardPresentation.resolve(watchState, sessionStatus, nowMs)
    }

    LaunchedEffect(uiModel.needsTicker, watchState.lastHeartRateTimestampMs) {
        nowMs = System.currentTimeMillis()
        if (!uiModel.needsTicker) return@LaunchedEffect
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val isConnected = uiModel.isConnected
    val containerColor = if (isConnected) {
        Color(0xFF00331C)
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
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Watch, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isConnected) Color(0xFF00E676) else Color(0xFF757575), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    uiModel.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            val statusColor = when (uiModel.tone) {
                WatchStatusTone.LIVE, WatchStatusTone.READY -> Color(0xFF00E676)
                WatchStatusTone.WARNING -> Color(0xFFFFB300)
                WatchStatusTone.ACQUIRING -> Color(0xFF29B6F6)
                WatchStatusTone.MUTED -> Color.Gray
            }
            val fontWeight = when (uiModel.tone) {
                WatchStatusTone.LIVE -> FontWeight.Bold
                WatchStatusTone.READY -> FontWeight.Medium
                else -> FontWeight.Normal
            }
            Text(
                uiModel.statusText,
                fontSize = 11.sp,
                color = statusColor,
                fontWeight = fontWeight,
                maxLines = 1
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
    onDismiss: () -> Unit = {},
    completedFilenames: Set<String> = emptySet()
) {
    val headersByFile = remember(cachedWorkouts) {
        cachedWorkouts.associateBy { it.filename.lowercase() }
    }
    // Highlight the next uncompleted level from real ride history; empty history → Level 1.
    val recommended = remember(completedFilenames) { BeginnerPlan.recommendNext(completedFilenames) }
    val graduated = remember(completedFilenames) { BeginnerPlan.hasGraduated(completedFilenames) }

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
                        "NEW TO BIKING?",
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
                        val isCompleted = completedFilenames.contains(level.filename.lowercase())
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
                                    if (isRecommended && header != null && !graduated) {
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
                                    val doneSuffix = if (isCompleted) " • ✓ Done" else ""
                                    Text(
                                        "$minutes min • TSS %.0f$doneSuffix".format(header.estimatedTss),
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
                        if (graduated) {
                            "Graduated! You finished all 4 levels — time for Sweet Spot Intervals (30 min)."
                        } else {
                            "Graduation: Sweet Spot Intervals (30 min) (${BeginnerPlan.PACING_TIMELINE}). Easy efforts first — fitness builds week to week."
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF78909C)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutLibraryHeader(
    onNewWorkout: () -> Unit,
    onImportWorkout: () -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    selectedSortOption: WorkoutSortOption = WorkoutSortOption.RECENTLY_MODIFIED,
    onSelectSortOption: (WorkoutSortOption) -> Unit = {},
    selectedDurationBracket: WorkoutDurationBracket = WorkoutDurationBracket.ALL,
    onSelectDurationBracket: (WorkoutDurationBracket) -> Unit = {},
    showFilters: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Workout Library",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (showFilters) {
            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search workouts, tags, authors…", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNewWorkout,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "New",
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onImportWorkout,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Import .zwo",
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showFilters) {
                WorkoutSortMenu(
                    selectedOption = selectedSortOption,
                    onSelectOption = onSelectSortOption
                )

                WorkoutDurationMenu(
                    selectedBracket = selectedDurationBracket,
                    onSelectBracket = onSelectDurationBracket
                )
            }
        }
    }
}

@Composable
internal fun WorkoutDurationMenu(
    selectedBracket: WorkoutDurationBracket,
    onSelectBracket: (WorkoutDurationBracket) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Schedule, contentDescription = "Filter by duration", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (selectedBracket == WorkoutDurationBracket.ALL) "Duration" else selectedBracket.displayName,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            WorkoutDurationBracket.entries.forEach { bracket ->
                DropdownMenuItem(
                    text = {
                        Text(
                            bracket.displayName,
                            fontWeight = if (bracket == selectedBracket) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelectBracket(bracket)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun WorkoutSortMenu(
    selectedOption: WorkoutSortOption,
    onSelectOption: (WorkoutSortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort workouts", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                selectedOption.displayName,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            WorkoutSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.displayName,
                            fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelectOption(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagFilterRow(
    tags: List<TagCount>,
    selectedTag: String?,
    totalWorkoutsCount: Int,
    onSelectTag: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedTag == null || selectedTag.equals("All", ignoreCase = true),
                onClick = { onSelectTag(null) },
                label = { Text("All ($totalWorkoutsCount)", fontSize = 12.sp) },
                shape = RoundedCornerShape(8.dp)
            )
        }
        items(tags, key = { it.tag }) { tagCount ->
            FilterChip(
                selected = selectedTag.equals(tagCount.tag, ignoreCase = true),
                onClick = {
                    if (selectedTag.equals(tagCount.tag, ignoreCase = true)) {
                        onSelectTag(null)
                    } else {
                        onSelectTag(tagCount.tag)
                    }
                },
                label = { Text("${tagCount.tag} (${tagCount.count})", fontSize = 12.sp) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
internal fun WorkoutTagBadge(
    tag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = tag,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF38BDF8)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutItemCard(
    header: CachedWorkoutHeader,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isFavorite: Boolean = false,
    completionCount: Int = 0,
    onToggleFavorite: () -> Unit = {}
) {
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
                .padding(end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleFavorite
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Color(0xFFFFB300) else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = header.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (completionCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00331C), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (completionCount == 1) "✓ Completed" else "✓ ${completionCount}x",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                }

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
                        Text("By ${header.author}", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                if (header.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        header.tags.take(3).forEach { tag ->
                            WorkoutTagBadge(tag = tag)
                        }
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun DeleteWorkoutDialog(
    workoutName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Workout") },
        text = {
            Text("Are you sure you want to delete \"$workoutName\"?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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

package com.valpr.bikecompanion.ui.athletestats

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.health.HealthConnectionStatus
import com.valpr.bikecompanion.health.HealthSyncState
import com.valpr.bikecompanion.shared.AthleteMetrics
import com.valpr.bikecompanion.shared.BiologicalSex
import com.valpr.bikecompanion.shared.CyclingCategory
import com.valpr.bikecompanion.shared.UnitSystem
import com.valpr.bikecompanion.ui.components.DeviceScanDialog
import com.valpr.bikecompanion.ui.theme.AccentAmber
import com.valpr.bikecompanion.ui.theme.AccentCyan
import com.valpr.bikecompanion.ui.theme.AccentGreen
import com.valpr.bikecompanion.ui.theme.AccentRed
import com.valpr.bikecompanion.ui.theme.TextMuted
import com.valpr.bikecompanion.ui.theme.TextPrimary
import com.valpr.bikecompanion.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteStatsScreen(
    viewModel: AthleteStatsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    healthStatus: HealthConnectionStatus = HealthConnectionStatus(),
    healthSyncState: HealthSyncState = HealthSyncState.Idle,
    onHealthConnectClick: () -> Unit = {}
) {
    val profile by viewModel.userProfile.collectAsState()
    val bleConnectionState by viewModel.bleManager.connectionState.collectAsState()
    val lastBleError by viewModel.bleManager.lastError.collectAsState()
    val discoveredDevices by viewModel.bleManager.discoveredDevices.collectAsState()

    var showScanDialog by remember { mutableStateOf(false) }
    var isHardwareExpanded by remember { mutableStateOf(false) }

    // Units
    val isMetric = profile.unitSystem == UnitSystem.METRIC

    // Bio states
    var ageInput by remember(profile.age) { mutableStateOf(profile.age.toString()) }
    var selectedSex by remember(profile.biologicalSex) { mutableStateOf(profile.biologicalSex) }
    var weightInput by remember(profile.weightKg, isMetric) {
        val displayWeight = if (isMetric) profile.weightKg else AthleteMetrics.kgToLbs(profile.weightKg)
        mutableStateOf("%.1f".format(displayWeight))
    }
    var heightInput by remember(profile.heightCm, isMetric) {
        val displayHeight = if (isMetric) profile.heightCm else AthleteMetrics.cmToInches(profile.heightCm)
        mutableStateOf("%.1f".format(displayHeight))
    }

    // Power & Cadence states
    var ftpInput by remember(profile.ftp) { mutableStateOf(if (profile.ftp > 0) profile.ftp.toString() else "") }
    var preferredCadenceInput by remember(profile.preferredCadenceRpm) { mutableStateOf(profile.preferredCadenceRpm.toString()) }

    // HR states
    var maxHrInput by remember(profile.maxHeartRate) { mutableStateOf(profile.maxHeartRate.toString()) }
    var restingHrInput by remember(profile.restingHeartRate) { mutableStateOf(profile.restingHeartRate.toString()) }
    var lthrInput by remember(profile.lactateThresholdHeartRate) { mutableStateOf(profile.lactateThresholdHeartRate.toString()) }
    var criticalHrInput by remember(profile.criticalHeartRate) { mutableStateOf(profile.criticalHeartRate.toString()) }
    var useKarvonenZones by remember { mutableStateOf(false) }

    // Engine tuning sliders
    var sliderKp by remember(profile.ergKp) { mutableFloatStateOf(profile.ergKp) }
    var sliderKi by remember(profile.ergKi) { mutableFloatStateOf(profile.ergKi) }
    var sliderFloor by remember(profile.cadenceFloorRpm) { mutableIntStateOf(profile.cadenceFloorRpm) }
    var sliderRecovery by remember(profile.cadenceRecoveryRpm) { mutableIntStateOf(profile.cadenceRecoveryRpm) }

    if (showScanDialog) {
        DeviceScanDialog(
            isScanning = bleConnectionState is BleConnectionState.Scanning,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Athlete Profile & Stats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Metric / Imperial unit toggle chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        FilterChip(
                            selected = isMetric,
                            onClick = { viewModel.updateUnitSystem(UnitSystem.METRIC) },
                            label = { Text("kg / cm", fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = !isMetric,
                            onClick = { viewModel.updateUnitSystem(UnitSystem.IMPERIAL) },
                            label = { Text("lbs / in", fontSize = 11.sp) }
                        )
                    }
                },
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

            // ----------------------------------------------------
            // 1. Biometrics & Core Profile
            // ----------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = AccentCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Athlete Biometrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Age & Sex
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { ageInput = it },
                            label = { Text("Age (Years)") },
                            placeholder = { Text("30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sex", fontSize = 12.sp, color = TextSecondary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = selectedSex == BiologicalSex.MALE,
                                    onClick = { selectedSex = BiologicalSex.MALE },
                                    label = { Text("M", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = selectedSex == BiologicalSex.FEMALE,
                                    onClick = { selectedSex = BiologicalSex.FEMALE },
                                    label = { Text("F", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = selectedSex == BiologicalSex.OTHER,
                                    onClick = { selectedSex = BiologicalSex.OTHER },
                                    label = { Text("Other", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Weight & Height
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val weightLabel = if (isMetric) "Weight (kg)" else "Weight (lbs)"
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text(weightLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        val heightLabel = if (isMetric) "Height (cm)" else "Height (in)"
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text(heightLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // BMR indicator & Save button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bmrKcal = profile.estimatedBmrKcal
                        Text(
                            "Est. BMR: ~$bmrKcal kcal/day",
                            fontSize = 12.sp,
                            color = AccentCyan,
                            fontWeight = FontWeight.Medium
                        )

                        Button(
                            onClick = {
                                val enteredAge = ageInput.toIntOrNull() ?: profile.age
                                val rawWeight = weightInput.toFloatOrNull() ?: profile.weightKg
                                val weightKg = if (isMetric) rawWeight else AthleteMetrics.lbsToKg(rawWeight)

                                val rawHeight = heightInput.toFloatOrNull() ?: profile.heightCm
                                val heightCm = if (isMetric) rawHeight else AthleteMetrics.inchesToCm(rawHeight)

                                viewModel.updateAthleteBio(enteredAge, weightKg, heightCm, selectedSex)
                            }
                        ) {
                            Text("Save Vitals")
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // 2. Power Performance & FTP
            // ----------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = AccentGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Power Performance & FTP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ftpInput,
                            onValueChange = { ftpInput = it },
                            label = { Text("FTP (Watts)") },
                            placeholder = { Text("e.g. 220") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = preferredCadenceInput,
                            onValueChange = { preferredCadenceInput = it },
                            label = { Text("Pref Cadence (RPM)") },
                            placeholder = { Text("e.g. 85") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val enteredFtp = ftpInput.toIntOrNull() ?: 0
                                val enteredCadence = preferredCadenceInput.toIntOrNull() ?: 85
                                viewModel.updatePowerSettings(enteredFtp, enteredCadence)
                            }
                        ) {
                            Text("Save Power")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // W/kg Benchmark Pill
                    val wkg = profile.wattsPerKg
                    val category = CyclingCategory.fromWkg(wkg)
                    val categoryColor = when (category) {
                        CyclingCategory.RECREATIONAL -> TextMuted
                        CyclingCategory.MODERATE -> AccentCyan
                        CyclingCategory.TRAINED -> AccentGreen
                        CyclingCategory.VERY_GOOD -> AccentAmber
                        CyclingCategory.EXCELLENT,
                        CyclingCategory.WORLD_CLASS -> AccentRed
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("POWER-TO-WEIGHT", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("%.2f W/kg".format(wkg), fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(categoryColor.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(category.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = categoryColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Coggan 7-Zone Power Breakdown
                    Text("Coggan 7-Zone Power Targets", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    val powerZones = AthleteMetrics.calculateCogganPowerZones(profile.ftp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        powerZones.forEach { zone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Z${zone.zoneNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(zone.name, fontSize = 12.sp, color = TextPrimary)
                                }

                                Text(
                                    if (zone.zoneNumber == 7) "> ${zone.minWatts} W" else "${zone.minWatts}–${zone.maxWatts} W",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // 3. Heart Rate & Zones
            // ----------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cardiovascular & Heart Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Max HR + Auto-estimate button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = maxHrInput,
                            onValueChange = { maxHrInput = it },
                            label = { Text("Max HR (BPM)") },
                            placeholder = { Text("190") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedButton(
                            onClick = {
                                val enteredAge = ageInput.toIntOrNull() ?: profile.age
                                val recommended = AthleteMetrics.recommendMaxHr(enteredAge, selectedSex)
                                maxHrInput = recommended.toString()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Calc from Age", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Resting HR + LTHR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = restingHrInput,
                            onValueChange = { restingHrInput = it },
                            label = { Text("Resting HR (BPM)") },
                            placeholder = { Text("60") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = lthrInput,
                            onValueChange = { lthrInput = it },
                            label = { Text("LTHR (BPM)") },
                            placeholder = { Text("165") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Critical HR safety threshold
                    OutlinedTextField(
                        value = criticalHrInput,
                        onValueChange = { criticalHrInput = it },
                        label = { Text("Critical Safety HR Alert (BPM)") },
                        placeholder = { Text("175") },
                        supportingText = { Text("Triggers dynamic 10% FTP derating & watch haptic", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val enteredMax = maxHrInput.toIntOrNull() ?: profile.maxHeartRate
                                val enteredRest = restingHrInput.toIntOrNull() ?: profile.restingHeartRate
                                val enteredLthr = lthrInput.toIntOrNull() ?: profile.lactateThresholdHeartRate
                                val enteredCrit = criticalHrInput.toIntOrNull() ?: profile.criticalHeartRate
                                viewModel.updateHeartRateSettings(enteredMax, enteredCrit, enteredRest, enteredLthr)
                            }
                        ) {
                            Text("Save Heart Rate")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Heart Rate Zone Preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("HR Training Zones", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Karvonen (HRR)", fontSize = 11.sp, color = if (useKarvonenZones) AccentCyan else TextMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = useKarvonenZones,
                                onCheckedChange = { useKarvonenZones = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val hrZones = if (useKarvonenZones) {
                        AthleteMetrics.calculateKarvonenZones(profile.maxHeartRate, profile.restingHeartRate)
                    } else {
                        AthleteMetrics.calculateMaxHrZones(profile.maxHeartRate)
                    }

                    val zoneColors = listOf(
                        Color(0xFF81D4FA), // Z1 Blue
                        Color(0xFFA5D6A7), // Z2 Green
                        Color(0xFFFFF59D), // Z3 Yellow
                        Color(0xFFFFCC80), // Z4 Orange
                        Color(0xFFEF9A9A) // Z5 Red
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        hrZones.forEachIndexed { index, zone ->
                            val zColor = zoneColors.getOrElse(index) { AccentGreen }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(zColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Z${zone.zoneNumber} ${zone.name}", fontSize = 12.sp, color = TextPrimary)
                                }

                                Text(
                                    "${zone.minBpm}–${zone.maxBpm} BPM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = zColor
                                )
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // 4. Collapsible Bike & Hardware Settings (Unified Hub)
            // ----------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isHardwareExpanded = !isHardwareExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Bike & Hardware Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Bluetooth, Health Connect, ERG engine", fontSize = 11.sp, color = TextMuted)
                            }
                        }

                        Icon(
                            imageVector = if (isHardwareExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isHardwareExpanded) "Collapse" else "Expand"
                        )
                    }

                    AnimatedVisibility(visible = isHardwareExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Hardware BLE Sub-card
                            Text("Bluetooth Setup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            val statusText = when (bleConnectionState) {
                                is BleConnectionState.Connected -> "Connected to ${(bleConnectionState as BleConnectionState.Connected).deviceName}"
                                is BleConnectionState.Connecting -> "Connecting..."
                                is BleConnectionState.Handshaking -> "Handshaking with bike..."
                                is BleConnectionState.Scanning -> "Scanning for Echelon bikes..."
                                else -> "Disconnected"
                            }
                            Text("Status: $statusText", style = MaterialTheme.typography.bodyMedium)

                            lastBleError?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(it, fontSize = 12.sp, color = AccentRed)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto-Connect on Scan")
                                Switch(
                                    checked = viewModel.bleManager.autoConnect,
                                    onCheckedChange = { viewModel.setAutoConnect(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        showScanDialog = true
                                        viewModel.bleManager.startScan()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Scan for Bike")
                                }

                                if (bleConnectionState is BleConnectionState.Connected) {
                                    OutlinedButton(
                                        onClick = { viewModel.bleManager.disconnect() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Disconnect")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            var macInput by remember { mutableStateOf("") }
                            var macError by remember { mutableStateOf<String?>(null) }
                            OutlinedTextField(
                                value = macInput,
                                onValueChange = {
                                    macInput = it
                                    macError = null
                                },
                                label = { Text("Manual MAC (fallback)") },
                                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                                singleLine = true,
                                isError = macError != null,
                                modifier = Modifier.fillMaxWidth()
                            )
                            macError?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    val result = viewModel.bleManager.connectToMac(macInput)
                                    macError = result.exceptionOrNull()?.message
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Connect to MAC")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Health Connect Sub-card
                            Text("Health Connect Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            val availabilityText = when (healthStatus.providerAvailable) {
                                true -> "Health Connect is installed"
                                false -> "Health Connect not available on this device"
                                null -> "Checking availability…"
                            }
                            Text(availabilityText, style = MaterialTheme.typography.bodyMedium)

                            val permissionText = when (healthStatus.permissionsGranted) {
                                true -> "Permissions granted — workouts sync on completion"
                                false -> "Permissions missing — workouts will not sync"
                                null -> "Permission status unknown"
                            }
                            Text(permissionText, style = MaterialTheme.typography.bodySmall, color = TextMuted)

                            if (healthSyncState is HealthSyncState.Failed) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Last sync failed: ${healthSyncState.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentRed
                                )
                            }

                            if (healthStatus.permissionsGranted != true) {
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = onHealthConnectClick,
                                    enabled = healthStatus.providerAvailable != false,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Health Connect Permissions")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Advanced ERG Engine Tuning
                            Text("Advanced Engine Tuning", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Proportional Gain (Kp): %.3f".format(sliderKp), style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = sliderKp,
                                onValueChange = { sliderKp = it },
                                valueRange = 0.01f..0.20f,
                                onValueChangeFinished = {
                                    viewModel.updateEngineTuning(sliderFloor, sliderRecovery, sliderKp, sliderKi)
                                }
                            )

                            Text("Integral Gain (Ki): %.4f".format(sliderKi), style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = sliderKi,
                                onValueChange = { sliderKi = it },
                                valueRange = 0.001f..0.05f,
                                onValueChangeFinished = {
                                    viewModel.updateEngineTuning(sliderFloor, sliderRecovery, sliderKp, sliderKi)
                                }
                            )

                            Text("Cadence Floor: $sliderFloor RPM (Bailout below this)", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = sliderFloor.toFloat(),
                                onValueChange = { sliderFloor = it.roundToInt() },
                                valueRange = 50f..70f,
                                steps = 19,
                                onValueChangeFinished = {
                                    viewModel.updateEngineTuning(sliderFloor, sliderRecovery, sliderKp, sliderKi)
                                }
                            )

                            Text("Recovery Threshold: $sliderRecovery RPM (Sustained 3s to re-engage)", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = sliderRecovery.toFloat(),
                                onValueChange = { sliderRecovery = it.roundToInt() },
                                valueRange = 70f..85f,
                                steps = 14,
                                onValueChangeFinished = {
                                    viewModel.updateEngineTuning(sliderFloor, sliderRecovery, sliderKp, sliderKi)
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.resetEngineTuningToDefaults()
                                        sliderKp = 0.05f
                                        sliderKi = 0.01f
                                        sliderFloor = 60
                                        sliderRecovery = 75
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset to Defaults")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

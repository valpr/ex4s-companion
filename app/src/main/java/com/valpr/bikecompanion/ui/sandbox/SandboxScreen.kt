package com.valpr.bikecompanion.ui.sandbox

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.ui.components.DeviceScanDialog
import com.valpr.bikecompanion.ui.components.MetricCard
import com.valpr.bikecompanion.ui.components.PacketLogConsole
import com.valpr.bikecompanion.ui.components.ResistanceControl
import com.valpr.bikecompanion.ui.components.WattTableTester
import com.valpr.bikecompanion.ui.theme.AccentAmber
import com.valpr.bikecompanion.ui.theme.AccentCyan
import com.valpr.bikecompanion.ui.theme.AccentGreen
import com.valpr.bikecompanion.ui.theme.AccentRed
import com.valpr.bikecompanion.ui.theme.DarkBackground
import com.valpr.bikecompanion.ui.theme.DarkSurface
import com.valpr.bikecompanion.ui.theme.DarkSurfaceVariant
import com.valpr.bikecompanion.ui.theme.TextMuted
import com.valpr.bikecompanion.ui.theme.TextPrimary

@Composable
fun SandboxScreen(
    viewModel: SandboxViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val packetLogs by viewModel.packetLogs.collectAsState()
    val isScanDialogOpen by viewModel.isScanDialogOpen.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isServiceActive by viewModel.isServiceActive.collectAsState()

    val isScanning = connectionState is BleConnectionState.Scanning

    if (isScanDialogOpen) {
        DeviceScanDialog(
            isScanning = isScanning,
            devices = discoveredDevices,
            onStartScan = { viewModel.startScan() },
            onStopScan = { viewModel.stopScan() },
            onSelectDevice = { viewModel.connectDevice(it) },
            onDismiss = { viewModel.closeScanDialog() }
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EX-4S COMPANION",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Phase 1 BLE Sandbox",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Foreground Service Button
                    OutlinedButton(
                        onClick = { viewModel.toggleForegroundService(context, onRequestPermissions) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isServiceActive) AccentGreen else TextMuted
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isServiceActive) AccentGreen else TextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isServiceActive) "Service On" else "Service Off",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Connect/Disconnect Button
                    when (connectionState) {
                        is BleConnectionState.Connected -> {
                            Button(
                                onClick = { viewModel.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }
                        is BleConnectionState.Connecting,
                        is BleConnectionState.DiscoveringServices,
                        is BleConnectionState.Handshaking -> {
                            Button(
                                onClick = { viewModel.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Handshaking...", color = DarkBackground, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    onRequestPermissions()
                                    viewModel.openScanDialog()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = DarkBackground),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Connection Status Pill
            val (statusColor, statusText) = when (val state = connectionState) {
                is BleConnectionState.Connected -> AccentGreen to "Connected: ${state.deviceName} (${state.address})"
                is BleConnectionState.Connecting -> AccentAmber to "Connecting to ${state.deviceName}..."
                is BleConnectionState.DiscoveringServices -> AccentAmber to "Discovering Echelon GATT Services..."
                is BleConnectionState.Handshaking -> AccentAmber to "Exchanging 7-step Handshake & Polling..."
                is BleConnectionState.Scanning -> AccentCyan to "Searching for Echelon bike (Auto-connect on)..."
                is BleConnectionState.Error -> AccentRed to "Error: ${state.message}"
                is BleConnectionState.Disconnecting -> AccentAmber to "Disconnecting..."
                is BleConnectionState.Disconnected -> TextMuted to "Disconnected • Bike standby"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            // Locked Bike Warning Banner (if detected)
            if (telemetry.isLockedFirmwareDetected) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentRed.copy(alpha = 0.2f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FIRMWARE LOCK DETECTED (Opcode 0xE0). This bike may require an unlocked firmware version or virtual bridge.",
                        color = AccentRed,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The Big 3 Metrics: Power, Cadence, Resistance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    label = "Power",
                    value = "${telemetry.estimatedWatts}",
                    unit = "W",
                    accentColor = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Cadence",
                    value = "${telemetry.cadenceRpm}",
                    unit = "RPM",
                    accentColor = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Metrics: Resistance, Speed, Distance, Elapsed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    label = "Resistance",
                    value = "${telemetry.resistanceLevel}",
                    unit = "/32",
                    accentColor = AccentAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Speed",
                    value = "%.1f".format(telemetry.speedKmh),
                    unit = "km/h",
                    accentColor = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    label = "Distance",
                    value = "%.2f".format(telemetry.distanceKm),
                    unit = "km",
                    accentColor = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Elapsed",
                    value = telemetry.formattedElapsedTime,
                    unit = "",
                    accentColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Electronic Resistance Controller
            ResistanceControl(
                currentResistance = telemetry.resistanceLevel,
                onResistanceChange = { newLevel ->
                    viewModel.setResistance(newLevel)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 33x11 Watt Table Verification Tester
            WattTableTester()

            Spacer(modifier = Modifier.height(16.dp))

            // Raw BLE Packet Traffic Inspector
            PacketLogConsole(
                logs = packetLogs,
                onClearLogs = { viewModel.clearLogs() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

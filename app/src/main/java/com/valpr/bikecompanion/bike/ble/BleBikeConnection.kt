package com.valpr.bikecompanion.bike.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.util.Log
import com.valpr.bikecompanion.bike.api.BikeCapabilities
import com.valpr.bikecompanion.bike.api.BikeController
import com.valpr.bikecompanion.bike.api.BikeDriver
import com.valpr.bikecompanion.bike.api.BikeProtocol
import com.valpr.bikecompanion.bike.api.MatchScore
import com.valpr.bikecompanion.bike.api.ParseResult
import com.valpr.bikecompanion.bike.api.ScanAdvert
import com.valpr.bikecompanion.bike.echelon.EchelonDriver
import com.valpr.bikecompanion.ble.BleCommand
import com.valpr.bikecompanion.ble.BleCommandQueue
import com.valpr.bikecompanion.ble.BluetoothStateReceiver
import com.valpr.bikecompanion.ble.BluetoothStateResolver
import com.valpr.bikecompanion.ble.CadenceZeroFilter
import com.valpr.bikecompanion.ble.EchelonBleLogic
import com.valpr.bikecompanion.ble.EchelonGattAttributes
import com.valpr.bikecompanion.ble.PacketLogRecorder
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.data.DiscoveredBikeDevice
import com.valpr.bikecompanion.data.PacketDirection
import com.valpr.bikecompanion.data.PacketLogEntry
import com.valpr.bikecompanion.shared.MacValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

open class BleBikeConnection(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val drivers: List<BikeDriver> = listOf(EchelonDriver())
) : BikeController {

    companion object {
        private const val TAG = "BleBikeConnection"
        private const val POLL_INTERVAL_MS = 2000L
        const val PACKET_LOG_DIR = "packetlogs"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeGatt: BluetoothGatt? = null
    private var activeProtocol: BikeProtocol = drivers.first().createProtocol()
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val notifyCharacteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()

    private var pollJob: Job? = null
    private var scanCallback: ScanCallback? = null

    override var autoConnect: Boolean = true
    private var userRequestedDisconnect: Boolean = false
    private var pollCounter: Int = 1
    private var lastTargetResistance: Int = -1
    private var consecutiveZeroCadenceFrames: Int = 0

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow(BikeTelemetry())
    override val telemetry: StateFlow<BikeTelemetry> = _telemetry.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBikeDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredBikeDevice>> = _discoveredDevices.asStateFlow()

    private val _capabilities = MutableStateFlow(activeProtocol.capabilities)
    override val capabilities: StateFlow<BikeCapabilities> = _capabilities.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun clearLastError() {
        _lastError.value = null
    }

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    override val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    override val hasBluetoothAdapter: Boolean
        get() = bluetoothAdapter != null

    private var bluetoothStateReceiver: BroadcastReceiver? = null

    private val _packetLog = MutableSharedFlow<PacketLogEntry>(extraBufferCapacity = 100)
    val packetLog: SharedFlow<PacketLogEntry> = _packetLog.asSharedFlow()

    override val packetLogRecorder = PacketLogRecorder(
        logDirectory = File(context.filesDir, PACKET_LOG_DIR),
        scope = scope,
        packetSource = packetLog,
        stateSource = connectionState
    )

    private val commandQueue = BleCommandQueue(
        scope = scope,
        gattProvider = { activeGatt },
        onPacketSent = { bytes, desc ->
            logPacket(PacketDirection.TX, "TX", bytes, desc)
        }
    )

    init {
        commandQueue.start()
        packetLogRecorder.start()
        startBluetoothMonitoring()
    }

    // region Bluetooth adapter monitoring

    private fun startBluetoothMonitoring() {
        if (bluetoothStateReceiver != null) return
        try {
            val receiver = BluetoothStateReceiver { enabled -> onBluetoothEnabledChanged(enabled) }
            val filter = BluetoothStateReceiver.intentFilter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            bluetoothStateReceiver = receiver
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth state monitoring unavailable: ${e.message}")
        }
        refreshBluetoothState()
    }

    private fun stopBluetoothMonitoring() {
        val receiver = bluetoothStateReceiver ?: return
        bluetoothStateReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister Bluetooth receiver: ${e.message}")
        }
    }

    override fun refreshBluetoothState() {
        onBluetoothEnabledChanged(bluetoothAdapter?.isEnabled == true)
    }

    private fun onBluetoothEnabledChanged(enabled: Boolean) {
        val wasEnabled = _isBluetoothEnabled.value
        _isBluetoothEnabled.value = enabled
        if (!enabled) {
            scanCallback = null
            if (!BluetoothStateResolver.isBluetoothDisabledError(_connectionState.value)) {
                _lastError.value = BluetoothStateResolver.DISABLED_MESSAGE
                _connectionState.value =
                    BleConnectionState.Error(BluetoothStateResolver.DISABLED_MESSAGE)
            }
        } else if (!wasEnabled && BluetoothStateResolver.isBluetoothDisabledError(_connectionState.value)) {
            _connectionState.value = BleConnectionState.Disconnected
            _lastError.value = null
            if (autoConnect && !userRequestedDisconnect) {
                startScan()
            }
        }
    }

    // endregion

    // region Scanning

    @SuppressLint("MissingPermission")
    override fun startScan() {
        userRequestedDisconnect = false
        stopScan()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _isBluetoothEnabled.value = false
            _lastError.value = BluetoothStateResolver.DISABLED_MESSAGE
            _connectionState.value = BleConnectionState.Error(BluetoothStateResolver.DISABLED_MESSAGE)
            return
        }
        _isBluetoothEnabled.value = true

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = BleConnectionState.Error("BLE scanner unavailable")
            return
        }

        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (res in results) {
                    handleScanResult(res)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE Scan failed with error code: $errorCode")
                _connectionState.value = BleConnectionState.Error("Scan failed: error $errorCode")
            }
        }

        try {
            Log.i(TAG, "Starting BLE scan in LOW_LATENCY mode...")
            scanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan: ${e.message}", e)
            _connectionState.value = BleConnectionState.Error("Failed to start scan: ${e.message}")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val record = result.scanRecord
        val advertisedName = record?.deviceName
        val deviceName = try {
            device.name
        } catch (e: SecurityException) {
            null
        }

        val serviceUuids = record?.serviceUuids?.map { it.uuid } ?: emptyList()
        val existing = _discoveredDevices.value.find { it.address == device.address }
        val resolvedName = when {
            !deviceName.isNullOrBlank() -> deviceName
            !advertisedName.isNullOrBlank() -> advertisedName
            serviceUuids.contains(EchelonGattAttributes.SERVICE_ECHELON) -> "Echelon EX-4S (Identified by UUID)"
            existing != null && existing.name != "Unknown Device" -> existing.name
            else -> "Unknown Device"
        }

        val advert = ScanAdvert(
            name = resolvedName,
            address = device.address,
            serviceUuids = serviceUuids,
            rssi = result.rssi
        )

        // Find best matching driver
        var bestDriver: BikeDriver? = null
        var bestScore = MatchScore.NONE
        for (driver in drivers) {
            val score = driver.match(advert)
            if (score > bestScore) {
                bestScore = score
                bestDriver = driver
            }
        }

        val isEchelon = bestDriver?.id == "echelon" || EchelonBleLogic.isEchelonDevice(resolvedName, serviceUuids.contains(EchelonGattAttributes.SERVICE_ECHELON))
        val address = device.address
        val rssi = result.rssi

        Log.d(TAG, "BLE Device seen: '$resolvedName' [$address] rssi=$rssi, driver=${bestDriver?.id}, score=$bestScore")

        if (autoConnect && bestScore != MatchScore.NONE && _connectionState.value is BleConnectionState.Scanning) {
            Log.i(
                TAG,
                "Auto-Connect: Found bike '$resolvedName' [$address] via driver '${bestDriver?.id}'. Connecting immediately..."
            )
            connect(DiscoveredBikeDevice(device, resolvedName, address, rssi, isEchelon, bestDriver?.id))
            return
        }

        _discoveredDevices.update { current ->
            val existingIndex = current.indexOfFirst { it.address == address }
            val updatedItem = DiscoveredBikeDevice(device, resolvedName, address, rssi, isEchelon, bestDriver?.id)
            val updatedList = if (existingIndex >= 0) {
                current.toMutableList().apply { set(existingIndex, updatedItem) }
            } else {
                current + updatedItem
            }
            updatedList.sortedWith(
                compareByDescending<DiscoveredBikeDevice> { it.isEchelonDevice }
                    .thenByDescending { it.rssi }
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        val callback = scanCallback
        scanCallback = null
        if (callback != null && bluetoothAdapter?.isEnabled == true) {
            try {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Exception stopping BLE scan: ${e.message}")
            }
            if (_connectionState.value is BleConnectionState.Scanning) {
                _connectionState.value = BleConnectionState.Disconnected
            }
        }
    }

    // endregion

    // region Connection & Handshake

    @SuppressLint("MissingPermission")
    override fun connect(device: DiscoveredBikeDevice) {
        connect(device.device, device.driverId)
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BluetoothDevice, driverId: String?) {
        userRequestedDisconnect = false
        _lastError.value = null
        stopScan()

        val selectedDriver = (if (driverId != null) drivers.find { it.id == driverId } else null)
            ?: drivers.first()
        activeProtocol = selectedDriver.createProtocol()
        _capabilities.value = activeProtocol.capabilities

        val deviceName = device.name ?: activeProtocol.capabilities.modelName
        _connectionState.value = BleConnectionState.Connecting(deviceName, device.address)

        scope.launch {
            disconnectInternal(cleanState = false)
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectToMac(mac: String, driverId: String?): Result<Unit> {
        val validated = MacValidator.validate(mac)
            .getOrElse { return Result.failure(it) }
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth unavailable"))
        return try {
            connect(adapter.getRemoteDevice(validated), driverId)
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        userRequestedDisconnect = true
        scope.launch {
            disconnectInternal(cleanState = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal(cleanState: Boolean) {
        stopKeepAlivePoll()
        activeGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Exception during gatt close: ${e.message}")
            }
        }
        activeGatt = null
        writeCharacteristic = null
        notifyCharacteristics.clear()

        if (cleanState) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "GATT state change: status=$status, newState=$newState")
            val deviceName = gatt.device.name ?: activeProtocol.capabilities.modelName
            val address = gatt.device.address

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT error status: $status")
                val msg = "Connection error: status $status. If the bike won't connect, " +
                    "force-close any official bike app (single BLE master), don't pair in " +
                    "system Bluetooth settings, and rename the bike to a short name."
                _lastError.value = msg
                _connectionState.value = BleConnectionState.Error(msg)
                disconnectInternal(cleanState = false)
                if (!userRequestedDisconnect && autoConnect) {
                    Log.i(TAG, "Auto-Reconnect: attempting recovery scan in 2s...")
                    scope.launch {
                        delay(2000)
                        startScan()
                    }
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.DiscoveringServices
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected (userRequested=$userRequestedDisconnect)")
                    disconnectInternal(cleanState = true)
                    if (!userRequestedDisconnect && autoConnect) {
                        Log.i(TAG, "Auto-Reconnect: restarting scan in 1.5s...")
                        scope.launch {
                            delay(1500)
                            if (_connectionState.value is BleConnectionState.Disconnected) {
                                startScan()
                            }
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status $status")
                _connectionState.value = BleConnectionState.Error("Service discovery failed")
                return
            }

            // Verify or dynamically resolve protocol matching discovered services
            var primaryService = gatt.getService(activeProtocol.serviceUuid)
            if (primaryService == null) {
                // Check if another driver matches discovered services
                val discoveredUuids = gatt.services.map { it.uuid }
                val fallbackDriver = drivers.find { driver ->
                    val candidate = driver.createProtocol()
                    discoveredUuids.contains(candidate.serviceUuid)
                }
                if (fallbackDriver != null) {
                    activeProtocol = fallbackDriver.createProtocol()
                    _capabilities.value = activeProtocol.capabilities
                    primaryService = gatt.getService(activeProtocol.serviceUuid)
                }
            }

            if (primaryService == null) {
                Log.e(TAG, "Required GATT service not found on device!")
                _connectionState.value = BleConnectionState.Error("Required bike GATT service not found")
                return
            }

            writeCharacteristic = primaryService.getCharacteristic(activeProtocol.writeCharacteristic)
            notifyCharacteristics.clear()
            for (uuid in activeProtocol.notifyCharacteristics) {
                primaryService.getCharacteristic(uuid)?.let {
                    notifyCharacteristics[uuid] = it
                }
            }

            if (writeCharacteristic == null || notifyCharacteristics.isEmpty()) {
                Log.e(TAG, "One or more required characteristics missing")
                _connectionState.value = BleConnectionState.Error("Required bike characteristics not found")
                return
            }

            // High connection priority for low-latency resistance changes
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

            scope.launch {
                setupNotificationsAndHandshake(gatt)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            commandQueue.onCharacteristicWriteAcknowledged(status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            commandQueue.onDescriptorWriteAcknowledged(status)
        }

        @Deprecated("Deprecated in Java API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotificationBytes(characteristic, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotificationBytes(characteristic, value)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun setupNotificationsAndHandshake(gatt: BluetoothGatt) {
        _connectionState.value = BleConnectionState.Handshaking

        // 1. Enable local notifications and CCCD writes for all notify characteristics
        for ((_, characteristic) in notifyCharacteristics) {
            gatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(EchelonGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
            if (cccd != null) {
                commandQueue.enqueue(
                    BleCommand.WriteDescriptor(
                        descriptor = cccd,
                        data = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                        description = "Enable CCCD ${characteristic.uuid}"
                    )
                )
            }
        }

        // 2. Send protocol handshake sequence
        val handshakePackets = activeProtocol.createHandshake()
        val writeChar = writeCharacteristic
        if (writeChar != null) {
            for (packet in handshakePackets) {
                val success = commandQueue.enqueue(
                    BleCommand.WriteCharacteristic(
                        characteristic = writeChar,
                        data = packet.data,
                        description = packet.description
                    )
                )
                if (!success) {
                    Log.w(TAG, "Handshake step '${packet.description}' failed to acknowledge, continuing...")
                }
            }
        }

        // 3. Connected!
        val deviceName = gatt.device.name ?: activeProtocol.capabilities.modelName
        _connectionState.value = BleConnectionState.Connected(deviceName, gatt.device.address)

        startKeepAlivePoll()

        if (lastTargetResistance > 0) {
            setResistance(lastTargetResistance)
        }
    }

    // endregion

    // region Telemetry Processing

    private fun handleNotificationBytes(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        when (val result = activeProtocol.parseNotification(characteristic.uuid, data, _telemetry.value)) {
            is ParseResult.TelemetryUpdate -> {
                if (result.rawCadenceRpm != null) {
                    consecutiveZeroCadenceFrames = CadenceZeroFilter.nextZeroStreak(
                        consecutiveZeroCadenceFrames,
                        result.rawCadenceRpm
                    )
                    val holdLast = result.rawCadenceRpm <= 0 &&
                        !CadenceZeroFilter.shouldAcceptZero(consecutiveZeroCadenceFrames)

                    logPacket(
                        PacketDirection.RX,
                        result.logOpcode,
                        data,
                        result.logDescription + if (holdLast) " (held last good)" else ""
                    )

                    if (holdLast) {
                        _telemetry.update { current ->
                            // Update elapsed/distance but hold cadence & watts
                            val updated = result.update(current)
                            current.copy(
                                elapsedSeconds = updated.elapsedSeconds,
                                distanceKm = updated.distanceKm,
                                lastUpdateTimestampMs = System.currentTimeMillis()
                            )
                        }
                        return
                    }
                } else {
                    logPacket(PacketDirection.RX, result.logOpcode, data, result.logDescription)
                }

                if (result.rawResistance != null) {
                    lastTargetResistance = result.rawResistance
                }

                _telemetry.update { current ->
                    result.update(current).copy(lastUpdateTimestampMs = System.currentTimeMillis())
                }
            }
            is ParseResult.LockedWarning -> {
                logPacket(PacketDirection.RX, "Locked", data, result.logDescription)
                _telemetry.update { it.copy(isLockedFirmwareDetected = true) }
            }
            is ParseResult.Ignored -> {
                logPacket(PacketDirection.RX, "Unknown", data, result.reason)
            }
        }
    }

    private fun logPacket(direction: PacketDirection, opcode: String, rawBytes: ByteArray, description: String) {
        _packetLog.tryEmit(
            PacketLogEntry(
                direction = direction,
                opcode = opcode,
                rawBytes = rawBytes,
                description = description
            )
        )
    }

    // endregion

    // region Resistance & Polling Commands

    override fun setResistance(level: Int) {
        val writeChar = writeCharacteristic
        if (writeChar == null) {
            Log.w(TAG, "setResistance($level) dropped: handshake not complete")
            return
        }

        val packet = activeProtocol.createResistanceCommand(level)
        lastTargetResistance = level.coerceIn(activeProtocol.capabilities.resistanceRange)

        scope.launch {
            commandQueue.enqueue(
                BleCommand.WriteCharacteristic(
                    characteristic = writeChar,
                    data = packet.data,
                    description = packet.description
                )
            )
        }
    }

    override fun setTargetPower(watts: Int) {
        val writeChar = writeCharacteristic ?: return
        val packet = activeProtocol.createTargetPowerCommand(watts) ?: return

        scope.launch {
            commandQueue.enqueue(
                BleCommand.WriteCharacteristic(
                    characteristic = writeChar,
                    data = packet.data,
                    description = packet.description
                )
            )
        }
    }

    private fun startKeepAlivePoll() {
        stopKeepAlivePoll()
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val writeChar = writeCharacteristic ?: continue
                val sentCounter = pollCounter
                val packet = activeProtocol.createKeepAlive(sentCounter) ?: continue
                pollCounter = EchelonBleLogic.nextPollCounter(sentCounter)

                commandQueue.enqueue(
                    BleCommand.WriteCharacteristic(
                        characteristic = writeChar,
                        data = packet.data,
                        description = packet.description
                    )
                )
            }
        }
    }

    private fun stopKeepAlivePoll() {
        pollJob?.cancel()
        pollJob = null
    }

    // endregion

    override fun onDestroy() {
        stopBluetoothMonitoring()
        packetLogRecorder.stop()
        stopKeepAlivePoll()
        commandQueue.stop()
        disconnectInternal(cleanState = true)
    }
}

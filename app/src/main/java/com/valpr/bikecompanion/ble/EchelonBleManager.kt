package com.valpr.bikecompanion.ble

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
import android.content.Context
import android.os.Build
import android.util.Log
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.data.DiscoveredBikeDevice
import com.valpr.bikecompanion.data.EchelonWattTable
import com.valpr.bikecompanion.data.PacketDirection
import com.valpr.bikecompanion.data.PacketLogEntry
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

class EchelonBleManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    companion object {
        private const val TAG = "EchelonBleManager"
        private const val POLL_INTERVAL_MS = 2000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notify1Characteristic: BluetoothGattCharacteristic? = null
    private var notify2Characteristic: BluetoothGattCharacteristic? = null

    private var pollJob: Job? = null
    private var scanCallback: ScanCallback? = null

    var autoConnect: Boolean = true
    private var userRequestedDisconnect: Boolean = false
    private var pollCounter: Int = 1
    private var lastTargetResistance: Int = -1

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow(BikeTelemetry())
    val telemetry: StateFlow<BikeTelemetry> = _telemetry.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBikeDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBikeDevice>> = _discoveredDevices.asStateFlow()

    /** Sticky last error kept across auto-reconnect scans so the UI banner isn't lost. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun clearLastError() { _lastError.value = null }

    private val _packetLog = MutableSharedFlow<PacketLogEntry>(extraBufferCapacity = 100)
    val packetLog: SharedFlow<PacketLogEntry> = _packetLog.asSharedFlow()

    private val commandQueue = BleCommandQueue(
        scope = scope,
        gattProvider = { activeGatt },
        onPacketSent = { bytes, desc ->
            logPacket(PacketDirection.TX, "TX", bytes, desc)
        }
    )

    init {
        commandQueue.start()
    }

    // region Scanning

    @SuppressLint("MissingPermission")
    fun startScan() {
        userRequestedDisconnect = false
        stopScan()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is disabled. Please turn on Bluetooth.")
            return
        }

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
        val isEchelonUuid = serviceUuids.contains(EchelonGattAttributes.SERVICE_ECHELON)

        val existing = _discoveredDevices.value.find { it.address == device.address }
        val resolvedName = when {
            !deviceName.isNullOrBlank() -> deviceName
            !advertisedName.isNullOrBlank() -> advertisedName
            isEchelonUuid -> "Echelon EX-4S (Identified by UUID)"
            existing != null && existing.name != "Unknown Device" -> existing.name
            else -> "Unknown Device"
        }

        val isEchelon = EchelonBleLogic.isEchelonDevice(resolvedName, isEchelonUuid)

        val address = device.address
        val rssi = result.rssi

        Log.d(TAG, "BLE Device seen: '$resolvedName' [$address] rssi=$rssi, isEchelon=$isEchelon")

        if (autoConnect && isEchelon && _connectionState.value is BleConnectionState.Scanning) {
            Log.i(TAG, "QZ-style Auto-Connect: Found Echelon bike '$resolvedName' [$address]. Connecting immediately...")
            connect(device)
            return
        }

        _discoveredDevices.update { current ->
            val existingIndex = current.indexOfFirst { it.address == address }
            val updatedItem = DiscoveredBikeDevice(device, resolvedName, address, rssi, isEchelon)
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
    fun stopScan() {
        if (scanCallback != null && bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            scanCallback = null
            if (_connectionState.value is BleConnectionState.Scanning) {
                _connectionState.value = BleConnectionState.Disconnected
            }
        }
    }

    // endregion

    // region Connection & Handshake

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        userRequestedDisconnect = false
        _lastError.value = null
        stopScan()
        val deviceName = device.name ?: "Echelon Bike"
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

    /**
     * Manual MAC fallback for bikes whose advertisements fail auto-scan name matching
     * (long-name bug, vendor-variant ads). Validates format before resolving.
     */
    @SuppressLint("MissingPermission")
    fun connectToMac(mac: String): Result<Unit> {
        val validated = com.valpr.bikecompanion.shared.MacValidator.validate(mac)
            .getOrElse { return Result.failure(it) }
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth unavailable"))
        return try {
            connect(adapter.getRemoteDevice(validated))
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
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
        notify1Characteristic = null
        notify2Characteristic = null

        if (cleanState) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "GATT state change: status=$status, newState=$newState")
            val deviceName = gatt.device.name ?: "Echelon Bike"
            val address = gatt.device.address

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT error status: $status")
                val msg = "Connection error: status $status. If the bike won't connect, " +
                    "force-close the Echelon Fit app (single BLE master), don't pair in " +
                    "system Bluetooth settings, and rename the bike to a short name."
                _lastError.value = msg
                _connectionState.value = BleConnectionState.Error(msg)
                disconnectInternal(cleanState = false)
                if (!userRequestedDisconnect && autoConnect) {
                    Log.i(TAG, "QZ-style Auto-Reconnect: attempting recovery scan in 2s...")
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
                    // Request service discovery
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected (userRequested=$userRequestedDisconnect)")
                    disconnectInternal(cleanState = true)
                    if (!userRequestedDisconnect && autoConnect) {
                        Log.i(TAG, "QZ-style Auto-Reconnect: restarting scan in 1.5s...")
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

            val echelonService = gatt.getService(EchelonGattAttributes.SERVICE_ECHELON)
            if (echelonService == null) {
                Log.e(TAG, "Echelon service not found on device!")
                _connectionState.value = BleConnectionState.Error("Echelon proprietary GATT service not found")
                return
            }

            writeCharacteristic = echelonService.getCharacteristic(EchelonGattAttributes.CHAR_WRITE)
            notify1Characteristic = echelonService.getCharacteristic(EchelonGattAttributes.CHAR_NOTIFY_1)
            notify2Characteristic = echelonService.getCharacteristic(EchelonGattAttributes.CHAR_NOTIFY_2)

            if (writeCharacteristic == null || notify1Characteristic == null || notify2Characteristic == null) {
                Log.e(TAG, "One or more Echelon characteristics missing")
                _connectionState.value = BleConnectionState.Error("Required Echelon characteristics not found")
                return
            }

            // Request high connection priority immediately after discovery (architecture section A.8)
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

            // Initiate subscription and handshake sequence
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

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            commandQueue.onDescriptorWriteAcknowledged(status)
        }

        @Deprecated("Deprecated in Java API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleNotificationBytes(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotificationBytes(value)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun setupNotificationsAndHandshake(gatt: BluetoothGatt) {
        _connectionState.value = BleConnectionState.Handshaking

        val notify1 = notify1Characteristic ?: return
        val notify2 = notify2Characteristic ?: return
        val writeChar = writeCharacteristic ?: return

        // 1. Enable notifications locally
        gatt.setCharacteristicNotification(notify1, true)
        gatt.setCharacteristicNotification(notify2, true)

        // 2. Write CCCD to Notify 1
        val cccdDescriptor1 = notify1.getDescriptor(EchelonGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
        if (cccdDescriptor1 != null) {
            commandQueue.enqueue(
                BleCommand.WriteDescriptor(
                    descriptor = cccdDescriptor1,
                    data = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                    description = "Enable CCCD Notify 1"
                )
            )
        }

        // 3. Write CCCD to Notify 2
        val cccdDescriptor2 = notify2.getDescriptor(EchelonGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
        if (cccdDescriptor2 != null) {
            commandQueue.enqueue(
                BleCommand.WriteDescriptor(
                    descriptor = cccdDescriptor2,
                    data = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                    description = "Enable CCCD Notify 2"
                )
            )
        }

        // 4. Send handshake sequence
        val sequence = EchelonProtocol.getFullHandshakeSequence()
        for ((packet, desc) in sequence) {
            val success = commandQueue.enqueue(
                BleCommand.WriteCharacteristic(
                    characteristic = writeChar,
                    data = packet,
                    description = desc
                )
            )
            if (!success) {
                Log.w(TAG, "Handshake step '$desc' failed to acknowledge, continuing...")
            }
        }

        // 5. Handshake complete! Set state to connected and start keep-alive polling
        val deviceName = gatt.device.name ?: "Echelon Bike"
        _connectionState.value = BleConnectionState.Connected(deviceName, gatt.device.address)

        startKeepAlivePoll()

        // Re-apply last resistance if reconnected
        if (lastTargetResistance > 0) {
            setResistance(lastTargetResistance)
        }
    }

    // endregion

    // region Telemetry Processing

    private fun handleNotificationBytes(data: ByteArray) {
        val parsed = EchelonPacketParser.parse(data)
        when (parsed) {
            is ParsedPacket.CadenceFrame -> {
                logPacket(PacketDirection.RX, "0xD1 Cadence", data, "Cadence: ${parsed.cadenceRpm} RPM, Dist: %.2f km".format(parsed.distanceKm))
                _telemetry.update { current ->
                    val watts = EchelonWattTable.calculateWattsInt(
                        resistance = current.resistanceLevel,
                        cadenceRpm = parsed.cadenceRpm.toDouble()
                    )
                    current.copy(
                        cadenceRpm = parsed.cadenceRpm,
                        elapsedSeconds = parsed.elapsedSeconds,
                        distanceKm = parsed.distanceKm,
                        speedKmh = parsed.speedKmh,
                        estimatedWatts = watts,
                        lastUpdateTimestampMs = System.currentTimeMillis()
                    )
                }
            }
            is ParsedPacket.ResistanceFrame -> {
                logPacket(PacketDirection.RX, "0xD2 Resistance", data, "Resistance Level: ${parsed.resistanceLevel}")
                lastTargetResistance = parsed.resistanceLevel
                _telemetry.update { current ->
                    val watts = EchelonWattTable.calculateWattsInt(
                        resistance = parsed.resistanceLevel,
                        cadenceRpm = current.cadenceRpm.toDouble()
                    )
                    current.copy(
                        resistanceLevel = parsed.resistanceLevel,
                        estimatedWatts = watts,
                        lastUpdateTimestampMs = System.currentTimeMillis()
                    )
                }
            }
            is ParsedPacket.LockedBikeFrame -> {
                logPacket(PacketDirection.RX, "0xE0 Locked", data, "WARNING: Bike firmware lock detected!")
                _telemetry.update { it.copy(isLockedFirmwareDetected = true) }
            }
            is ParsedPacket.UnknownFrame -> {
                logPacket(PacketDirection.RX, "Unknown", data, parsed.reason)
            }
        }
    }

    private fun logPacket(
        direction: PacketDirection,
        opcode: String,
        rawBytes: ByteArray,
        description: String
    ) {
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

    fun setResistance(level: Int) {
        val safeLevel = level.coerceIn(1, 32)
        val writeChar = writeCharacteristic
        if (writeChar == null) {
            Log.w(TAG, "setResistance($safeLevel) dropped: handshake not complete")
            return
        }

        lastTargetResistance = safeLevel
        val commandBytes = EchelonProtocol.createResistanceCommand(safeLevel)

        scope.launch {
            commandQueue.enqueue(
                BleCommand.WriteCharacteristic(
                    characteristic = writeChar,
                    data = commandBytes,
                    description = "Set Resistance $safeLevel"
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
                val pollBytes = EchelonProtocol.createPollCommand(sentCounter)
                pollCounter = EchelonBleLogic.nextPollCounter(sentCounter)

                commandQueue.enqueue(
                    BleCommand.WriteCharacteristic(
                        characteristic = writeChar,
                        data = pollBytes,
                        description = "Poll (counter=$sentCounter)"
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

    fun onDestroy() {
        stopKeepAlivePoll()
        commandQueue.stop()
        disconnectInternal(cleanState = true)
    }
}

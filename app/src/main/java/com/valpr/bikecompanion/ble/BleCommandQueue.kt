package com.valpr.bikecompanion.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class BleCommandQueue(
    private val scope: CoroutineScope,
    private val gattProvider: () -> BluetoothGatt?,
    private val onPacketSent: (ByteArray, String) -> Unit = { _, _ -> },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: (isError: Boolean, msg: String) -> Unit =
        { isError, msg -> if (isError) Log.e(TAG, msg) else Log.w(TAG, msg) }
) {
    companion object {
        private const val TAG = "BleCommandQueue"
        private const val WRITE_TIMEOUT_MS = 2500L
        private const val POST_WRITE_COOLDOWN_MS = 40L
    }

    private val queue = Channel<BleCommand>(Channel.UNLIMITED)
    private var activeCommand: BleCommand? = null
    private var workerJob: Job? = null

    fun start() {
        if (workerJob != null) return
        workerJob = scope.launch(workDispatcher) {
            processQueue()
        }
    }

    fun stop() {
        workerJob?.cancel()
        workerJob = null
        activeCommand?.completion?.cancel()
        activeCommand = null
    }

    suspend fun enqueue(command: BleCommand): Boolean = withContext(workDispatcher) {
        queue.send(command)
        try {
            val status = withTimeout(WRITE_TIMEOUT_MS + 500L) {
                command.completion.await()
            }
            status == BluetoothGatt.GATT_SUCCESS
        } catch (e: TimeoutCancellationException) {
            logger(false, "Command '${command.description}' timed out awaiting peripheral ack")
            false
        } catch (e: Exception) {
            logger(true, "Command '${command.description}' failed: ${e.message}")
            false
        }
    }

    fun onCharacteristicWriteAcknowledged(status: Int) {
        val current = activeCommand
        if (current is BleCommand.WriteCharacteristic) {
            current.completion.complete(status)
        }
    }

    fun onDescriptorWriteAcknowledged(status: Int) {
        val current = activeCommand
        if (current is BleCommand.WriteDescriptor) {
            current.completion.complete(status)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun processQueue() {
        while (scope.isActive) {
            val command = queue.receive()
            activeCommand = command

            val gatt = gattProvider()
            if (gatt == null) {
                logger(true, "Cannot execute command '${command.description}': GATT is null")
                command.completion.complete(BluetoothGatt.GATT_FAILURE)
                activeCommand = null
                continue
            }

            try {
                val writeInitiated = when (command) {
                    is BleCommand.WriteCharacteristic -> {
                        executeWriteCharacteristic(gatt, command)
                    }
                    is BleCommand.WriteDescriptor -> {
                        executeWriteDescriptor(gatt, command)
                    }
                }

                if (!writeInitiated) {
                    logger(true, "Failed to initiate write for '${command.description}'")
                    command.completion.complete(BluetoothGatt.GATT_FAILURE)
                } else {
                    // Wait for GATT callback acknowledgment
                    try {
                        withTimeout(WRITE_TIMEOUT_MS) {
                            command.completion.await()
                        }
                    } catch (e: TimeoutCancellationException) {
                        logger(false, "Timeout waiting for GATT ack on '${command.description}'")
                    }
                }
            } catch (e: Exception) {
                logger(true, "Error executing '${command.description}': ${e.message}")
                command.completion.complete(BluetoothGatt.GATT_FAILURE)
            } finally {
                activeCommand = null
                // Post-write cooldown to allow peripheral MCU processing
                delay(POST_WRITE_COOLDOWN_MS)
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun executeWriteCharacteristic(gatt: BluetoothGatt, command: BleCommand.WriteCharacteristic): Boolean {
        onPacketSent(command.data, command.description)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = gatt.writeCharacteristic(
                command.characteristic,
                command.data,
                command.writeType
            )
            res == BluetoothGatt.GATT_SUCCESS
        } else {
            command.characteristic.writeType = command.writeType
            command.characteristic.value = command.data
            gatt.writeCharacteristic(command.characteristic)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun executeWriteDescriptor(gatt: BluetoothGatt, command: BleCommand.WriteDescriptor): Boolean {
        onPacketSent(command.data, command.description)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = gatt.writeDescriptor(
                command.descriptor,
                command.data
            )
            res == BluetoothGatt.GATT_SUCCESS
        } else {
            command.descriptor.value = command.data
            gatt.writeDescriptor(command.descriptor)
        }
    }
}

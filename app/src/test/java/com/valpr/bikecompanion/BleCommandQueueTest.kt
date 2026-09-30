package com.valpr.bikecompanion

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.valpr.bikecompanion.ble.BleCommand
import com.valpr.bikecompanion.ble.BleCommandQueue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BleCommandQueueTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private val logs = mutableListOf<String>()

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        logs.clear()
    }

    private fun queue(gatt: BluetoothGatt?) = BleCommandQueue(
        scope = testScope,
        gattProvider = { gatt },
        workDispatcher = testDispatcher,
        logger = { _, msg -> logs.add(msg) }
    )

    private fun characteristic(): BluetoothGattCharacteristic {
        val c = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { c.uuid } returns UUID.randomUUID()
        return c
    }

    @Test
    fun nullGatt_failsFastAndLogs() = runTest(testDispatcher) {
        val q = queue(null)
        q.start()
        val cmd = BleCommand.WriteCharacteristic(
            characteristic = characteristic(),
            data = byteArrayOf(1, 2),
            description = "t"
        )
        var result: Boolean? = null
        val job = launch { result = q.enqueue(cmd) }
        runCurrent()
        runCurrent()
        job.join()
        assertTrue(result == false)
        assertTrue(logs.any { it.contains("GATT is null") })
        q.stop()
    }

    @Test
    fun ackSuccess_returnsTrue() = runTest(testDispatcher) {
        val gatt = mockk<BluetoothGatt>()
        every { gatt.writeCharacteristic(any<BluetoothGattCharacteristic>()) } returns true
        val q = queue(gatt)
        q.start()
        val cmd = BleCommand.WriteCharacteristic(
            characteristic = characteristic(),
            data = byteArrayOf(1, 2),
            description = "t"
        )
        var result: Boolean? = null
        val job = launch { result = q.enqueue(cmd) }
        // Step the shared scheduler without advancing time (timeouts must not fire).
        runCurrent()
        runCurrent()
        q.onCharacteristicWriteAcknowledged(BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        runCurrent()
        job.join()
        assertTrue(result == true)
        q.stop()
    }

    @Test
    fun failedAck_returnsFalse() = runTest(testDispatcher) {
        val gatt = mockk<BluetoothGatt>()
        every { gatt.writeCharacteristic(any<BluetoothGattCharacteristic>()) } returns true
        val q = queue(gatt)
        q.start()
        val cmd = BleCommand.WriteCharacteristic(
            characteristic = characteristic(),
            data = byteArrayOf(1, 2),
            description = "t"
        )
        var result: Boolean? = null
        val job = launch { result = q.enqueue(cmd) }
        runCurrent()
        runCurrent()
        q.onCharacteristicWriteAcknowledged(BluetoothGatt.GATT_FAILURE)
        runCurrent()
        runCurrent()
        job.join()
        assertTrue(result == false)
        q.stop()
    }

    @Test
    fun start_isIdempotent() = runTest(testDispatcher) {
        val q = queue(null)
        q.start()
        q.start() // must not throw / spawn second worker
        q.stop()
    }

    @Test
    fun descriptorAck_completes() = runTest(testDispatcher) {
        val gatt = mockk<BluetoothGatt>()
        every { gatt.writeDescriptor(any<BluetoothGattDescriptor>()) } returns true
        val q = queue(gatt)
        q.start()
        val desc = mockk<BluetoothGattDescriptor>(relaxed = true)
        val cmd = BleCommand.WriteDescriptor(
            descriptor = desc,
            data = byteArrayOf(1),
            description = "d"
        )
        var result: Boolean? = null
        val job = launch { result = q.enqueue(cmd) }
        runCurrent()
        runCurrent()
        q.onDescriptorWriteAcknowledged(BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        runCurrent()
        job.join()
        assertTrue(result == true)
        q.stop()
    }
}

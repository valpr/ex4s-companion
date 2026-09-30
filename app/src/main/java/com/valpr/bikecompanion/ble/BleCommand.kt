package com.valpr.bikecompanion.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.CompletableDeferred

sealed interface BleCommand {
    val description: String
    val completion: CompletableDeferred<Int> // status code from GATT callback

    data class WriteCharacteristic(
        val characteristic: BluetoothGattCharacteristic,
        val data: ByteArray,
        val writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        override val description: String,
        override val completion: CompletableDeferred<Int> = CompletableDeferred()
    ) : BleCommand {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as WriteCharacteristic
            return characteristic == other.characteristic &&
                    data.contentEquals(other.data) &&
                    description == other.description
        }

        override fun hashCode(): Int {
            var result = characteristic.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + description.hashCode()
            return result
        }
    }

    data class WriteDescriptor(
        val descriptor: BluetoothGattDescriptor,
        val data: ByteArray,
        override val description: String,
        override val completion: CompletableDeferred<Int> = CompletableDeferred()
    ) : BleCommand {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as WriteDescriptor
            return descriptor == other.descriptor &&
                    data.contentEquals(other.data) &&
                    description == other.description
        }

        override fun hashCode(): Int {
            var result = descriptor.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + description.hashCode()
            return result
        }
    }
}

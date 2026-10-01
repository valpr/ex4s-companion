package com.valpr.bikecompanion.shared

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Protocol constants and message definitions for Wearable Data Layer
 * communication between the phone and Pixel Watch.
 */
object WearableProtocol {
    // Message paths
    const val PATH_HEART_RATE = "/telemetry/hr"
    const val PATH_ROTARY_BAILOUT = "/workout/bailout"
    const val PATH_RESUME_SLAP = "/workout/resume"
    const val PATH_WORKOUT_STATE = "/workout/state"
    const val PATH_HAPTIC_TRIGGER = "/workout/haptic"
    const val PATH_REQUEST_STATE = "/workout/request_state"

    // Capabilities
    const val CAPABILITY_PHONE_APP = "bike_companion_phone"
    const val CAPABILITY_WEAR_APP = "bike_companion_wear"
}

/**
 * Batched Heart Rate samples sent from Pixel Watch to Phone.
 * Batching at 2-3s (active) or 5-10s (ambient) avoids the 25-40%/hr battery drain of 1Hz ChannelClient.
 */
data class HeartRateBatch(val timestampMs: Long, val bpmSamples: List<Int>, val accuracy: Int = 3) {
    val latestBpm: Int
        get() = bpmSamples.lastOrNull() ?: 0

    val averageBpm: Int
        get() =
            if (bpmSamples.isNotEmpty()) {
                bpmSamples.average().toInt()
            } else {
                0
            }

    fun toByteArray(): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { dos ->
            dos.writeByte(MAGIC_BYTE.toInt())
            dos.writeLong(timestampMs)
            dos.writeByte(accuracy)
            dos.writeShort(bpmSamples.size)
            for (sample in bpmSamples) {
                dos.writeShort(sample)
            }
        }
        return baos.toByteArray()
    }

    companion object {
        private const val MAGIC_BYTE: Byte = 0x48 // 'H'

        fun fromByteArray(bytes: ByteArray): HeartRateBatch? {
            if (bytes.size < 12) return null
            return try {
                DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
                    val magic = dis.readByte()
                    if (magic != MAGIC_BYTE) return null
                    val timestampMs = dis.readLong()
                    val accuracy = dis.readByte().toInt()
                    val count = dis.readShort().toInt()
                    if (count < 0 || count > 500) return null
                    val samples = ArrayList<Int>(count)
                    for (i in 0 until count) {
                        samples.add(dis.readShort().toInt())
                    }
                    HeartRateBatch(
                        timestampMs = timestampMs,
                        bpmSamples = samples,
                        accuracy = accuracy
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * State synchronization packet sent from Phone to Watch.
 */
data class WorkoutStateMessage(
    val sessionStatus: Int, // 0 = IDLE, 1 = RUNNING, 2 = PAUSED, 3 = COMPLETED
    val elapsedSeconds: Int,
    val targetWatts: Int, // -1 if free ride / no target
    val currentWatts: Int,
    val cadenceRpm: Int,
    val heartRateBpm: Int,
    val isBailoutActive: Boolean,
    val isCadenceFloorActive: Boolean,
    val isHrCapped: Boolean,
    val workoutName: String,
    val athleteMaxHr: Int = 190
) {
    val isRunning: Boolean get() = sessionStatus == 1
    val isPaused: Boolean get() = sessionStatus == 2
    val isIdle: Boolean get() = sessionStatus == 0
    val isCompleted: Boolean get() = sessionStatus == 3

    val formattedElapsedTime: String
        get() {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    fun toByteArray(): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { dos ->
            dos.writeByte(MAGIC_BYTE.toInt())
            dos.writeByte(sessionStatus)
            dos.writeInt(elapsedSeconds)
            dos.writeShort(targetWatts)
            dos.writeShort(currentWatts)
            dos.writeShort(cadenceRpm)
            dos.writeShort(heartRateBpm)
            var flags = 0
            if (isBailoutActive) flags = flags or 0x01
            if (isCadenceFloorActive) flags = flags or 0x02
            if (isHrCapped) flags = flags or 0x04
            dos.writeByte(flags)
            dos.writeShort(athleteMaxHr)
            dos.writeUTF(workoutName)
        }
        return baos.toByteArray()
    }

    companion object {
        private const val MAGIC_BYTE: Byte = 0x57 // 'W'

        const val STATUS_IDLE = 0
        const val STATUS_RUNNING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_COMPLETED = 3

        fun fromByteArray(bytes: ByteArray): WorkoutStateMessage? {
            if (bytes.size < 14) return null
            return try {
                DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
                    val magic = dis.readByte()
                    if (magic != MAGIC_BYTE) return null
                    val sessionStatus = dis.readByte().toInt()
                    val elapsedSeconds = dis.readInt()
                    val targetWatts = dis.readShort().toInt()
                    val currentWatts = dis.readShort().toInt()
                    val cadenceRpm = dis.readShort().toInt()
                    val heartRateBpm = dis.readShort().toInt()
                    val flags = dis.readByte().toInt()
                    val isBailout = (flags and 0x01) != 0
                    val isCadenceFloor = (flags and 0x02) != 0
                    val isHrCapped = (flags and 0x04) != 0
                    // Appended maxHr field with legacy fallback: old phone packets have
                    // flags byte followed directly by UTF. Mark before probing so a
                    // misaligned read can reset and parse the legacy layout.
                    dis.mark(512)
                    var maxHr = 190
                    var workoutName = ""
                    try {
                        val candidate = dis.readShort().toInt()
                        val name = dis.readUTF()
                        if (candidate in 100..240) {
                            maxHr = candidate
                            workoutName = name
                        } else {
                            // Legacy packet: the "short" was the UTF length prefix. Reset.
                            dis.reset()
                            workoutName = dis.readUTF()
                        }
                    } catch (_: Exception) {
                        try {
                            dis.reset()
                            workoutName = dis.readUTF()
                        } catch (_: Exception) {
                            return null
                        }
                    }

                    WorkoutStateMessage(
                        sessionStatus = sessionStatus,
                        elapsedSeconds = elapsedSeconds,
                        targetWatts = targetWatts,
                        currentWatts = currentWatts,
                        cadenceRpm = cadenceRpm,
                        heartRateBpm = heartRateBpm,
                        isBailoutActive = isBailout,
                        isCadenceFloorActive = isCadenceFloor,
                        isHrCapped = isHrCapped,
                        workoutName = workoutName,
                        athleteMaxHr = maxHr
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Haptic alert commands sent from Phone to Watch.
 */
enum class HapticAlertType(val id: Byte) {
    CRITICAL_HR_WARNING(0x01),
    BAILOUT_TRIGGERED(0x02),
    RESUME_TRIGGERED(0x03);

    fun toByteArray(): ByteArray = byteArrayOf(id)

    companion object {
        fun fromByteArray(bytes: ByteArray): HapticAlertType? {
            if (bytes.isEmpty()) return null
            return when (bytes[0]) {
                CRITICAL_HR_WARNING.id -> CRITICAL_HR_WARNING
                BAILOUT_TRIGGERED.id -> BAILOUT_TRIGGERED
                RESUME_TRIGGERED.id -> RESUME_TRIGGERED
                else -> null
            }
        }
    }
}

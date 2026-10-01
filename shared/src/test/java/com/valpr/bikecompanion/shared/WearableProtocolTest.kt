package com.valpr.bikecompanion.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableProtocolTest {
    @Test
    fun testProtocolConstants() {
        assertEquals("/telemetry/hr", WearableProtocol.PATH_HEART_RATE)
        assertEquals("/workout/bailout", WearableProtocol.PATH_ROTARY_BAILOUT)
        assertEquals("/workout/resume", WearableProtocol.PATH_RESUME_SLAP)
        assertEquals("/workout/state", WearableProtocol.PATH_WORKOUT_STATE)
        assertEquals("/workout/haptic", WearableProtocol.PATH_HAPTIC_TRIGGER)
        assertEquals("/workout/request_state", WearableProtocol.PATH_REQUEST_STATE)
        assertEquals("bike_companion_phone", WearableProtocol.CAPABILITY_PHONE_APP)
        assertEquals("bike_companion_wear", WearableProtocol.CAPABILITY_WEAR_APP)
    }

    @Test
    fun testHeartRateBatchSerializationAndDeserialization() {
        val original =
            HeartRateBatch(
                timestampMs = 1711600000000L,
                bpmSamples = listOf(142, 143, 145, 146, 144),
                accuracy = 3
            )

        val bytes = original.toByteArray()
        val decoded = HeartRateBatch.fromByteArray(bytes)

        assertNotNull(decoded)
        assertEquals(original.timestampMs, decoded!!.timestampMs)
        assertEquals(original.bpmSamples, decoded.bpmSamples)
        assertEquals(3, decoded.accuracy)
        assertEquals(144, decoded.latestBpm)
        assertEquals(144, decoded.averageBpm)
    }

    @Test
    fun testHeartRateBatchEmptySamples() {
        val original =
            HeartRateBatch(
                timestampMs = 1711600000000L,
                bpmSamples = emptyList(),
                accuracy = 1
            )

        val bytes = original.toByteArray()
        val decoded = HeartRateBatch.fromByteArray(bytes)

        assertNotNull(decoded)
        assertEquals(0, decoded!!.bpmSamples.size)
        assertEquals(0, decoded.latestBpm)
        assertEquals(0, decoded.averageBpm)
    }

    @Test
    fun testHeartRateBatchCorruptPayload() {
        val corrupt = byteArrayOf(0x00, 0x01, 0x02)
        assertNull(HeartRateBatch.fromByteArray(corrupt))
    }

    @Test
    fun testWorkoutStateMessageSerializationAndDeserialization() {
        val original =
            WorkoutStateMessage(
                sessionStatus = WorkoutStateMessage.STATUS_RUNNING,
                elapsedSeconds = 125,
                targetWatts = 220,
                currentWatts = 218,
                cadenceRpm = 88,
                heartRateBpm = 152,
                isBailoutActive = false,
                isCadenceFloorActive = false,
                isHrCapped = false,
                workoutName = "Sweet Spot Intervals"
            )

        val bytes = original.toByteArray()
        val decoded = WorkoutStateMessage.fromByteArray(bytes)

        assertNotNull(decoded)
        assertEquals(WorkoutStateMessage.STATUS_RUNNING, decoded!!.sessionStatus)
        assertTrue(decoded.isRunning)
        assertFalse(decoded.isPaused)
        assertEquals(125, decoded.elapsedSeconds)
        assertEquals("02:05", decoded.formattedElapsedTime)
        assertEquals(220, decoded.targetWatts)
        assertEquals(218, decoded.currentWatts)
        assertEquals(88, decoded.cadenceRpm)
        assertEquals(152, decoded.heartRateBpm)
        assertFalse(decoded.isBailoutActive)
        assertFalse(decoded.isCadenceFloorActive)
        assertFalse(decoded.isHrCapped)
        assertEquals("Sweet Spot Intervals", decoded.workoutName)
    }

    @Test
    fun testWorkoutStateFlagsSerialization() {
        val original =
            WorkoutStateMessage(
                sessionStatus = WorkoutStateMessage.STATUS_RUNNING,
                elapsedSeconds = 300,
                targetWatts = 250,
                currentWatts = 120,
                cadenceRpm = 52,
                heartRateBpm = 178,
                isBailoutActive = true,
                isCadenceFloorActive = true,
                isHrCapped = true,
                workoutName = "Ramp Test"
            )

        val bytes = original.toByteArray()
        val decoded = WorkoutStateMessage.fromByteArray(bytes)

        assertNotNull(decoded)
        assertTrue(decoded!!.isBailoutActive)
        assertTrue(decoded.isCadenceFloorActive)
        assertTrue(decoded.isHrCapped)
    }

    @Test
    fun testWorkoutStateCorruptPayload() {
        val corrupt = byteArrayOf(0x57, 0x01, 0x02)
        assertNull(WorkoutStateMessage.fromByteArray(corrupt))
    }

    @Test
    fun testWorkoutStateMaxHrRoundTrip() {
        val original =
            WorkoutStateMessage(
                sessionStatus = WorkoutStateMessage.STATUS_RUNNING,
                elapsedSeconds = 60,
                targetWatts = 200,
                currentWatts = 195,
                cadenceRpm = 85,
                heartRateBpm = 150,
                isBailoutActive = false,
                isCadenceFloorActive = false,
                isHrCapped = false,
                workoutName = "Test",
                athleteMaxHr = 182
            )
        val decoded = WorkoutStateMessage.fromByteArray(original.toByteArray())
        assertNotNull(decoded)
        assertEquals(182, decoded!!.athleteMaxHr)
    }

    @Test
    fun testWorkoutStateLegacyPacketDefaultsMaxHr() {
        // Legacy phone packet without appended maxHr: flags byte then UTF directly.
        val baos = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(baos).use { dos ->
            dos.writeByte(0x57)
            dos.writeByte(WorkoutStateMessage.STATUS_RUNNING)
            dos.writeInt(60)
            dos.writeShort(200)
            dos.writeShort(195)
            dos.writeShort(85)
            dos.writeShort(150)
            dos.writeByte(0)
            dos.writeUTF("Legacy")
        }
        val decoded = WorkoutStateMessage.fromByteArray(baos.toByteArray())
        assertNotNull(decoded)
        assertEquals(190, decoded!!.athleteMaxHr)
        assertEquals(60, decoded.athleteRestingHr)
        assertEquals(false, decoded.useKarvonenZones)
        assertEquals("Legacy", decoded.workoutName)
    }

    @Test
    fun testWorkoutStateLegacyPacketWithLongWorkoutName() {
        // Legacy phone packet where workoutName length is in 100..240 (probed range in older heuristics)
        val longName = "A".repeat(120)
        val baos = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(baos).use { dos ->
            dos.writeByte(0x57)
            dos.writeByte(WorkoutStateMessage.STATUS_RUNNING)
            dos.writeInt(60)
            dos.writeShort(200)
            dos.writeShort(195)
            dos.writeShort(85)
            dos.writeShort(150)
            dos.writeByte(0)
            dos.writeUTF(longName)
        }
        val decoded = WorkoutStateMessage.fromByteArray(baos.toByteArray())
        assertNotNull(decoded)
        assertEquals(190, decoded!!.athleteMaxHr)
        assertEquals(60, decoded.athleteRestingHr)
        assertEquals(false, decoded.useKarvonenZones)
        assertEquals(longName, decoded.workoutName)
    }

    @Test
    fun testWorkoutStateIntermediatePacketDefaultsRestingHr() {
        // Intermediate v2 packet: flags byte then maxHr Short then UTF directly (no restingHr byte).
        val baos = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(baos).use { dos ->
            dos.writeByte(0x57)
            dos.writeByte(WorkoutStateMessage.STATUS_RUNNING)
            dos.writeInt(60)
            dos.writeShort(200)
            dos.writeShort(195)
            dos.writeShort(85)
            dos.writeShort(150)
            dos.writeByte(0)
            dos.writeShort(185)
            dos.writeUTF("Intermediate")
        }
        val decoded = WorkoutStateMessage.fromByteArray(baos.toByteArray())
        assertNotNull(decoded)
        assertEquals(185, decoded!!.athleteMaxHr)
        assertEquals(60, decoded.athleteRestingHr)
        assertEquals(false, decoded.useKarvonenZones)
        assertEquals("Intermediate", decoded.workoutName)
    }

    @Test
    fun testWorkoutStateKarvonenAndRestingHrRoundTrip() {
        val original = WorkoutStateMessage(
            sessionStatus = WorkoutStateMessage.STATUS_RUNNING,
            elapsedSeconds = 90,
            targetWatts = 220,
            currentWatts = 218,
            cadenceRpm = 90,
            heartRateBpm = 160,
            isBailoutActive = false,
            isCadenceFloorActive = false,
            isHrCapped = false,
            workoutName = "VO2 Intervals",
            athleteMaxHr = 188,
            athleteRestingHr = 52,
            useKarvonenZones = true
        )
        val decoded = WorkoutStateMessage.fromByteArray(original.toByteArray())
        assertNotNull(decoded)
        assertEquals(188, decoded!!.athleteMaxHr)
        assertEquals(52, decoded.athleteRestingHr)
        assertEquals(true, decoded.useKarvonenZones)
        assertEquals("VO2 Intervals", decoded.workoutName)
    }

    @Test
    fun testHapticAlertTypeSerialization() {
        for (alert in HapticAlertType.entries) {
            val bytes = alert.toByteArray()
            val decoded = HapticAlertType.fromByteArray(bytes)
            assertEquals(alert, decoded)
        }

        assertNull(HapticAlertType.fromByteArray(byteArrayOf()))
        assertNull(HapticAlertType.fromByteArray(byteArrayOf(0x7F)))
    }

    @Test
    fun testPingPongMessageSerialization() {
        val timestamp = 1711600123456L
        val original = PingPongMessage(timestamp)
        val bytes = original.toByteArray()
        val decoded = PingPongMessage.fromByteArray(bytes)

        assertNotNull(decoded)
        assertEquals(timestamp, decoded!!.timestampMs)

        assertNull(PingPongMessage.fromByteArray(byteArrayOf()))
        assertNull(PingPongMessage.fromByteArray(byteArrayOf(0x00, 0x01, 0x02)))
        assertNull(PingPongMessage.fromByteArray(byteArrayOf(0x51, 0, 0, 0, 0, 0, 0, 0, 1))) // wrong magic
    }
}

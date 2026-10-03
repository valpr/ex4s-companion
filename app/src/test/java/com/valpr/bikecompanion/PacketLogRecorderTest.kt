package com.valpr.bikecompanion

import com.valpr.bikecompanion.ble.LogFileInfo
import com.valpr.bikecompanion.ble.PacketLogLines
import com.valpr.bikecompanion.ble.PacketLogRecorder
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.data.PacketDirection
import com.valpr.bikecompanion.data.PacketLogEntry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PacketLogRecorderTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File.createTempFile("packetlog_test_", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // region Pure formatting

    @Test
    fun formatPacket_emitsSingleJsonLine() {
        val line = PacketLogLines.formatPacket(
            PacketLogEntry(
                timestampMs = 1_700_000_000_000L,
                direction = PacketDirection.RX,
                opcode = "0xD1 Cadence",
                rawBytes = byteArrayOf(0xF0.toByte(), 0xD1.toByte(), 0x00),
                description = "Cadence: 85 RPM"
            )
        )
        assertEquals(
            "{\"t\":1700000000000,\"dir\":\"RX\",\"op\":\"0xD1 Cadence\"," +
                "\"hex\":\"F0 D1 00\",\"desc\":\"Cadence: 85 RPM\"}",
            line.trimEnd()
        )
    }

    @Test
    fun formatPacket_escapesJsonSpecials() {
        val line = PacketLogLines.formatPacket(
            PacketLogEntry(
                timestampMs = 1L,
                direction = PacketDirection.TX,
                opcode = "Weird \"op\" \\ test",
                rawBytes = byteArrayOf(),
                description = "line1\nline2"
            )
        )
        assertTrue(line.contains("\"op\":\"Weird \\\"op\\\" \\\\ test\""))
        assertTrue(line.contains("\"desc\":\"line1\\nline2\""))
    }

    @Test
    fun fileNameFor_usesJsonlExtension() {
        assertEquals("packets_123.jsonl", PacketLogLines.fileNameFor(123L))
    }

    // endregion

    // region Pure retention

    @Test
    fun selectFilesToDelete_dropsOnlyAgedOut() {
        val files = listOf(
            LogFileInfo("packets_1.jsonl", 100, 1_000L),
            LogFileInfo("packets_2.jsonl", 100, 9_000L)
        )
        val victims = PacketLogLines.selectFilesToDelete(files, maxTotalBytes = 10_000L, cutoffMs = 5_000L)
        assertEquals(listOf(files[0]), victims)
    }

    @Test
    fun selectFilesToDelete_enforcesSizeBudgetOldestFirst() {
        val files = listOf(
            LogFileInfo("packets_1.jsonl", 400, 1_000L),
            LogFileInfo("packets_2.jsonl", 400, 2_000L),
            LogFileInfo("packets_3.jsonl", 400, 3_000L)
        )
        val victims = PacketLogLines.selectFilesToDelete(files, maxTotalBytes = 800L, cutoffMs = 0L)
        assertEquals(listOf(files[0]), victims)
    }

    @Test
    fun selectFilesToDelete_keepsEverythingUnderBudget() {
        val files = listOf(LogFileInfo("packets_1.jsonl", 100, 1_000L))
        assertTrue(
            PacketLogLines.selectFilesToDelete(files, maxTotalBytes = 10_000L, cutoffMs = 0L).isEmpty()
        )
    }

    // endregion

    // region Recorder integration (own TestScope per two-clock rule)

    @Test
    fun recordsPacketsAndStateTransitionsAsJsonLines() {
        val recorderScope = TestScope()
        try {
            val packets = MutableSharedFlow<PacketLogEntry>(extraBufferCapacity = 100)
            val states = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val io = StandardTestDispatcher(recorderScope.testScheduler)
            val recorder = PacketLogRecorder(
                logDirectory = tempDir,
                scope = recorderScope,
                packetSource = packets,
                stateSource = states,
                ioDispatcher = io,
                logger = {}
            )
            recorder.start()
            recorderScope.runCurrent()

            packets.tryEmit(
                PacketLogEntry(
                    timestampMs = 10L,
                    direction = PacketDirection.RX,
                    opcode = "0xD1 Cadence",
                    rawBytes = byteArrayOf(0xF0.toByte(), 0xD1.toByte()),
                    description = "Cadence: 85 RPM"
                )
            )
            states.value = BleConnectionState.Scanning
            recorderScope.runCurrent()
            recorder.stop()
            recorderScope.runCurrent()

            val latest = recorder.latestLogFile()
            assertNotNull(latest)
            val lines = latest!!.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            // Initial Disconnected state + packet + Scanning state.
            assertEquals(3, lines.size)
            assertTrue(lines[1].contains("\"dir\":\"RX\""))
            assertTrue(lines[1].contains("\"hex\":\"F0 D1\""))
            assertTrue(lines.any { it.contains("\"dir\":\"STATE\"") && it.contains("Scanning") })
        } finally {
            recorderScope.cancel()
        }
    }

    @Test
    fun startIsIdempotentAndStopWithoutStartIsSafe() {
        val recorderScope = TestScope()
        try {
            val packets = MutableSharedFlow<PacketLogEntry>(extraBufferCapacity = 100)
            val recorder = PacketLogRecorder(
                logDirectory = tempDir,
                scope = recorderScope,
                packetSource = packets,
                ioDispatcher = StandardTestDispatcher(recorderScope.testScheduler),
                logger = {}
            )
            recorder.stop() // no job yet: must not throw
            recorder.start()
            recorder.start() // duplicate: ignored
            recorderScope.runCurrent()
            recorder.stop()
            recorderScope.runCurrent()
            assertNull(recorder.latestLogFile()) // nothing recorded, no file created
        } finally {
            recorderScope.cancel()
        }
    }

    @Test
    fun rotatesWhenFileExceedsBudget() {
        val recorderScope = TestScope()
        try {
            val packets = MutableSharedFlow<PacketLogEntry>(extraBufferCapacity = 100)
            val recorder = PacketLogRecorder(
                logDirectory = tempDir,
                scope = recorderScope,
                packetSource = packets,
                ioDispatcher = StandardTestDispatcher(recorderScope.testScheduler),
                maxFileBytes = 100L,
                logger = {}
            )
            recorder.start()
            recorderScope.runCurrent()
            repeat(10) {
                packets.tryEmit(
                    PacketLogEntry(
                        timestampMs = it.toLong(),
                        direction = PacketDirection.TX,
                        opcode = "Poll",
                        rawBytes = byteArrayOf(0xF0.toByte()),
                        description = "Poll (counter=$it)"
                    )
                )
                recorderScope.runCurrent()
            }
            recorder.stop()
            recorderScope.runCurrent()
            val files = tempDir.listFiles { file ->
                file.isFile && file.name.startsWith("packets_")
            }!!
            assertTrue("expected rotation, found ${files.size}", files.size >= 2)
            // No truncation on same-millisecond name collisions: every line survives.
            assertEquals(
                10,
                files.sumOf { file -> file.readLines(Charsets.UTF_8).count { it.isNotBlank() } }
            )
        } finally {
            recorderScope.cancel()
        }
    }

    @Test
    fun prunesAgedOutFilesOnStart() {
        val stale = File(tempDir, "packets_1.jsonl").apply { writeText("{}\n") }
        stale.setLastModified(1_000L)
        val recorderScope = TestScope()
        try {
            val packets = MutableSharedFlow<PacketLogEntry>(extraBufferCapacity = 100)
            val recorder = PacketLogRecorder(
                logDirectory = tempDir,
                scope = recorderScope,
                packetSource = packets,
                ioDispatcher = StandardTestDispatcher(recorderScope.testScheduler),
                maxAgeDays = 0,
                logger = {}
            )
            recorder.start()
            // Pruning runs synchronously in start(): maxAgeDays = 0 makes the
            // 1970-dated file older than the cutoff, so it is deleted.
            assertFalse(stale.exists())
            recorder.stop()
            recorderScope.runCurrent()
        } finally {
            recorderScope.cancel()
        }
    }

    // endregion
}

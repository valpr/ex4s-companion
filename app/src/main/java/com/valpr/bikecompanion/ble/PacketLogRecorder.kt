package com.valpr.bikecompanion.ble

import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.data.PacketDirection
import com.valpr.bikecompanion.data.PacketLogEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File

/**
 * Persists the BLE packet stream (plus connection-state transitions) as
 * JSON-lines files under [logDirectory] for post-ride dropout forensics.
 *
 * Rotation: one `packets_<startMs>.jsonl` per run, rolled when it exceeds
 * [maxFileBytes]. Retention: [pruneOldLogs] on [start] drops files older than
 * [maxAgeDays] and oldest-first beyond [maxTotalBytes].
 *
 * Never throws: every I/O path is guarded so logging can never break a ride.
 * Writes run on [ioDispatcher]; [logger] is injectable for JVM tests
 * (no static Android logging on hot paths, AGENTS.md §8).
 */
class PacketLogRecorder(
    private val logDirectory: File,
    private val scope: CoroutineScope,
    private val packetSource: SharedFlow<PacketLogEntry>,
    private val stateSource: StateFlow<BleConnectionState>? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxFileBytes: Long = 512L * 1024,
    private val maxTotalBytes: Long = 4L * 1024 * 1024,
    private val maxAgeDays: Int = 7,
    private val logger: (String) -> Unit = {}
) {
    private var collectorJob: Job? = null
    private val writerLock = Any()
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var currentFileBytes: Long = 0
    private var linesSinceFlush: Int = 0

    /** Idempotent: duplicate starts are ignored (AGENTS.md §3). */
    fun start() {
        if (collectorJob?.isActive == true) return
        try {
            logDirectory.mkdirs()
            pruneOldLogs()
        } catch (_: Exception) {
            // Best-effort hygiene; recording still attempts below.
        }
        collectorJob = scope.launch(ioDispatcher) {
            try {
                val packets = launch { packetSource.collect { appendLine(PacketLogLines.formatPacket(it)) } }
                val states = stateSource?.let { states ->
                    launch { states.collect { appendLine(PacketLogLines.formatState(it)) } }
                }
                packets.join()
                states?.join()
            } finally {
                synchronized(writerLock) {
                    flushQuietly()
                    closeQuietly()
                }
            }
        }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
    }

    /** Newest log file, or null when nothing has been recorded yet. */
    fun latestLogFile(): File? = try {
        logDirectory.listFiles { file ->
            file.isFile && file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_EXTENSION)
        }?.maxByOrNull { it.lastModified() }
    } catch (_: Exception) {
        null
    }

    private fun appendLine(line: String) {
        synchronized(writerLock) {
            // Files are created lazily on first content so idle starts
            // leave no empty files behind.
            if (writer == null) {
                openWriter()
            }
            val out = writer ?: return
            try {
                out.write(line)
                out.newLine()
                // Track in memory: file.length() is stale behind the buffer.
                currentFileBytes += line.toByteArray(Charsets.UTF_8).size + 1
                linesSinceFlush++
                if (linesSinceFlush >= FLUSH_EVERY_LINES) {
                    flushQuietly()
                }
                if (currentFileBytes >= maxFileBytes) {
                    flushQuietly()
                    closeQuietly()
                }
            } catch (e: Exception) {
                logger("PacketLogRecorder append failed: ${e.message}")
            }
        }
    }

    private fun openWriter() {
        try {
            currentFile = uniqueLogFile(System.currentTimeMillis())
            writer = currentFile!!.bufferedWriter(Charsets.UTF_8, BUFFER_BYTES)
            currentFileBytes = 0
            linesSinceFlush = 0
        } catch (e: Exception) {
            logger("PacketLogRecorder open failed: ${e.message}")
            writer = null
            currentFile = null
        }
    }

    /** Base name from [PacketLogLines.fileNameFor], suffixed when colliding. */
    private fun uniqueLogFile(startMs: Long): File {
        var candidate = File(logDirectory, PacketLogLines.fileNameFor(startMs))
        var attempt = 1
        while (candidate.exists()) {
            attempt++
            candidate = File(logDirectory, "packets_${startMs}_$attempt.jsonl")
        }
        return candidate
    }

    private fun flushQuietly() {
        try {
            writer?.flush()
        } catch (_: Exception) {
            // Diagnostic path; never propagate.
        }
        linesSinceFlush = 0
    }

    private fun closeQuietly() {
        try {
            writer?.close()
        } catch (_: Exception) {
            // Diagnostic path; never propagate.
        }
        writer = null
        currentFile = null
        currentFileBytes = 0
    }

    private fun pruneOldLogs() {
        val files = logDirectory.listFiles { file ->
            file.isFile && file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_EXTENSION)
        } ?: return
        val infos = files.map { LogFileInfo(it.name, it.length(), it.lastModified()) }
        val cutoffMs = System.currentTimeMillis() - maxAgeDays * DAY_MS
        for (victim in PacketLogLines.selectFilesToDelete(infos, maxTotalBytes, cutoffMs)) {
            try {
                File(logDirectory, victim.name).delete()
            } catch (_: Exception) {
                // Best-effort hygiene.
            }
        }
    }

    companion object {
        const val LOG_PREFIX = "packets_"
        const val LOG_EXTENSION = ".jsonl"
        private const val FLUSH_EVERY_LINES = 25
        private const val BUFFER_BYTES = 8 * 1024
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** Minimal file metadata so retention decisions stay pure and JVM-testable. */
data class LogFileInfo(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

/**
 * Pure packet-log formatting and retention. No Android types, no I/O —
 * plain-JUnit per AGENTS.md §8.
 */
object PacketLogLines {

    fun fileNameFor(startMs: Long): String = "packets_$startMs.jsonl"

    /** One JSON object per line: `{"t":..,"dir":"RX","op":"..","hex":"..","desc":".."}`. */
    fun formatPacket(entry: PacketLogEntry): String {
        val dir = if (entry.direction == PacketDirection.RX) "RX" else "TX"
        return "{\"t\":${entry.timestampMs}," +
            "\"dir\":\"$dir\"," +
            "\"op\":\"${escape(entry.opcode)}\"," +
            "\"hex\":\"${entry.rawBytes.joinToString(" ") { "%02X".format(it) }}\"," +
            "\"desc\":\"${escape(entry.description)}\"}"
    }

    /** Connection-state transitions share the stream so gaps are interpretable. */
    fun formatState(state: BleConnectionState): String = "{\"t\":${System.currentTimeMillis()}," +
        "\"dir\":\"STATE\"," +
        "\"op\":\"${escape(state.javaClass.simpleName)}\"," +
        "\"hex\":\"\"," +
        "\"desc\":\"${escape(state.toString())}\"}"

    /**
     * Oldest-first deletion set: everything at/older than [cutoffMs] first,
     * then oldest remaining until the survivors fit [maxTotalBytes].
     */
    fun selectFilesToDelete(
        files: List<LogFileInfo>,
        maxTotalBytes: Long,
        cutoffMs: Long
    ): List<LogFileInfo> {
        val victims = files.filter { it.lastModifiedMs <= cutoffMs }.toMutableList()
        val survivors = files.filter { it.lastModifiedMs > cutoffMs }
            .sortedBy { it.lastModifiedMs }
            .toMutableList()
        var survivorBytes = survivors.sumOf { it.sizeBytes }
        while (survivors.isNotEmpty() && survivorBytes > maxTotalBytes) {
            val oldest = survivors.removeAt(0)
            survivorBytes -= oldest.sizeBytes
            victims.add(oldest)
        }
        return victims
    }

    internal fun escape(raw: String): String {
        val out = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch < ' ') out.append("\\u%04x".format(ch.code)) else out.append(ch)
            }
        }
        return out.toString()
    }
}

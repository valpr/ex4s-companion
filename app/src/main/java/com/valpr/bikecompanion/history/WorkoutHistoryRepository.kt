package com.valpr.bikecompanion.history

import android.content.Context
import com.valpr.bikecompanion.workout.WorkoutSummary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists completed [WorkoutSummary] records as JSON in `filesDir/history/`.
 *
 * Layout: one `ride_<startEpochMs>.json` full file per ride plus a header-only
 * `history_index.json` so list screens never parse multi-thousand-sample files.
 * Saves are idempotent on the ride id: re-saving the same session is a no-op.
 *
 * Threading: every public entry is guarded by a single lock, so concurrent
 * saves/deletes/lists cannot interleave the file+index read-modify-writes.
 * The index is a cache — full files are the source of truth, and [listHeaders]
 * heals the index by absorbing orphaned full files on every read.
 */
class WorkoutHistoryRepository(private val historyDirectory: File) {
    constructor(context: Context) : this(File(context.filesDir, HISTORY_DIR))

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    private val lock = Any()

    init {
        if (!historyDirectory.exists()) {
            historyDirectory.mkdirs()
        }
    }

    /**
     * Saves a completed summary. Returns true when a new record was written,
     * false when the record already existed (idempotent re-save).
     */
    fun save(summary: WorkoutSummary, sourceWorkoutFilename: String? = null): Boolean = synchronized(lock) {
        val ride = CompletedRide.fromSummary(summary, sourceWorkoutFilename)
        val target = File(historyDirectory, "${ride.id}.json")
        if (target.exists()) {
            return false
        }
        return try {
            // Write-then-rename so a crash never leaves a half-written visible file.
            val staging = File(historyDirectory, "${ride.id}.json.tmp")
            staging.writeText(json.encodeToString(CompletedRide.serializer(), ride), Charsets.UTF_8)
            if (!staging.renameTo(target)) {
                staging.delete()
                return false
            }
            upsertIndexEntry(ride.header())
            true
        } catch (_: Exception) {
            target.delete()
            false
        }
    }

    /** Newest-first headers for list screens. Never throws. Heals a stale index. */
    fun listHeaders(): List<RideHeader> = synchronized(lock) {
        val files = rideFiles()
        val existingIds = files.map { it.nameWithoutExtension }.toSet()
        val indexed = (readIndex() ?: emptyList()).filter { it.id in existingIds }
        // Absorb orphaned full files (crash between file write and index update).
        val knownIds = indexed.map { it.id }.toSet()
        val orphans = files
            .filter { it.nameWithoutExtension !in knownIds }
            .mapNotNull { decodeHeader(it) }
        val merged = if (orphans.isNotEmpty()) {
            (indexed + orphans).sortedByDescending { it.startTimeEpochMs }.also { writeIndex(it) }
        } else {
            indexed.sortedByDescending { it.startTimeEpochMs }
        }
        return merged
    }

    /**
     * Loads a full ride including samples, or null when missing/corrupt.
     * A corrupt file evicts its index entry so list and detail stop disagreeing.
     */
    fun loadRide(id: String): CompletedRide? = synchronized(lock) {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(historyDirectory, "$safeId.json")
        if (!file.exists()) {
            return null
        }
        return try {
            json.decodeFromString(CompletedRide.serializer(), file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            file.delete()
            removeIndexEntry(safeId)
            null
        }
    }

    /** Deletes a ride. Returns true only when a record was actually removed. */
    fun delete(id: String): Boolean = synchronized(lock) {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(historyDirectory, "$safeId.json")
        if (!file.exists()) {
            removeIndexEntry(safeId)
            return false
        }
        val removed = file.delete()
        if (removed) {
            removeIndexEntry(safeId)
        }
        return removed
    }

    /** Source .zwo filenames of completed structured rides (feeds BeginnerPath recommendNext). */
    fun completedFilenames(): Set<String> = listHeaders()
        .mapNotNull { it.sourceWorkoutFilename?.lowercase() }
        .toSet()

    private fun rideFiles(): List<File> = historyDirectory.listFiles { file ->
        file.isFile && file.name.startsWith("ride_") && file.name.endsWith(".json")
    }?.toList() ?: emptyList()

    private fun decodeHeader(file: File): RideHeader? = try {
        json.decodeFromString(CompletedRide.serializer(), file.readText(Charsets.UTF_8)).header()
    } catch (_: Exception) {
        null
    }

    private fun indexFile(): File = File(historyDirectory, INDEX_FILENAME)

    private fun readIndex(): List<RideHeader>? {
        return try {
            val file = indexFile()
            if (!file.exists()) {
                return null
            }
            json.decodeFromString(ListSerializer(RideHeader.serializer()), file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun writeIndex(headers: List<RideHeader>) {
        try {
            indexFile().writeText(
                json.encodeToString(ListSerializer(RideHeader.serializer()), headers),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            // Index is a cache; full files remain the source of truth.
        }
    }

    private fun upsertIndexEntry(header: RideHeader) {
        val current = (readIndex() ?: emptyList()).filter { it.id != header.id } + header
        writeIndex(current.sortedByDescending { it.startTimeEpochMs })
    }

    private fun removeIndexEntry(id: String) {
        val current = readIndex() ?: return
        writeIndex(current.filter { it.id != id })
    }

    companion object {
        const val HISTORY_DIR = "history"
        const val INDEX_FILENAME = "history_index.json"
    }
}

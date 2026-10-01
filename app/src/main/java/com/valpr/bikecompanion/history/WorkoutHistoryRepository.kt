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
 * Saves are idempotent on start epoch: re-saving the same session is a no-op.
 */
class WorkoutHistoryRepository(private val historyDirectory: File) {
    constructor(context: Context) : this(File(context.filesDir, HISTORY_DIR))

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    init {
        if (!historyDirectory.exists()) {
            historyDirectory.mkdirs()
        }
    }

    /**
     * Saves a completed summary. Returns true when a new record was written,
     * false when the record already existed (idempotent re-save).
     */
    fun save(summary: WorkoutSummary, sourceWorkoutFilename: String? = null): Boolean {
        val ride = CompletedRide.fromSummary(summary, sourceWorkoutFilename)
        val target = File(historyDirectory, "${ride.id}.json")
        if (target.exists()) {
            return false
        }
        return try {
            target.writeText(json.encodeToString(CompletedRide.serializer(), ride), Charsets.UTF_8)
            upsertIndexEntry(ride.header())
            true
        } catch (_: Exception) {
            target.delete()
            false
        }
    }

    /** Newest-first headers for list screens. Never throws. */
    fun listHeaders(): List<RideHeader> {
        val fromIndex = readIndex()
        if (fromIndex != null) {
            // Drop index entries whose full file vanished (external cleanup).
            val existingIds = rideFiles().map { it.nameWithoutExtension }.toSet()
            return fromIndex.filter { it.id in existingIds }.sortedByDescending { it.startTimeEpochMs }
        }
        // Fallback: rebuild from full files when the index is missing/corrupt.
        val rebuilt = rideFiles().mapNotNull { file ->
            try {
                val ride = json.decodeFromString(CompletedRide.serializer(), file.readText(Charsets.UTF_8))
                ride.header()
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.startTimeEpochMs }
        writeIndex(rebuilt)
        return rebuilt
    }

    /** Loads a full ride including samples, or null when missing/corrupt. */
    fun loadRide(id: String): CompletedRide? {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(historyDirectory, "$safeId.json")
        if (!file.exists()) {
            return null
        }
        return try {
            json.decodeFromString(CompletedRide.serializer(), file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun delete(id: String): Boolean {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val removed = File(historyDirectory, "$safeId.json").delete()
        removeIndexEntry(safeId)
        return removed
    }

    /** Source .zwo filenames of completed structured rides (feeds BeginnerPath recommendNext). */
    fun completedFilenames(): Set<String> = listHeaders()
        .mapNotNull { it.sourceWorkoutFilename?.lowercase() }
        .toSet()

    private fun rideFiles(): List<File> = historyDirectory.listFiles { file ->
        file.isFile && file.name.startsWith("ride_") && file.name.endsWith(".json")
    }?.toList() ?: emptyList()

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

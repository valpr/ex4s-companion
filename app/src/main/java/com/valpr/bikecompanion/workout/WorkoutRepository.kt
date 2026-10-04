package com.valpr.bikecompanion.workout

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Metadata summary of a locally cached .zwo workout file.
 */
data class CachedWorkoutHeader(
    val filename: String,
    val name: String,
    val author: String,
    val description: String,
    val durationSeconds: Int,
    val estimatedTss: Double,
    val fileSizeBytes: Long,
    val lastModifiedMs: Long,
    val tags: List<String> = emptyList()
)

/**
 * Repository managing workout files (.zwo) in internal app storage (`filesDir/workouts/`).
 */
class WorkoutRepository(private val workoutDirectory: File) {
    constructor(context: Context) : this(File(context.filesDir, "workouts"))

    init {
        if (!workoutDirectory.exists()) {
            workoutDirectory.mkdirs()
        }
    }

    private val cacheLock = Any()
    private var cachedHeaders: List<CachedWorkoutHeader>? = null
    private var cachedSnapshot: Map<String, Pair<Long, Long>>? = null

    /**
     * Lists all locally cached workouts sorted by last modified descending.
     *
     * Results are cached: a cheap directory listing (names + mtimes + sizes)
     * validates the cache, so repeated list calls during browsing never re-parse
     * every `.zwo` XML. Any on-disk change — via this repository or externally —
     * invalidates the snapshot and triggers a re-parse.
     */
    fun getCachedWorkouts(): List<CachedWorkoutHeader> = synchronized(cacheLock) {
        val files = workoutDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".zwo", ignoreCase = true)
        } ?: emptyArray()

        val snapshot = files.associate { it.name to (it.lastModified() to it.length()) }
        if (snapshot == cachedSnapshot && cachedHeaders != null) {
            return cachedHeaders!!.toList()
        }

        val parsed = files.mapNotNull { file ->
            try {
                FileInputStream(file).use { stream ->
                    val workout = ZwoParser.parse(stream)
                    CachedWorkoutHeader(
                        filename = file.name,
                        name = workout.name,
                        author = workout.author,
                        description = workout.description,
                        durationSeconds = workout.totalDurationSeconds,
                        estimatedTss = workout.estimatedTss,
                        fileSizeBytes = file.length(),
                        lastModifiedMs = file.lastModified(),
                        tags = workout.tags
                    )
                }
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.lastModifiedMs }
        cachedSnapshot = snapshot
        cachedHeaders = parsed
        return parsed
    }

    /**
     * Loads and parses a workout from storage by filename.
     */
    fun loadWorkout(filename: String): Result<Workout> {
        val file = File(workoutDirectory, sanitizeFilename(filename))
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("Workout file not found: $filename"))
        }

        return try {
            FileInputStream(file).use { stream ->
                ZwoParser.parseSafe(stream)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves a workout XML string to disk after validating its syntax.
     */
    fun saveWorkout(filename: String, xmlContent: String): Result<Workout> {
        val parsedResult = ZwoParser.parseSafe(xmlContent)
        val workout = parsedResult.getOrNull()
        if (parsedResult.isFailure || workout == null || workout.segments.isEmpty()) {
            return Result.failure(
                parsedResult.exceptionOrNull()
                    ?: IllegalArgumentException(
                        "Invalid workout: file must contain at least one segment in a <workout> block"
                    )
            )
        }

        val safeFilename = sanitizeFilename(filename)
        val targetFile = File(workoutDirectory, safeFilename)

        return try {
            // Atomic write-then-rename so a crash never leaves a half-written workout.
            val staging = File(workoutDirectory, "$safeFilename.tmp")
            staging.writeText(xmlContent, Charsets.UTF_8)
            if (!staging.renameTo(targetFile)) {
                try {
                    staging.copyTo(targetFile, overwrite = true)
                    staging.delete()
                } catch (e: Exception) {
                    staging.delete()
                    return Result.failure(e)
                }
            }
            invalidateCache()
            Result.success(workout)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports a workout from an [InputStream] (e.g. from SAF or download) after validating syntax.
     */
    fun saveWorkout(filename: String, inputStream: InputStream): Result<Workout> {
        val content = try {
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return saveWorkout(filename, content)
    }

    /**
     * Deletes a cached workout file. The filename is sanitized so `../`
     * traversal can never escape the workout directory.
     */
    fun deleteWorkout(filename: String): Boolean {
        val file = File(workoutDirectory, sanitizeFilename(filename))
        val removed = file.exists() && file.delete()
        if (removed) invalidateCache()
        return removed
    }

    /**
     * True when a workout file exists in storage.
     */
    fun workoutExists(filename: String): Boolean = File(workoutDirectory, sanitizeFilename(filename)).exists()

    /**
     * True when [filename] is a bundled sample the editor can reset to factory content.
     */
    fun isSampleFile(filename: String): Boolean = SAMPLE_FILES.containsKey(filename)

    /**
     * Restores a bundled sample to its factory content (editor "reset to
     * original"). Returns false when [filename] is not a bundled sample.
     */
    fun resetSampleWorkout(filename: String): Boolean {
        val xmlContent = SAMPLE_FILES[filename] ?: return false
        // Canonical sample keys are already filesystem-safe; sanitize anyway so
        // the staging/target pair can never escape the workout directory.
        val safeFilename = sanitizeFilename(filename)
        return try {
            val staging = File(workoutDirectory, "$safeFilename.tmp")
            staging.writeText(xmlContent, Charsets.UTF_8)
            val target = File(workoutDirectory, safeFilename)
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }
            invalidateCache()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pre-populates sample workouts if the library is empty, and backfills any
     * missing samples for existing users (e.g. the Beginner Path added later).
     * Idempotent: never overwrites user-modified files with the same name.
     */
    fun importSampleWorkoutsIfEmpty() {
        ensureSampleWorkouts()
    }

    /**
     * Ensures every bundled sample exists on disk. Separated for testability.
     */
    fun ensureSampleWorkouts() {
        for ((filename, xmlContent) in SAMPLE_FILES) {
            ensureSample(filename, xmlContent)
        }
    }

    private fun ensureSample(filename: String, xmlContent: String) {
        val target = File(workoutDirectory, filename)
        if (!target.exists()) {
            saveWorkout(filename, xmlContent)
        }
    }

    private fun invalidateCache() = synchronized(cacheLock) {
        cachedHeaders = null
        cachedSnapshot = null
    }

    internal fun sanitizeFilename(filename: String): String {
        val cleanName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (cleanName.endsWith(".zwo", ignoreCase = true)) {
            cleanName
        } else {
            "$cleanName.zwo"
        }
    }
    companion object {
        /**
         * All bundled samples by filename. Alias for [SampleWorkouts.FILES] kept
         * so seeding, sample detection, and editor reset can never drift apart.
         */
        val SAMPLE_FILES: Map<String, String> get() = SampleWorkouts.FILES
        val SAMPLE_SWEET_SPOT: String get() = SampleWorkouts.SAMPLE_SWEET_SPOT
        val SAMPLE_RAMP_TEST: String get() = SampleWorkouts.SAMPLE_RAMP_TEST
        val SAMPLE_BEGINNER_01: String get() = SampleWorkouts.SAMPLE_BEGINNER_01
        val SAMPLE_BEGINNER_02: String get() = SampleWorkouts.SAMPLE_BEGINNER_02
        val SAMPLE_BEGINNER_03: String get() = SampleWorkouts.SAMPLE_BEGINNER_03
        val SAMPLE_BEGINNER_04: String get() = SampleWorkouts.SAMPLE_BEGINNER_04
        val SAMPLE_ENDURANCE_30: String get() = SampleWorkouts.SAMPLE_ENDURANCE_30
        val SAMPLE_ENDURANCE_60: String get() = SampleWorkouts.SAMPLE_ENDURANCE_60
        val SAMPLE_CLIMB_30: String get() = SampleWorkouts.SAMPLE_CLIMB_30
        val SAMPLE_CLIMB_45: String get() = SampleWorkouts.SAMPLE_CLIMB_45
        val SAMPLE_HIIT_30: String get() = SampleWorkouts.SAMPLE_HIIT_30
        val SAMPLE_HIIT_45: String get() = SampleWorkouts.SAMPLE_HIIT_45
        val SAMPLE_TABATA_20: String get() = SampleWorkouts.SAMPLE_TABATA_20
        val SAMPLE_VO2_30: String get() = SampleWorkouts.SAMPLE_VO2_30
        val SAMPLE_VO2_45: String get() = SampleWorkouts.SAMPLE_VO2_45
        val SAMPLE_RECOVERY_20: String get() = SampleWorkouts.SAMPLE_RECOVERY_20
    }
}

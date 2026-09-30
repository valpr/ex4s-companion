package com.valpr.bikecompanion.workout

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    val lastModifiedMs: Long
)

/**
 * Repository managing workout files (.zwo) in internal app storage (`filesDir/workouts/`).
 */
class WorkoutRepository(
    private val workoutDirectory: File
) {
    constructor(context: Context) : this(File(context.filesDir, "workouts"))

    init {
        if (!workoutDirectory.exists()) {
            workoutDirectory.mkdirs()
        }
    }

    /**
     * Lists all locally cached workouts sorted by last modified descending.
     */
    fun getCachedWorkouts(): List<CachedWorkoutHeader> {
        val files = workoutDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".zwo", ignoreCase = true)
        } ?: emptyArray()

        return files.mapNotNull { file ->
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
                        lastModifiedMs = file.lastModified()
                    )
                }
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.lastModifiedMs }
    }

    /**
     * Loads and parses a workout from storage by filename.
     */
    fun loadWorkout(filename: String): Result<Workout> {
        val file = File(workoutDirectory, filename)
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
                    ?: IllegalArgumentException("Invalid workout: file must contain at least one segment in a <workout> block")
            )
        }

        val safeFilename = sanitizeFilename(filename)
        val targetFile = File(workoutDirectory, safeFilename)

        return try {
            targetFile.writeText(xmlContent, Charsets.UTF_8)
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
     * Deletes a cached workout file.
     */
    fun deleteWorkout(filename: String): Boolean {
        val file = File(workoutDirectory, filename)
        return file.exists() && file.delete()
    }

    /**
     * Pre-populates sample workouts if the library is empty.
     */
    fun importSampleWorkoutsIfEmpty() {
        val existingFiles = workoutDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".zwo", ignoreCase = true)
        }
        if (existingFiles.isNullOrEmpty()) {
            saveWorkout("sweet_spot_intervals.zwo", SAMPLE_SWEET_SPOT)
            saveWorkout("ftp_ramp_test.zwo", SAMPLE_RAMP_TEST)
        }
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
        val SAMPLE_SWEET_SPOT = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Sweet Spot Intervals (30 min)</name>
                <description>A 30-minute high-efficiency workout with two 8-minute intervals at 88% FTP.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="SweetSpot"/>
                    <tag name="Threshold"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.50" PowerHigh="0.75" Cadence="85">
                        <textevent timeoffset="10" message="Welcome! Spin easy at 85 RPM to warm up."/>
                        <textevent timeoffset="180" message="Gradually increasing power toward sweet spot."/>
                    </Warmup>
                    <IntervalsT Repeat="2" OnDuration="480" OffDuration="240" OnPower="0.88" OffPower="0.55" Cadence="90" CadenceResting="80">
                        <textevent timeoffset="0" message="Interval 1: Settle in at 88% FTP, 90 RPM cadence."/>
                    </IntervalsT>
                    <Cooldown Duration="300" PowerLow="0.65" PowerHigh="0.45" Cadence="80">
                        <textevent timeoffset="0" message="Great job! Spin out the legs for 5 minutes."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_RAMP_TEST = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>FTP Ramp Test</name>
                <description>Progressive 1-minute steps to exhaustion. 75% of your best 1-minute power is your new FTP.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Test"/>
                    <tag name="FTP"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.60" Cadence="85">
                        <textevent timeoffset="10" message="5-minute warmup. Pedal smoothly around 85-90 RPM."/>
                    </Warmup>
                    <SteadyState Duration="60" Power="0.65" Cadence="90"/>
                    <SteadyState Duration="60" Power="0.71" Cadence="90"/>
                    <SteadyState Duration="60" Power="0.77" Cadence="90"/>
                    <SteadyState Duration="60" Power="0.83" Cadence="90"/>
                    <SteadyState Duration="60" Power="0.89" Cadence="90"/>
                    <SteadyState Duration="60" Power="0.95" Cadence="90"/>
                    <SteadyState Duration="60" Power="1.01" Cadence="90"/>
                    <SteadyState Duration="60" Power="1.07" Cadence="90"/>
                    <SteadyState Duration="60" Power="1.13" Cadence="90"/>
                    <SteadyState Duration="60" Power="1.19" Cadence="90"/>
                    <SteadyState Duration="60" Power="1.25" Cadence="90"/>
                    <SteadyState Duration="60" Power="1.31" Cadence="90"/>
                    <Cooldown Duration="300" PowerLow="0.50" PowerHigh="0.30" Cadence="80"/>
                </workout>
            </workout_file>
        """.trimIndent()
    }
}

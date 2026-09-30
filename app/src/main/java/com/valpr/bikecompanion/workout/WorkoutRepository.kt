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
    val lastModifiedMs: Long
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
                    ?: IllegalArgumentException(
                        "Invalid workout: file must contain at least one segment in a <workout> block"
                    )
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
        ensureSample("sweet_spot_intervals.zwo", SAMPLE_SWEET_SPOT)
        ensureSample("ftp_ramp_test.zwo", SAMPLE_RAMP_TEST)
        ensureSample(BeginnerPlan.LEVELS[0].filename, SAMPLE_BEGINNER_01)
        ensureSample(BeginnerPlan.LEVELS[1].filename, SAMPLE_BEGINNER_02)
        ensureSample(BeginnerPlan.LEVELS[2].filename, SAMPLE_BEGINNER_03)
        ensureSample(BeginnerPlan.LEVELS[3].filename, SAMPLE_BEGINNER_04)
    }

    private fun ensureSample(filename: String, xmlContent: String) {
        val target = File(workoutDirectory, filename)
        if (!target.exists()) {
            saveWorkout(filename, xmlContent)
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

        // region Beginner Path (Levels 1-4): short, easy, heavily coached.
        val SAMPLE_BEGINNER_01 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>First Pedals (15 min) - Beginner 1/4</name>
                <description>Your very first ride. Easy spinning while ERG controls the bike, plus a 2-minute shifting practice. If you can talk in full sentences, you are at the right effort.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Beginner"/>
                    <tag name="Recovery"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.50" Cadence="75">
                        <textevent timeoffset="10" message="Welcome! Just spin easy at 70-75 RPM. The bike sets resistance for you."/>
                        <textevent timeoffset="150" message="Relax your shoulders, light grip, breathe through your nose."/>
                    </Warmup>
                    <SteadyState Duration="300" Power="0.50" Cadence="75">
                        <textevent timeoffset="10" message="Nice and steady at 50% FTP. You should be able to chat easily."/>
                    </SteadyState>
                    <FreeRide Duration="120" Cadence="70">
                        <textevent timeoffset="5" message="Your turn! Use the + / - buttons to feel resistance change, then settle easy."/>
                    </FreeRide>
                    <Cooldown Duration="180" PowerLow="0.50" PowerHigh="0.35" Cadence="70">
                        <textevent timeoffset="5" message="Cool down. If this felt good, repeat it once more before Level 2."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_BEGINNER_02 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Building Rhythm (20 min) - Beginner 2/4</name>
                <description>Two steady blocks at 55-60% FTP with an easy breather between. Practice holding one rhythm instead of surging.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Beginner"/>
                    <tag name="Endurance"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.55" Cadence="75">
                        <textevent timeoffset="10" message="5-minute warmup. Find a comfortable 75 RPM rhythm."/>
                    </Warmup>
                    <SteadyState Duration="360" Power="0.55" Cadence="78">
                        <textevent timeoffset="10" message="First steady block at 55%. Breathing a little deeper is fine."/>
                        <textevent timeoffset="240" message="Almost there. Keep your cadence smooth, not choppy."/>
                    </SteadyState>
                    <SteadyState Duration="180" Power="0.50" Cadence="75">
                        <textevent timeoffset="5" message="Easy breather. Shake out your hands, sip water if needed."/>
                    </SteadyState>
                    <SteadyState Duration="240" Power="0.60" Cadence="78">
                        <textevent timeoffset="5" message="Second block at 60%. Same rhythm, a touch more push."/>
                    </SteadyState>
                    <Cooldown Duration="120" PowerLow="0.50" PowerHigh="0.35" Cadence="70">
                        <textevent timeoffset="5" message="Well done! Do this one twice comfortably, then try Level 3."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_BEGINNER_03 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Steady Confidence (25 min) - Beginner 3/4</name>
                <description>Two 6-minute pushes at 65% FTP with full recoveries. Your first taste of repeatable efforts. Back off anytime with The Clutch.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Beginner"/>
                    <tag name="Endurance"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.55" Cadence="75">
                        <textevent timeoffset="10" message="Warm up well. Today has two gentle 6-minute pushes."/>
                    </Warmup>
                    <IntervalsT Repeat="2" OnDuration="360" OffDuration="180" OnPower="0.65" OffPower="0.50" Cadence="80" CadenceResting="75">
                        <textevent timeoffset="10" message="Push at 65%: brisk but sustainable. Remember The Clutch if you need a break."/>
                    </IntervalsT>
                    <Cooldown Duration="120" PowerLow="0.50" PowerHigh="0.35" Cadence="70">
                        <textevent timeoffset="5" message="Great control! Repeat until both pushes feel steady, then Level 4."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_BEGINNER_04 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Ready for More (30 min) - Beginner 4/4</name>
                <description>Graduation ride: three 5-minute efforts at 65-70% FTP. Finish this strong and you are ready for Sweet Spot Intervals.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Beginner"/>
                    <tag name="Tempo"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.55" Cadence="75">
                        <textevent timeoffset="10" message="Last beginner level! Warm up, then 3 x 5 minutes."/>
                    </Warmup>
                    <IntervalsT Repeat="3" OnDuration="300" OffDuration="120" OnPower="0.68" OffPower="0.50" Cadence="80" CadenceResting="75">
                        <textevent timeoffset="10" message="Push at 68%: strong breathing, but never gasping."/>
                    </IntervalsT>
                    <Cooldown Duration="240" PowerLow="0.50" PowerHigh="0.35" Cadence="70">
                        <textevent timeoffset="5" message="You did it! Recover well. Next stop: Sweet Spot Intervals."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()
        // endregion
    }
}

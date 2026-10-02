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
                        lastModifiedMs = file.lastModified(),
                        tags = workout.tags
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
     * True when a workout file exists in storage.
     */
    fun workoutExists(filename: String): Boolean = File(workoutDirectory, filename).exists()

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
        File(workoutDirectory, filename).writeText(xmlContent, Charsets.UTF_8)
        return true
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

        // region Class-Type Library: Express (<=30 min) + Classic (45-60 min) tiers.
        // Goal-based collections mirroring Peloton class types and Zwift collections
        // (Endurance, Climb, HIIT, Tabata, VO2Max, Recovery). Short variants suit
        // weekday sessions; Classic variants extend the work blocks, not the
        // warmup/cooldown. Hard types carry Clutch/HR-cap cues and no Beginner tag.
        val SAMPLE_ENDURANCE_30 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Fat Burn Endurance (30 min)</name>
                <description>Steady Zone 2 at 65% FTP. Conversational pace that builds aerobic base and maximizes fat metabolism. Keep cadence smooth at 85 RPM.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Endurance"/>
                    <tag name="FatBurn"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.55" Cadence="80">
                        <textevent timeoffset="10" message="Settle in at 80 RPM. You should breathe through your nose comfortably."/>
                    </Warmup>
                    <SteadyState Duration="1200" Power="0.65" Cadence="85">
                        <textevent timeoffset="10" message="Steady Zone 2 at 65%. If you cannot talk in full sentences, tap -5%."/>
                        <textevent timeoffset="600" message="Halfway. Relax your grip, drop your shoulders, keep it smooth."/>
                    </SteadyState>
                    <Cooldown Duration="300" PowerLow="0.55" PowerHigh="0.40" Cadence="80">
                        <textevent timeoffset="5" message="Cool down. Ride this 2-3 times per week to build your base."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_ENDURANCE_60 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Aerobic Base (60 min)</name>
                <description>A full hour of Zone 2 at 68% FTP. The classic base-builder: easy enough to repeat often, long enough to drive mitochondrial adaptation.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Endurance"/>
                    <tag name="FatBurn"/>
                </tags>
                <workout>
                    <Warmup Duration="600" PowerLow="0.40" PowerHigh="0.60" Cadence="80">
                        <textevent timeoffset="10" message="10-minute warmup. Find your comfortable 80 RPM rhythm."/>
                    </Warmup>
                    <SteadyState Duration="2400" Power="0.68" Cadence="85">
                        <textevent timeoffset="10" message="40 minutes of Zone 2 at 68%. Conversational is the rule, not the exception."/>
                        <textevent timeoffset="1200" message="Halfway. Shake out your hands, sip water, stay seated and smooth."/>
                    </SteadyState>
                    <Cooldown Duration="600" PowerLow="0.60" PowerHigh="0.40" Cadence="80">
                        <textevent timeoffset="5" message="Well done. Long slow rides like this are 80% of good training."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_CLIMB_30 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Climb Strength (30 min)</name>
                <description>Seated climbing strength: 4-minute pushes at 82% FTP and low 70 RPM cadence with easy spin recoveries. Heavy legs, steady breathing.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Climb"/>
                    <tag name="Strength"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.55" Cadence="80">
                        <textevent timeoffset="10" message="Warm up the legs. The climbs are seated at 70 RPM."/>
                    </Warmup>
                    <IntervalsT Repeat="3" OnDuration="240" OffDuration="120" OnPower="0.82" OffPower="0.50" Cadence="70" CadenceResting="80">
                        <textevent timeoffset="10" message="Climb! Heavy and steady at 70 RPM. Never grind below 60."/>
                    </IntervalsT>
                    <Cooldown Duration="300" PowerLow="0.55" PowerHigh="0.40" Cadence="75">
                        <textevent timeoffset="5" message="Strong climbing. Spin it out easy."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_CLIMB_45 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Climb Strength (45 min)</name>
                <description>Longer seated climbing: four 5-minute pushes at 85% FTP and 70 RPM. Builds repeatable hill strength for real-world gradients.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Climb"/>
                    <tag name="Strength"/>
                </tags>
                <workout>
                    <Warmup Duration="420" PowerLow="0.40" PowerHigh="0.60" Cadence="80">
                        <textevent timeoffset="10" message="7-minute warmup. Four climbs ahead, all seated at 70 RPM."/>
                    </Warmup>
                    <IntervalsT Repeat="4" OnDuration="300" OffDuration="180" OnPower="0.85" OffPower="0.50" Cadence="70" CadenceResting="80">
                        <textevent timeoffset="10" message="Climb at 85%, 70 RPM. Power through your heels, breathe deeply."/>
                    </IntervalsT>
                    <Cooldown Duration="360" PowerLow="0.55" PowerHigh="0.40" Cadence="75">
                        <textevent timeoffset="5" message="Summit reached. Easy spinning to recover."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_HIIT_30 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>HIIT Intervals (30 min)</name>
                <description>Ten rounds of 40 seconds at 115% FTP with 80-second recoveries. Short, sharp, and over in half an hour. The Clutch is there if you need it.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="HIIT"/>
                    <tag name="Intervals"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.60" Cadence="85">
                        <textevent timeoffset="10" message="Warm up well. Ten 40-second efforts ahead at 95 RPM."/>
                    </Warmup>
                    <IntervalsT Repeat="10" OnDuration="40" OffDuration="80" OnPower="1.15" OffPower="0.50" Cadence="95" CadenceResting="80">
                        <textevent timeoffset="5" message="Go hard! 40 seconds on, 80 easy. Count down each rep."/>
                    </IntervalsT>
                    <Cooldown Duration="300" PowerLow="0.55" PowerHigh="0.35" Cadence="80">
                        <textevent timeoffset="5" message="Drenched? That is the point. Spin easy and breathe."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_HIIT_45 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>HIIT and Hills (45 min)</name>
                <description>Alternating sprint blocks (40s at 112% FTP) and seated hills (5 min at 85% FTP, 72 RPM). The Peloton-style mixed session.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="HIIT"/>
                    <tag name="Climb"/>
                </tags>
                <workout>
                    <Warmup Duration="300" PowerLow="0.40" PowerHigh="0.60" Cadence="85">
                        <textevent timeoffset="10" message="Warm up. Three sprint blocks separated by seated hills."/>
                    </Warmup>
                    <IntervalsT Repeat="5" OnDuration="40" OffDuration="60" OnPower="1.12" OffPower="0.55" Cadence="95" CadenceResting="80">
                        <textevent timeoffset="5" message="Sprint block: 40 seconds on, 60 easy."/>
                    </IntervalsT>
                    <SteadyState Duration="300" Power="0.85" Cadence="72">
                        <textevent timeoffset="5" message="Hill 1: seated at 72 RPM, strong and steady."/>
                    </SteadyState>
                    <IntervalsT Repeat="5" OnDuration="40" OffDuration="60" OnPower="1.12" OffPower="0.55" Cadence="95" CadenceResting="80">
                        <textevent timeoffset="5" message="Sprint block 2. Same recipe, fresh legs are optional."/>
                    </IntervalsT>
                    <SteadyState Duration="300" Power="0.85" Cadence="72">
                        <textevent timeoffset="5" message="Hill 2: hold form, 72 RPM, breathe through the burn."/>
                    </SteadyState>
                    <IntervalsT Repeat="5" OnDuration="40" OffDuration="60" OnPower="1.12" OffPower="0.55" Cadence="95" CadenceResting="80">
                        <textevent timeoffset="5" message="Final sprint block. Empty the tank, then recover."/>
                    </IntervalsT>
                    <Cooldown Duration="300" PowerLow="0.55" PowerHigh="0.35" Cadence="80">
                        <textevent timeoffset="5" message="Done. That mixed session builds both engines."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_TABATA_20 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Tabata Intervals (20 min)</name>
                <description>Classic Tabata: two sets of 8 x 20 seconds all-out at 125% FTP with 10-second rests. Maximum intensity, minimum time.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Tabata"/>
                    <tag name="HIIT"/>
                </tags>
                <workout>
                    <Warmup Duration="360" PowerLow="0.40" PowerHigh="0.65" Cadence="85">
                        <textevent timeoffset="10" message="Warm up thoroughly. Tabata demands a hot engine."/>
                    </Warmup>
                    <IntervalsT Repeat="8" OnDuration="20" OffDuration="10" OnPower="1.25" OffPower="0.50" Cadence="100" CadenceResting="80">
                        <textevent timeoffset="5" message="20 seconds ALL OUT, 10 rest. 8 rounds. Go!"/>
                    </IntervalsT>
                    <SteadyState Duration="180" Power="0.55" Cadence="80">
                        <textevent timeoffset="5" message="Breathe. One more set coming."/>
                    </SteadyState>
                    <IntervalsT Repeat="8" OnDuration="20" OffDuration="10" OnPower="1.25" OffPower="0.50" Cadence="100" CadenceResting="80">
                        <textevent timeoffset="5" message="Second set. Same deal: 20 on, 10 off."/>
                    </IntervalsT>
                    <Cooldown Duration="240" PowerLow="0.55" PowerHigh="0.35" Cadence="80">
                        <textevent timeoffset="5" message="Survived. Nothing else today — Tabata is enough."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_VO2_30 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>VO2 Max 3x3 (30 min)</name>
                <description>Three 3-minute efforts at 110% FTP with equal recoveries at 95 RPM. Raises your aerobic ceiling. Hard but repeatable weekly.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="VO2Max"/>
                    <tag name="Threshold"/>
                </tags>
                <workout>
                    <Warmup Duration="420" PowerLow="0.40" PowerHigh="0.65" Cadence="85">
                        <textevent timeoffset="10" message="7-minute warmup. Three 3-minute VO2 efforts ahead."/>
                    </Warmup>
                    <IntervalsT Repeat="3" OnDuration="180" OffDuration="180" OnPower="1.10" OffPower="0.55" Cadence="95" CadenceResting="85">
                        <textevent timeoffset="10" message="3 minutes at 110%, 95 RPM. Fast legs keep the trainer from bogging you down."/>
                    </IntervalsT>
                    <Cooldown Duration="300" PowerLow="0.60" PowerHigh="0.35" Cadence="80">
                        <textevent timeoffset="5" message="Ceiling raised. Cool down well; do this at most twice a week."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_VO2_45 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>VO2 Max 40-20s (45 min)</name>
                <description>Microburst VO2: three blocks of 5 x 40 seconds at 109% FTP with 20-second floats. More time at max oxygen uptake than long intervals.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="VO2Max"/>
                    <tag name="Intervals"/>
                </tags>
                <workout>
                    <Warmup Duration="480" PowerLow="0.40" PowerHigh="0.70" Cadence="90">
                        <textevent timeoffset="10" message="8-minute warmup with fast pedaling to wake up the legs."/>
                    </Warmup>
                    <SteadyState Duration="300" Power="0.90" Cadence="90">
                        <textevent timeoffset="5" message="Primer: 5 minutes at 90%. Then the microbursts begin."/>
                    </SteadyState>
                    <IntervalsT Repeat="5" OnDuration="40" OffDuration="20" OnPower="1.09" OffPower="0.55" Cadence="95" CadenceResting="80">
                        <textevent timeoffset="5" message="40 seconds on, 20 easy. Short rests keep oxygen uptake pinned."/>
                    </IntervalsT>
                    <SteadyState Duration="300" Power="0.50" Cadence="85">
                        <textevent timeoffset="5" message="Recover well. Two more blocks to go."/>
                    </SteadyState>
                    <IntervalsT Repeat="5" OnDuration="40" OffDuration="20" OnPower="1.09" OffPower="0.55" Cadence="95" CadenceResting="80">
                        <textevent timeoffset="5" message="Block 2. Watch your heart rate climb and recover."/>
                    </IntervalsT>
                    <SteadyState Duration="300" Power="0.50" Cadence="85">
                        <textevent timeoffset="5" message="Last recovery. Then finish strong."/>
                    </SteadyState>
                    <IntervalsT Repeat="5" OnDuration="40" OffDuration="20" OnPower="1.09" OffPower="0.55" Cadence="95" CadenceResting="80">
                        <textevent timeoffset="5" message="Final block. Hold 95 RPM to the line."/>
                    </IntervalsT>
                    <Cooldown Duration="420" PowerLow="0.55" PowerHigh="0.35" Cadence="80">
                        <textevent timeoffset="5" message="Excellent. Microbursts deliver big VO2 gains per minute."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()

        val SAMPLE_RECOVERY_20 = """
            <workout_file>
                <author>Echelon Companion</author>
                <name>Low Impact Recovery (20 min)</name>
                <description>All-seated active recovery at 48% FTP and 80 RPM. For rest days or returning after time off. Light on the joints, easy on the mind.</description>
                <sportType>bike</sportType>
                <tags>
                    <tag name="Recovery"/>
                    <tag name="LowImpact"/>
                </tags>
                <workout>
                    <Warmup Duration="240" PowerLow="0.35" PowerHigh="0.45" Cadence="78">
                        <textevent timeoffset="10" message="Easy spin. Stay seated for the whole ride."/>
                    </Warmup>
                    <SteadyState Duration="720" Power="0.48" Cadence="80">
                        <textevent timeoffset="10" message="Recovery pace. This should feel almost too easy — that is correct."/>
                    </SteadyState>
                    <Cooldown Duration="240" PowerLow="0.48" PowerHigh="0.35" Cadence="75">
                        <textevent timeoffset="5" message="Fresh legs tomorrow. Well rested."/>
                    </Cooldown>
                </workout>
            </workout_file>
        """.trimIndent()
        // endregion

        /**
         * All bundled samples by filename. Single list driving [ensureSampleWorkouts],
         * [isSampleFile], and [resetSampleWorkout] so seeding and editor reset
         * can never drift apart.
         */
        val SAMPLE_FILES: Map<String, String> = linkedMapOf(
            "sweet_spot_intervals.zwo" to SAMPLE_SWEET_SPOT,
            "ftp_ramp_test.zwo" to SAMPLE_RAMP_TEST,
            BeginnerPlan.LEVELS[0].filename to SAMPLE_BEGINNER_01,
            BeginnerPlan.LEVELS[1].filename to SAMPLE_BEGINNER_02,
            BeginnerPlan.LEVELS[2].filename to SAMPLE_BEGINNER_03,
            BeginnerPlan.LEVELS[3].filename to SAMPLE_BEGINNER_04,
            "endurance_30_fat_burn.zwo" to SAMPLE_ENDURANCE_30,
            "endurance_60_aerobic_base.zwo" to SAMPLE_ENDURANCE_60,
            "climb_30_strength.zwo" to SAMPLE_CLIMB_30,
            "climb_45_strength.zwo" to SAMPLE_CLIMB_45,
            "hiit_30_intervals.zwo" to SAMPLE_HIIT_30,
            "hiit_45_hills.zwo" to SAMPLE_HIIT_45,
            "tabata_20_intervals.zwo" to SAMPLE_TABATA_20,
            "vo2_30_3x3.zwo" to SAMPLE_VO2_30,
            "vo2_45_40_20s.zwo" to SAMPLE_VO2_45,
            "recovery_20_low_impact.zwo" to SAMPLE_RECOVERY_20
        )
    }
}

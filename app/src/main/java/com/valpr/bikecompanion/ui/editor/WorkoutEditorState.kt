package com.valpr.bikecompanion.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutRepository
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.WorkoutTags
import com.valpr.bikecompanion.workout.WorkoutTextEvent
import com.valpr.bikecompanion.workout.WorkoutValidator
import com.valpr.bikecompanion.workout.ZwoParser
import com.valpr.bikecompanion.workout.ZwoWriter
import kotlin.math.roundToInt

/** Segment types the editor can create, mirroring [WorkoutSegment]. */
enum class EditableSegmentType(val label: String) {
    STEADY_STATE("Steady"),
    WARMUP("Warmup"),
    COOLDOWN("Cooldown"),
    RAMP("Ramp"),
    FREE_RIDE("Free Ride"),
    MAX_EFFORT("Max Effort"),

    /** Repeating on/off block; expands to on/off pairs like `IntervalsT`. */
    INTERVALS("Intervals")
}

/**
 * One editable row. Powers are whole %FTP (65 = 0.65); cadence null means
 * unspecified. Cues are segment-relative and preserved across saves.
 */
data class EditableSegment(
    val id: Long,
    val type: EditableSegmentType,
    val durationSeconds: Int = 300,
    val powerPct: Int = 65,
    val powerLowPct: Int = 50,
    val powerHighPct: Int = 70,
    val cadence: Int? = 85,
    val cues: List<WorkoutTextEvent> = emptyList(),
    // Intervals-only fields.
    val repeatCount: Int = 5,
    val onDurationSeconds: Int = 40,
    val onPowerPct: Int = 112,
    val offDurationSeconds: Int = 60,
    val offPowerPct: Int = 55,
    val restingCadence: Int? = 80,
    val restCues: List<WorkoutTextEvent> = emptyList()
) {
    fun toWorkoutSegment(): WorkoutSegment {
        val coercedDuration = durationSeconds.coerceAtLeast(1)
        return when (type) {
            EditableSegmentType.STEADY_STATE ->
                WorkoutSegment.SteadyState(coercedDuration, powerPct.toFraction(), cadence)
            EditableSegmentType.WARMUP ->
                WorkoutSegment.Warmup(coercedDuration, powerLowPct.toFraction(), powerHighPct.toFraction(), cadence)
            EditableSegmentType.COOLDOWN ->
                WorkoutSegment.Cooldown(coercedDuration, powerLowPct.toFraction(), powerHighPct.toFraction(), cadence)
            EditableSegmentType.RAMP ->
                WorkoutSegment.Ramp(coercedDuration, powerLowPct.toFraction(), powerHighPct.toFraction(), cadence)
            EditableSegmentType.FREE_RIDE ->
                WorkoutSegment.FreeRide(coercedDuration, flatRoad = false, targetCadence = cadence)
            EditableSegmentType.MAX_EFFORT ->
                WorkoutSegment.MaxEffort(coercedDuration, targetCadence = cadence)
            EditableSegmentType.INTERVALS ->
                // Single-segment view of an interval row: the work interval.
                // Full expansion lives in toWorkoutSegments().
                WorkoutSegment.SteadyState(
                    onDurationSeconds.coerceAtLeast(1),
                    onPowerPct.toFraction(),
                    cadence
                )
        }
    }

    /**
     * Expands this row to workout segments: a single segment, or the on/off
     * pairs for [EditableSegmentType.INTERVALS] (mirroring flattened
     * `IntervalsT`).
     */
    fun toWorkoutSegments(): List<WorkoutSegment> {
        if (type != EditableSegmentType.INTERVALS) return listOf(toWorkoutSegment())
        val repeat = repeatCount.coerceAtLeast(1)
        val on = WorkoutSegment.SteadyState(
            onDurationSeconds.coerceAtLeast(1),
            onPowerPct.toFraction(),
            cadence
        )
        val off = WorkoutSegment.SteadyState(
            offDurationSeconds.coerceAtLeast(1),
            offPowerPct.toFraction(),
            restingCadence
        )
        return buildList {
            repeat(repeat) {
                add(on)
                add(off)
            }
        }
    }

    companion object {
        fun fromWorkoutSegment(segment: WorkoutSegment, id: Long, cues: List<WorkoutTextEvent>): EditableSegment {
            val base = EditableSegment(id = id, type = EditableSegmentType.STEADY_STATE, cues = cues)
            return when (segment) {
                is WorkoutSegment.SteadyState -> base.copy(
                    type = EditableSegmentType.STEADY_STATE,
                    durationSeconds = segment.durationSeconds,
                    powerPct = segment.power.toPct(),
                    cadence = segment.targetCadence
                )
                is WorkoutSegment.Warmup -> base.copy(
                    type = EditableSegmentType.WARMUP,
                    durationSeconds = segment.durationSeconds,
                    powerLowPct = segment.powerLow.toPct(),
                    powerHighPct = segment.powerHigh.toPct(),
                    cadence = segment.targetCadence
                )
                is WorkoutSegment.Cooldown -> base.copy(
                    type = EditableSegmentType.COOLDOWN,
                    durationSeconds = segment.durationSeconds,
                    powerLowPct = segment.powerLow.toPct(),
                    powerHighPct = segment.powerHigh.toPct(),
                    cadence = segment.targetCadence
                )
                is WorkoutSegment.Ramp -> base.copy(
                    type = EditableSegmentType.RAMP,
                    durationSeconds = segment.durationSeconds,
                    powerLowPct = segment.powerLow.toPct(),
                    powerHighPct = segment.powerHigh.toPct(),
                    cadence = segment.targetCadence
                )
                is WorkoutSegment.FreeRide -> base.copy(
                    type = EditableSegmentType.FREE_RIDE,
                    durationSeconds = segment.durationSeconds,
                    cadence = segment.targetCadence
                )
                is WorkoutSegment.MaxEffort -> base.copy(
                    type = EditableSegmentType.MAX_EFFORT,
                    durationSeconds = segment.durationSeconds,
                    cadence = segment.targetCadence
                )
            }
        }

        fun defaultForType(type: EditableSegmentType, id: Long): EditableSegment = when (type) {
            EditableSegmentType.STEADY_STATE -> EditableSegment(id, type, 300, powerPct = 65, cadence = 85)
            EditableSegmentType.WARMUP -> EditableSegment(id, type, 300, powerLowPct = 40, powerHighPct = 60, cadence = 85)
            EditableSegmentType.COOLDOWN -> EditableSegment(id, type, 300, powerLowPct = 60, powerHighPct = 40, cadence = 80)
            EditableSegmentType.RAMP -> EditableSegment(id, type, 300, powerLowPct = 70, powerHighPct = 90, cadence = 85)
            EditableSegmentType.FREE_RIDE -> EditableSegment(id, type, 300, cadence = 80)
            EditableSegmentType.MAX_EFFORT -> EditableSegment(id, type, 30, cadence = 100)
            EditableSegmentType.INTERVALS -> EditableSegment(
                id,
                type,
                repeatCount = 5,
                onDurationSeconds = 40,
                onPowerPct = 112,
                offDurationSeconds = 60,
                offPowerPct = 55,
                cadence = 95,
                restingCadence = 80
            )
        }

        /**
         * Collapses consecutive identical on/off [WorkoutSegment.SteadyState]
         * pairs back into single [EditableSegmentType.INTERVALS] rows so
         * parsed `IntervalsT` blocks stay editable as one card. Runs of fewer
         * than 2 repeats are left as single rows. Only repeats whose cue
         * lists are all identical collapse: imported files with distinct
         * per-repeat messages stay as single rows so no coaching cue is
         * silently dropped (only the longest cue-consistent prefix collapses).
         */
        internal fun collapseIntervalRows(
            segments: List<WorkoutSegment>,
            cuesPerSegment: List<List<WorkoutTextEvent>>,
            nextId: () -> Long
        ): List<EditableSegment> {
            val rows = mutableListOf<EditableSegment>()
            var i = 0
            while (i < segments.size) {
                val on = segments.getOrNull(i) as? WorkoutSegment.SteadyState
                val off = segments.getOrNull(i + 1) as? WorkoutSegment.SteadyState
                var repeat = 0
                if (on != null && off != null) {
                    while (i + repeat * 2 + 1 < segments.size &&
                        segments[i + repeat * 2] == on &&
                        segments[i + repeat * 2 + 1] == off
                    ) {
                        repeat++
                    }
                }
                if (repeat >= 2 && on != null && off != null) {
                    // Shrink to the longest prefix whose cues all match the
                    // first repeat; distinct per-repeat messages must not
                    // collapse (their cues would be lost).
                    val firstOnCues = cuesPerSegment.getOrElse(i) { emptyList() }
                    val firstOffCues = cuesPerSegment.getOrElse(i + 1) { emptyList() }
                    var consistent = 1
                    while (consistent < repeat &&
                        cuesPerSegment.getOrElse(i + consistent * 2) { emptyList() } == firstOnCues &&
                        cuesPerSegment.getOrElse(i + consistent * 2 + 1) { emptyList() } == firstOffCues
                    ) {
                        consistent++
                    }
                    if (consistent >= 2) {
                        rows.add(
                            EditableSegment(
                                id = nextId(),
                                type = EditableSegmentType.INTERVALS,
                                cadence = on.targetCadence,
                                cues = firstOnCues,
                                repeatCount = consistent,
                                onDurationSeconds = on.durationSeconds,
                                onPowerPct = on.power.toPct(),
                                offDurationSeconds = off.durationSeconds,
                                offPowerPct = off.power.toPct(),
                                restingCadence = off.targetCadence,
                                restCues = firstOffCues
                            )
                        )
                        i += consistent * 2
                        continue
                    }
                }
                rows.add(
                    fromWorkoutSegment(
                        segments[i],
                        nextId(),
                        cuesPerSegment.getOrElse(i) { emptyList() }
                    )
                )
                i++
            }
            return rows
        }
    }
}

private fun Int.toFraction(): Float = this / 100f

private fun Float.toPct(): Int = (this * 100).roundToInt()

/**
 * Form-editor draft holder. Compose-state backed so the screen recomposes on
 * edit; plain-class so logic stays unit-testable without Robolectric.
 */
class WorkoutEditorState(val repository: WorkoutRepository) {

    var originalFilename: String? = null
        private set

    var name by mutableStateOf("")
        private set
    var author by mutableStateOf("Echelon Companion")
        private set
    var description by mutableStateOf("")
        private set
    var tagsText by mutableStateOf("")
        private set

    val segments = mutableStateListOf<EditableSegment>()

    var isDirty by mutableStateOf(false)
        private set

    /** Set when [save] needs explicit overwrite confirmation. */
    var pendingOverwriteFilename: String? by mutableStateOf(null)
        private set

    /** Whether the pending overwrite confirmation is for a Save-as-copy. */
    var pendingOverwriteAsCopy: Boolean by mutableStateOf(false)
        private set

    private var nextId = 0L

    val isSeededFile: Boolean
        get() = originalFilename?.let { repository.isSampleFile(it) } == true

    fun loadNew() {
        originalFilename = null
        name = ""
        author = "Echelon Companion"
        description = ""
        tagsText = ""
        segments.clear()
        segments.add(EditableSegment.defaultForType(EditableSegmentType.STEADY_STATE, nextId++))
        pendingOverwriteFilename = null
        isDirty = false
    }

    /** Returns false when the file cannot be loaded. */
    fun load(filename: String): Boolean {
        val workout = repository.loadWorkout(filename).getOrNull() ?: return false
        val cues = ZwoWriter.distributeCues(workout)
        originalFilename = filename
        name = workout.name
        author = workout.author
        description = workout.description
        tagsText = workout.tags.joinToString(", ")
        segments.clear()
        segments.addAll(
            EditableSegment.collapseIntervalRows(workout.segments, cues) { nextId++ }
        )
        pendingOverwriteFilename = null
        isDirty = false
        return true
    }

    /**
     * Built workout plus the draft-row index owning each expanded segment, so
     * validation issues on expanded interval pairs highlight the right card.
     * [trimmedCueCount] reports coaching cues moved by duration clamping.
     */
    data class BuiltWorkout(
        val workout: Workout,
        val rowForSegment: List<Int>,
        val trimmedCueCount: Int
    )

    fun buildDetailed(): BuiltWorkout {
        var elapsed = 0
        var trimmedCues = 0
        val absoluteCues = mutableListOf<WorkoutTextEvent>()
        val builtSegments = mutableListOf<WorkoutSegment>()
        val rowForSegment = mutableListOf<Int>()
        segments.forEachIndexed { rowIndex, editable ->
            val expanded = editable.toWorkoutSegments()
            if (editable.type == EditableSegmentType.INTERVALS) {
                val onDur = editable.onDurationSeconds.coerceAtLeast(1)
                val offDur = editable.offDurationSeconds.coerceAtLeast(1)
                val cycle = onDur + offDur
                repeat(editable.repeatCount.coerceAtLeast(1)) { rep ->
                    val base = elapsed + rep * cycle
                    for (cue in editable.cues) {
                        val clamped = cue.timeOffsetSeconds.coerceIn(0, onDur)
                        if (clamped != cue.timeOffsetSeconds) trimmedCues++
                        absoluteCues.add(cue.copy(timeOffsetSeconds = base + clamped))
                    }
                    for (cue in editable.restCues) {
                        val clamped = cue.timeOffsetSeconds.coerceIn(0, offDur)
                        if (clamped != cue.timeOffsetSeconds) trimmedCues++
                        absoluteCues.add(cue.copy(timeOffsetSeconds = base + onDur + clamped))
                    }
                }
            } else {
                val segment = expanded.single()
                val clamped = editable.cues.map { cue ->
                    if (cue.timeOffsetSeconds !in 0..segment.durationSeconds) trimmedCues++
                    cue.copy(timeOffsetSeconds = cue.timeOffsetSeconds.coerceIn(0, segment.durationSeconds))
                }.sortedBy { it.timeOffsetSeconds }
                for (cue in clamped) {
                    absoluteCues.add(cue.copy(timeOffsetSeconds = elapsed + cue.timeOffsetSeconds))
                }
            }
            for (segment in expanded) {
                elapsed += segment.durationSeconds
                builtSegments.add(segment)
                rowForSegment.add(rowIndex)
            }
        }
        return BuiltWorkout(
            Workout(
                name = name.trim(),
                author = author.trim(),
                description = description.trim(),
                tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                segments = builtSegments,
                textEvents = absoluteCues.sortedBy { it.timeOffsetSeconds }
            ),
            rowForSegment,
            trimmedCues
        )
    }

    fun buildWorkout(): Workout = buildDetailed().workout

    /**
     * Writable rows for [ZwoWriter.serializeStructured]: interval rows stay a
     * single `<IntervalsT>` (never de-flattened to N `SteadyState` pairs), so
     * edit → save preserves the block structure and per-repeat cue templates.
     * Cue offsets are clamped exactly like [buildDetailed] so the validator's
     * trim warning and the written file can never disagree.
     */
    fun buildStructureItems(): List<ZwoWriter.StructureItem> = segments.map { row ->
        if (row.type != EditableSegmentType.INTERVALS) {
            val segment = row.toWorkoutSegments().single()
            val clamped = row.cues.map { cue ->
                cue.copy(timeOffsetSeconds = cue.timeOffsetSeconds.coerceIn(0, segment.durationSeconds))
            }.sortedBy { it.timeOffsetSeconds }
            ZwoWriter.StructureItem.Single(segment, clamped)
        } else {
            ZwoWriter.StructureItem.Intervals(
                repeat = row.repeatCount.coerceAtLeast(1),
                onDurationSeconds = row.onDurationSeconds.coerceAtLeast(1),
                onPower = row.onPowerPct.toFraction(),
                offDurationSeconds = row.offDurationSeconds.coerceAtLeast(1),
                offPower = row.offPowerPct.toFraction(),
                cadence = row.cadence,
                restingCadence = row.restingCadence,
                cues = row.cues.sortedBy { it.timeOffsetSeconds },
                restCues = row.restCues.sortedBy { it.timeOffsetSeconds }
            )
        }
    }

    fun issues(): List<WorkoutValidator.ValidationIssue> {
        val detailed = buildDetailed()
        val mapped = WorkoutValidator.validate(detailed.workout).map { issue ->
            val row = issue.segmentIndex?.let { detailed.rowForSegment.getOrElse(it) { it } }
            issue.copy(segmentIndex = row)
        }
        if (detailed.trimmedCueCount == 0) return mapped
        return mapped + WorkoutValidator.ValidationIssue(
            segmentIndex = null,
            field = WorkoutValidator.Field.CUES,
            message = "${detailed.trimmedCueCount} coaching cue${if (detailed.trimmedCueCount == 1) "" else "s"} " +
                "trimmed to fit shortened steps (delete them or lengthen the step)",
            isError = false
        )
    }

    fun canSave(): Boolean = issues().none { it.isError }

    /** Filename the draft would save to (original, or derived from name). */
    fun targetFilename(): String = originalFilename ?: deriveFilename(name)

    /**
     * Saves the draft. When the target file exists and is not the file being
     * edited, returns failure with [NeedsOverwrite] and stashes the filename;
     * the UI confirms, then retries with `overwrite = true`.
     *
     * @param asCopy derive the filename from the workout name even when
     * editing an existing file (fork instead of overwrite).
     */
    fun save(overwrite: Boolean = false, asCopy: Boolean = false): Result<String> {
        if (!canSave()) {
            return Result.failure(IllegalStateException("Fix validation errors before saving"))
        }
        val detailed = buildDetailed()
        val xml = ZwoWriter.serializeStructured(
            name = detailed.workout.name,
            author = detailed.workout.author,
            description = detailed.workout.description,
            sportType = detailed.workout.sportType,
            tags = detailed.workout.tags,
            items = buildStructureItems()
        )
        // Backstop: the writer's output must always re-parse.
        val reparsed = ZwoParser.parseSafe(xml).getOrNull()
            ?: return Result.failure(IllegalStateException("Generated workout failed to re-parse"))
        if (reparsed.segments.isEmpty()) {
            return Result.failure(IllegalStateException("Workout has no segments"))
        }
        val filename = if (asCopy || originalFilename == null) deriveFilename(name) else originalFilename!!
        if (!overwrite && filename != originalFilename && repository.workoutExists(filename)) {
            pendingOverwriteFilename = filename
            pendingOverwriteAsCopy = asCopy
            return Result.failure(NeedsOverwrite(filename))
        }
        repository.saveWorkout(filename, xml).getOrElse { return Result.failure(it) }
        originalFilename = filename
        pendingOverwriteFilename = null
        pendingOverwriteAsCopy = false
        isDirty = false
        // Reload so cues/durations reflect clamped writer output.
        if (!load(filename)) {
            return Result.failure(IllegalStateException("Saved $filename but failed to reload it"))
        }
        return Result.success(filename)
    }

    /** Restores a seeded file to factory content and reloads the draft. */
    fun resetToOriginal(): Boolean {
        val filename = originalFilename ?: return false
        if (!repository.resetSampleWorkout(filename)) return false
        return load(filename)
    }

    /**
     * Discards unsaved edits, restoring the last saved/opened state (or a
     * fresh draft when nothing was ever saved). The discard-confirm path must
     * call this before navigating away, otherwise the ViewModel retains the
     * dirty draft and re-entering the same file resurfaces rejected edits.
     */
    fun discardChanges() {
        val filename = originalFilename
        if (filename != null) {
            if (!load(filename)) loadNew()
        } else {
            loadNew()
        }
    }

    fun clearPendingOverwrite() {
        pendingOverwriteFilename = null
        pendingOverwriteAsCopy = false
    }

    fun updateHeader(name: String? = null, author: String? = null, description: String? = null, tagsText: String? = null) {
        name?.let { this.name = it }
        author?.let { this.author = it }
        description?.let { this.description = it }
        tagsText?.let { this.tagsText = it }
        isDirty = true
    }

    /** Currently applied tags, parsed from [tagsText]. */
    val currentTags: List<String>
        get() = tagsText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Adds [tag] to this workout if not already present (case-insensitive).
     * If [tag] contains commas, tokens are split, trimmed, and added individually.
     * Normalizes against [canonicalTags] if a case-insensitive match exists.
     * Sets [isDirty] to true. Returns true if at least one tag was added.
     */
    fun addTag(tag: String, canonicalTags: List<String> = WorkoutTags.CANONICAL): Boolean {
        val tokens = tag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        val existing = currentTags.toMutableList()
        var anyAdded = false
        for (token in tokens) {
            if (existing.none { it.equals(token, ignoreCase = true) }) {
                val resolvedTag = canonicalTags.find { it.equals(token, ignoreCase = true) } ?: token
                existing.add(resolvedTag)
                anyAdded = true
            }
        }
        if (anyAdded) {
            tagsText = existing.joinToString(", ")
            isDirty = true
        }
        return anyAdded
    }

    /**
     * Removes [tag] from this workout (case-insensitive).
     * Sets [isDirty] to true. Returns true if the tag was removed.
     */
    fun removeTag(tag: String): Boolean {
        val trimmed = tag.trim()
        val existing = currentTags
        val filtered = existing.filterNot { it.equals(trimmed, ignoreCase = true) }
        if (filtered.size == existing.size) return false
        tagsText = filtered.joinToString(", ")
        isDirty = true
        return true
    }

    /**
     * Observable tag suggestions gathered off-thread from canonical tags
     * and cached workouts. Defaults to canonical tags immediately so the UI
     * never blocks on disk I/O.
     */
    var availableTagSuggestions: List<String> by mutableStateOf(WorkoutTags.CANONICAL)
        internal set

    fun setAvailableTagSuggestions(suggestions: List<String>) {
        availableTagSuggestions = suggestions
    }

    fun updateSegment(index: Int, transform: (EditableSegment) -> EditableSegment) {
        if (index !in segments.indices) return
        segments[index] = transform(segments[index])
        isDirty = true
    }

    fun changeSegmentType(index: Int, type: EditableSegmentType) {
        if (index !in segments.indices) return
        val current = segments[index]
        if (current.type == type) return
        val defaults = EditableSegment.defaultForType(type, current.id)
        // Preserve power context across the switch: single↔range converts via
        // average/flattening so a Steady→Warmup→Steady round-trip is stable.
        // Interval rows use their work (on) power as the representative load.
        val currentSingle: Int? = when (current.type) {
            EditableSegmentType.STEADY_STATE -> current.powerPct
            EditableSegmentType.INTERVALS -> current.onPowerPct
            else -> null
        }
        // Rest-interval cues have no home on single-segment rows: merging them
        // into the work cues (instead of dropping) keeps every cue visible and
        // lets duration clamping surface a trim warning rather than deleting.
        val carriedRestCues = if (current.type == EditableSegmentType.INTERVALS) {
            current.restCues
        } else {
            emptyList()
        }
        val withPowers = when (type) {
            EditableSegmentType.STEADY_STATE -> defaults.copy(
                powerPct = currentSingle
                    ?: ((current.powerLowPct + current.powerHighPct) / 2)
            )
            EditableSegmentType.WARMUP,
            EditableSegmentType.COOLDOWN,
            EditableSegmentType.RAMP -> defaults.copy(
                powerLowPct = currentSingle ?: current.powerLowPct,
                powerHighPct = currentSingle ?: current.powerHighPct
            )
            EditableSegmentType.FREE_RIDE,
            EditableSegmentType.MAX_EFFORT -> defaults
            EditableSegmentType.INTERVALS -> defaults.copy(
                onPowerPct = currentSingle
                    ?: ((current.powerLowPct + current.powerHighPct) / 2),
                // Keep work cues as-is; rest cues only exist on interval rows.
                restCues = if (current.type == EditableSegmentType.INTERVALS) {
                    current.restCues
                } else {
                    defaults.restCues
                }
            )
        }
        segments[index] = withPowers.copy(
            durationSeconds = current.durationSeconds,
            cadence = current.cadence,
            cues = current.cues + carriedRestCues,
            // Single-segment rows have no rest-cue slot; interval targets keep
            // their own rest cues (handled above).
            restCues = if (type == EditableSegmentType.INTERVALS) withPowers.restCues else emptyList()
        )
        isDirty = true
    }

    fun addSegment(type: EditableSegmentType) {
        segments.add(EditableSegment.defaultForType(type, nextId++))
        isDirty = true
    }

    fun deleteSegment(index: Int) {
        if (segments.size <= 1 || index !in segments.indices) return
        segments.removeAt(index)
        isDirty = true
    }

    /** Removes one coaching cue (rest-interval cues via [rest] = true). */
    fun deleteCue(rowIndex: Int, cue: WorkoutTextEvent, rest: Boolean = false) {
        if (rowIndex !in segments.indices) return
        val row = segments[rowIndex]
        if (!rest) {
            val remaining = row.cues.toMutableList()
            if (!remaining.remove(cue)) return
            segments[rowIndex] = row.copy(cues = remaining)
        } else {
            val remaining = row.restCues.toMutableList()
            if (!remaining.remove(cue)) return
            segments[rowIndex] = row.copy(restCues = remaining)
        }
        isDirty = true
    }

    fun duplicateSegment(index: Int) {
        if (index !in segments.indices) return
        val copy = segments[index].copy(id = nextId++)
        segments.add(index + 1, copy)
        isDirty = true
    }

    fun moveSegment(from: Int, to: Int) {
        if (from !in segments.indices || to !in segments.indices || from == to) return
        val moved = segments.removeAt(from)
        segments.add(to, moved)
        isDirty = true
    }

    /** Thrown (as [Result.failure]) when saving needs overwrite confirmation. */
    class NeedsOverwrite(val filename: String) : IllegalStateException("File exists: $filename")

    companion object {
        fun deriveFilename(name: String): String {
            val base = name.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "custom_workout" }
            return "$base.zwo"
        }
    }
}

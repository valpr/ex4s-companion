package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.editor.EditableSegment
import com.valpr.bikecompanion.ui.editor.EditableSegmentType
import com.valpr.bikecompanion.ui.editor.WorkoutEditorState
import com.valpr.bikecompanion.workout.WorkoutRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WorkoutEditorStateTest {

    private lateinit var tempDir: File
    private lateinit var repository: WorkoutRepository
    private lateinit var state: WorkoutEditorState

    @Before
    fun setUp() {
        tempDir = File.createTempFile("editor_test_", "").apply {
            delete()
            mkdirs()
        }
        repository = WorkoutRepository(tempDir)
        state = WorkoutEditorState(repository)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun loadNew_startsWithSingleSteadySegment() {
        state.loadNew()
        assertEquals(1, state.segments.size)
        assertEquals(EditableSegmentType.STEADY_STATE, state.segments[0].type)
        assertFalse(state.isDirty)
    }

    @Test
    fun load_existingSample_populatesDraft() {
        repository.ensureSampleWorkouts()
        assertTrue(state.load("hiit_30_intervals.zwo"))
        assertEquals("HIIT Intervals (30 min)", state.name)
        // Warmup + 1 collapsed IntervalsT row (10x) + Cooldown.
        assertEquals(3, state.segments.size)
        val intervals = state.segments[1]
        assertEquals(EditableSegmentType.INTERVALS, intervals.type)
        assertEquals(10, intervals.repeatCount)
        assertEquals(40, intervals.onDurationSeconds)
        assertEquals(115, intervals.onPowerPct)
        assertTrue(state.segments.any { it.cues.isNotEmpty() })
        assertFalse(state.isDirty)
    }

    @Test
    fun load_missingFile_returnsFalse() {
        assertFalse(state.load("nope.zwo"))
    }

    @Test
    fun mutations_markDirty_andMoveReorders() {
        state.loadNew()
        state.addSegment(EditableSegmentType.FREE_RIDE)
        assertTrue(state.isDirty)
        val first = state.segments[0]
        state.moveSegment(0, 1)
        assertEquals(first, state.segments[1])
        state.duplicateSegment(0)
        assertEquals(3, state.segments.size)
        state.deleteSegment(0)
        assertEquals(2, state.segments.size)
    }

    @Test
    fun deleteSegment_neverRemovesLastRow() {
        state.loadNew()
        state.deleteSegment(0)
        assertEquals(1, state.segments.size)
    }

    @Test
    fun changeSegmentType_preservesDurationAndCues() {
        state.loadNew()
        state.updateSegment(0) { it.copy(durationSeconds = 240, powerPct = 80) }
        state.changeSegmentType(0, EditableSegmentType.WARMUP)
        assertEquals(240, state.segments[0].durationSeconds)
        assertEquals(EditableSegmentType.WARMUP, state.segments[0].type)
        assertEquals(80, state.segments[0].powerLowPct)
        assertEquals(80, state.segments[0].powerHighPct)
        state.changeSegmentType(0, EditableSegmentType.STEADY_STATE)
        assertEquals(80, state.segments[0].powerPct)
    }

    @Test
    fun save_newDraft_writesFileAndClearsDirty() {
        state.loadNew()
        state.updateHeader(name = "Lunch Ride", tagsText = "HIIT")
        val result = state.save()
        assertTrue(result.isSuccess)
        assertEquals("lunch_ride.zwo", state.targetFilename())
        assertTrue(repository.workoutExists("lunch_ride.zwo"))
        assertFalse(state.isDirty)
        val reloaded = repository.loadWorkout("lunch_ride.zwo").getOrThrow()
        assertEquals("Lunch Ride", reloaded.name)
        assertEquals(listOf("HIIT"), reloaded.tags)
    }

    @Test
    fun save_invalidDraft_fails() {
        state.loadNew()
        state.updateHeader(name = "")
        assertTrue(state.save().isFailure)
        assertFalse(repository.workoutExists("custom_workout.zwo"))
    }

    @Test
    fun save_collisionWithoutOverwrite_failsThenSucceedsWithOverwrite() {
        repository.ensureSampleWorkouts()
        state.loadNew()
        state.updateHeader(name = "HIIT Intervals (30 min)")
        // Derives hiit_intervals_30_min.zwo — force the seeded filename instead.
        state.updateHeader(name = "Forced")
        val forced = state.save()
        assertTrue(forced.isSuccess)

        val second = WorkoutEditorState(repository)
        second.loadNew()
        second.updateHeader(name = "Forced")
        val collision = second.save(overwrite = false)
        assertTrue(collision.isFailure)
        assertTrue(collision.exceptionOrNull() is WorkoutEditorState.NeedsOverwrite)
        assertTrue(second.save(overwrite = true).isSuccess)
    }

    @Test
    fun saveAsCopy_forksSeededFile() {
        repository.ensureSampleWorkouts()
        state.load("recovery_20_low_impact.zwo")
        state.updateHeader(name = "My Recovery Remix")
        val result = state.save(asCopy = true)
        assertTrue(result.isSuccess)
        assertEquals("my_recovery_remix.zwo", result.getOrThrow())
        // Original untouched, copy exists.
        assertEquals(
            "Low Impact Recovery (20 min)",
            repository.loadWorkout("recovery_20_low_impact.zwo").getOrThrow().name
        )
        assertEquals(
            "My Recovery Remix",
            repository.loadWorkout("my_recovery_remix.zwo").getOrThrow().name
        )
    }

    @Test
    fun shrinkingSegmentWithCue_surfacesTrimWarning() {
        repository.ensureSampleWorkouts()
        assertTrue(state.load("recovery_20_low_impact.zwo"))
        // Warmup cue sits at offset 10 of 240s; shrink below it.
        state.updateSegment(0) { it.copy(durationSeconds = 5) }
        val warnings = state.issues().filter { !it.isError }
        assertTrue(warnings.any { it.field == com.valpr.bikecompanion.workout.WorkoutValidator.Field.CUES })
        assertTrue(state.canSave())
    }

    @Test
    fun deleteCue_removesItAndMarksDirty() {
        repository.ensureSampleWorkouts()
        assertTrue(state.load("recovery_20_low_impact.zwo"))
        val cue = state.segments[0].cues.first()
        state.deleteCue(0, cue)
        assertFalse(state.segments[0].cues.contains(cue))
        assertTrue(state.isDirty)
        assertTrue(state.buildWorkout().textEvents.none { it.message == cue.message })
    }

    @Test
    fun editingPower_preservesCuesOnSave() {
        repository.ensureSampleWorkouts()
        assertTrue(state.load("hiit_30_intervals.zwo"))
        val cuesBefore = state.buildWorkout().textEvents
        assertTrue(cuesBefore.isNotEmpty())
        state.updateSegment(0) { it.copy(powerLowPct = 45) }
        assertTrue(state.save().isSuccess)
        assertEquals(cuesBefore, repository.loadWorkout("hiit_30_intervals.zwo").getOrThrow().textEvents)
    }

    @Test
    fun resetToOriginal_restoresSeededContent() {
        repository.ensureSampleWorkouts()
        state.load("recovery_20_low_impact.zwo")
        state.updateSegment(0) { it.copy(durationSeconds = 999) }
        assertTrue(state.isDirty)
        assertTrue(state.resetToOriginal())
        assertEquals(240, state.segments[0].durationSeconds)
        assertFalse(state.isDirty)
    }

    @Test
    fun resetToOriginal_nonSample_returnsFalse() {
        state.loadNew()
        state.updateHeader(name = "Custom")
        assertTrue(state.save().isSuccess)
        assertFalse(state.resetToOriginal())
    }

    @Test
    fun deriveFilename_sanitizesName() {
        assertEquals("lunch_break_hiit.zwo", WorkoutEditorState.deriveFilename("Lunch Break HIIT!"))
        assertEquals("custom_workout.zwo", WorkoutEditorState.deriveFilename("   "))
    }

    @Test
    fun editableSegment_roundTripsAllTypes() {
        val singleTypes = EditableSegmentType.entries - EditableSegmentType.INTERVALS
        assertEquals(6, singleTypes.size)
        for (type in singleTypes) {
            val editable = EditableSegment.defaultForType(type, 1L)
            val built = editable.toWorkoutSegment()
            val back = EditableSegment.fromWorkoutSegment(built, 2L, emptyList())
            assertEquals(type, back.type)
            assertEquals(editable.durationSeconds, back.durationSeconds)
        }
    }

    @Test
    fun intervalsRow_expandsToOnOffPairs() {
        val row = EditableSegment.defaultForType(EditableSegmentType.INTERVALS, 1L).copy(
            repeatCount = 3,
            onDurationSeconds = 40,
            onPowerPct = 115,
            offDurationSeconds = 80,
            offPowerPct = 50,
            cadence = 95,
            restingCadence = 80
        )
        val expanded = row.toWorkoutSegments()
        assertEquals(6, expanded.size)
        val on = expanded[0] as com.valpr.bikecompanion.workout.WorkoutSegment.SteadyState
        assertEquals(40, on.durationSeconds)
        assertEquals(95, on.targetCadence)
        val built = WorkoutEditorState(repository).apply {
            loadNew()
            segments.clear()
            segments.add(row)
        }.buildWorkout()
        assertEquals(3 * (40 + 80), built.totalDurationSeconds)
    }

    @Test
    fun collapseIntervalRows_singleRepeatOrMismatch_staysSingle() {
        val xml = """
            <workout_file>
                <name>Mixed</name>
                <workout>
                    <SteadyState Duration="60" Power="0.50"/>
                    <SteadyState Duration="60" Power="0.70"/>
                    <SteadyState Duration="60" Power="0.50"/>
                    <SteadyState Duration="60" Power="0.70"/>
                    <SteadyState Duration="60" Power="0.90"/>
                </workout>
            </workout_file>
        """.trimIndent()
        repository.saveWorkout("mixed.zwo", xml)
        assertTrue(state.load("mixed.zwo"))
        // First 4 collapse to 1 interval row; trailing odd one stays single.
        assertEquals(2, state.segments.size)
        assertEquals(EditableSegmentType.INTERVALS, state.segments[0].type)
        assertEquals(2, state.segments[0].repeatCount)
        assertEquals(EditableSegmentType.STEADY_STATE, state.segments[1].type)
    }

    @Test
    fun loadSaveRoundTrip_preservesParsedWorkout() {
        repository.ensureSampleWorkouts()
        val original = repository.loadWorkout("vo2_45_40_20s.zwo").getOrThrow()
        assertTrue(state.load("vo2_45_40_20s.zwo"))
        assertTrue(state.save().isSuccess)
        val reparsed = repository.loadWorkout("vo2_45_40_20s.zwo").getOrThrow()
        assertEquals(original.segments.size, reparsed.segments.size)
        assertEquals(original.totalDurationSeconds, reparsed.totalDurationSeconds)
        assertEquals(original.textEvents, reparsed.textEvents)
        assertEquals(original.estimatedTss, reparsed.estimatedTss, 0.001)
    }

    @Test
    fun intervalValidationError_mapsToRowIndex() {
        state.loadNew()
        state.segments.clear()
        state.updateHeader(name = "Intervals")
        state.addSegment(EditableSegmentType.INTERVALS)
        state.updateSegment(0) { it.copy(onPowerPct = 0) }
        val errors = state.issues().filter { it.isError }
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.all { it.segmentIndex == 0 })
        assertFalse(state.canSave())
    }

    @Test
    fun discardChanges_restoresLastSavedAndClearsDirty() {
        repository.ensureSampleWorkouts()
        assertTrue(state.load("recovery_20_low_impact.zwo"))
        state.updateSegment(0) { it.copy(durationSeconds = 999) }
        assertTrue(state.isDirty)
        state.discardChanges()
        assertFalse(state.isDirty)
        assertEquals(240, state.segments[0].durationSeconds)
    }

    @Test
    fun discardChanges_newDraft_resetsToClean() {
        state.loadNew()
        state.updateHeader(name = "Scratch")
        assertTrue(state.isDirty)
        state.discardChanges()
        assertFalse(state.isDirty)
        assertEquals("", state.name)
    }

    @Test
    fun collapseIntervalRows_distinctPerRepeatCues_staysSingle() {
        val xml = """
            <workout_file>
                <name>Mixed</name>
                <workout>
                    <SteadyState Duration="60" Power="0.50">
                        <textevent timeoffset="5" message="Rep one push!"/>
                    </SteadyState>
                    <SteadyState Duration="60" Power="0.70"/>
                    <SteadyState Duration="60" Power="0.50">
                        <textevent timeoffset="5" message="Rep two push!"/>
                    </SteadyState>
                    <SteadyState Duration="60" Power="0.70"/>
                </workout>
            </workout_file>
        """.trimIndent()
        repository.saveWorkout("distinct_cues.zwo", xml)
        assertTrue(state.load("distinct_cues.zwo"))
        // Identical powers but distinct per-repeat messages: collapsing would
        // delete rep two's cue, so rows must stay single.
        assertEquals(4, state.segments.size)
        assertTrue(state.segments.none { it.type == EditableSegmentType.INTERVALS })
        assertEquals(2, state.buildWorkout().textEvents.size)
    }

    @Test
    fun save_intervalRow_writesIntervalsTBlock() {
        state.loadNew()
        state.updateHeader(name = "Interval Save")
        state.segments.clear()
        state.addSegment(EditableSegmentType.INTERVALS)
        state.updateSegment(0) { it.copy(repeatCount = 3) }
        val result = state.save()
        assertTrue(result.isSuccess)
        val raw = File(tempDir, result.getOrThrow()).readText()
        assertTrue(raw.contains("<IntervalsT"))
        // Reload collapses back to the single interval row with cues intact.
        val reloaded = WorkoutEditorState(repository)
        assertTrue(reloaded.load(result.getOrThrow()))
        assertEquals(1, reloaded.segments.size)
        assertEquals(EditableSegmentType.INTERVALS, reloaded.segments[0].type)
        assertEquals(3, reloaded.segments[0].repeatCount)
        assertEquals(6, reloaded.buildWorkout().segments.size)
    }

    @Test
    fun changeSegmentType_intervalsToSteady_mergesRestCuesWithoutLoss() {
        state.loadNew()
        state.segments.clear()
        state.addSegment(EditableSegmentType.INTERVALS)
        val workCue = com.valpr.bikecompanion.workout.WorkoutTextEvent(5, "Work!")
        val restCue = com.valpr.bikecompanion.workout.WorkoutTextEvent(5, "Rest!")
        state.updateSegment(0) { it.copy(cues = listOf(workCue), restCues = listOf(restCue)) }
        state.changeSegmentType(0, EditableSegmentType.STEADY_STATE)
        val merged = state.segments[0].cues.map { it.message }
        assertTrue(merged.contains("Work!"))
        assertTrue(merged.contains("Rest!"))
    }
}

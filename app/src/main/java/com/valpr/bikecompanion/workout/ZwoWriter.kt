package com.valpr.bikecompanion.workout

import java.math.BigDecimal

/**
 * Serializes [Workout] back to Zwift (.zwo) XML.
 *
 * Inverse of [ZwoParser] for the segment types the editor supports.
 * Absolute [Workout.textEvents] are redistributed into their containing
 * segments as segment-relative `<textevent>` cues on write, so a
 * parse → serialize round-trip preserves coaching cues.
 *
 * Interval rows round-trip as `<IntervalsT>` via [serializeStructured];
 * [serialize] keeps the legacy flattened form for generic callers.
 *
 * Framework-free so round-trips stay plain-JUnit testable (AGENTS.md §8).
 */
object ZwoWriter {

    /**
     * One writable row: a single segment, or a repeating on/off block that
     * is emitted as a single `<IntervalsT>` element (instead of N flattened
     * `SteadyState` pairs) so edit → save never de-flattens interval blocks.
     */
    sealed interface StructureItem {
        data class Single(
            val segment: WorkoutSegment,
            val cues: List<WorkoutTextEvent> = emptyList()
        ) : StructureItem

        data class Intervals(
            val repeat: Int,
            val onDurationSeconds: Int,
            val onPower: Float,
            val offDurationSeconds: Int,
            val offPower: Float,
            val cadence: Int?,
            val restingCadence: Int?,
            val cues: List<WorkoutTextEvent> = emptyList(),
            val restCues: List<WorkoutTextEvent> = emptyList()
        ) : StructureItem
    }

    /**
     * Structured serializer: [StructureItem.Intervals] rows are written as a
     * single `<IntervalsT>` with work cues at their on-offset and rest cues at
     * `onDuration + offset`, mirroring how [ZwoParser] replicates nested cues
     * per repeat — so absolute cue positions survive the round-trip.
     */
    fun serializeStructured(
        name: String,
        author: String,
        description: String,
        sportType: String,
        tags: List<String>,
        items: List<StructureItem>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("<workout_file>")
        sb.appendLine("    <author>${author.escapeXml()}</author>")
        sb.appendLine("    <name>${name.escapeXml()}</name>")
        sb.appendLine("    <description>${description.escapeXml()}</description>")
        sb.appendLine("    <sportType>${sportType.escapeXml()}</sportType>")
        sb.appendLine("    <tags>")
        for (tag in tags) {
            sb.appendLine("        <tag name=\"${tag.escapeXml()}\"/>")
        }
        sb.appendLine("    </tags>")
        sb.appendLine("    <workout>")
        for (item in items) {
            when (item) {
                is StructureItem.Single -> sb.append(segmentElement(item.segment, item.cues))
                is StructureItem.Intervals -> sb.append(intervalsElement(item))
            }
        }
        sb.appendLine("    </workout>")
        sb.appendLine("</workout_file>")
        return sb.toString()
    }

    fun serialize(workout: Workout): String {
        val cuesPerSegment = distributeCues(workout)
        val items = workout.segments.mapIndexed { index, segment ->
            StructureItem.Single(segment, cuesPerSegment.getOrElse(index) { emptyList() })
        }
        return serializeStructured(
            name = workout.name,
            author = workout.author,
            description = workout.description,
            sportType = workout.sportType,
            tags = workout.tags,
            items = items
        )
    }

    /**
     * Assigns each absolute text event to its containing segment, returned as
     * segment-relative cues. Events past the workout end clamp to the last
     * segment; empty workouts yield empty lists.
     */
    internal fun distributeCues(workout: Workout): List<List<WorkoutTextEvent>> {
        val perSegment = List(workout.segments.size) { mutableListOf<WorkoutTextEvent>() }
        if (workout.segments.isEmpty()) return perSegment
        var start = 0
        val starts = workout.segments.map { segment ->
            val segmentStart = start
            start += segment.durationSeconds
            segmentStart
        }
        val total = start
        for (event in workout.textEvents) {
            val clamped = event.timeOffsetSeconds.coerceIn(0, total)
            var owner = perSegment.lastIndex
            for (i in workout.segments.indices) {
                if (clamped < starts[i] + workout.segments[i].durationSeconds) {
                    owner = i
                    break
                }
            }
            perSegment[owner].add(event.copy(timeOffsetSeconds = clamped - starts[owner]))
        }
        return perSegment.map { cues -> cues.sortedBy { it.timeOffsetSeconds } }
    }

    private fun segmentElement(segment: WorkoutSegment, cues: List<WorkoutTextEvent>): String {
        val (tag, attrs) = when (segment) {
            is WorkoutSegment.Warmup ->
                "Warmup" to
                    "Duration=\"${segment.durationSeconds}\" " +
                    "PowerLow=\"${segment.powerLow.toZwo()}\" " +
                    "PowerHigh=\"${segment.powerHigh.toZwo()}\"" +
                    segment.targetCadence?.let { " Cadence=\"$it\"" }.orEmpty()
            is WorkoutSegment.Cooldown ->
                "Cooldown" to
                    "Duration=\"${segment.durationSeconds}\" " +
                    "PowerLow=\"${segment.powerLow.toZwo()}\" " +
                    "PowerHigh=\"${segment.powerHigh.toZwo()}\"" +
                    segment.targetCadence?.let { " Cadence=\"$it\"" }.orEmpty()
            is WorkoutSegment.SteadyState ->
                "SteadyState" to
                    "Duration=\"${segment.durationSeconds}\" " +
                    "Power=\"${segment.power.toZwo()}\"" +
                    segment.targetCadence?.let { " Cadence=\"$it\"" }.orEmpty()
            is WorkoutSegment.Ramp ->
                "Ramp" to
                    "Duration=\"${segment.durationSeconds}\" " +
                    "PowerLow=\"${segment.powerLow.toZwo()}\" " +
                    "PowerHigh=\"${segment.powerHigh.toZwo()}\"" +
                    segment.targetCadence?.let { " Cadence=\"$it\"" }.orEmpty()
            is WorkoutSegment.FreeRide ->
                "FreeRide" to
                    buildString {
                        append("Duration=\"${segment.durationSeconds}\"")
                        if (segment.flatRoad) append(" FlatRoad=\"1\"")
                        segment.targetCadence?.let { append(" Cadence=\"$it\"") }
                    }
            is WorkoutSegment.MaxEffort ->
                "MaxEffort" to
                    "Duration=\"${segment.durationSeconds}\"" +
                    segment.targetCadence?.let { " Cadence=\"$it\"" }.orEmpty()
        }
        if (cues.isEmpty()) {
            return "        <$tag $attrs/>\n"
        }
        val sb = StringBuilder()
        sb.appendLine("        <$tag $attrs>")
        for (cue in cues) {
            sb.appendLine(
                "            <textevent timeoffset=\"${cue.timeOffsetSeconds}\" " +
                    "message=\"${cue.message.escapeXml()}\"/>"
            )
        }
        sb.appendLine("        </$tag>")
        return sb.toString()
    }

    /**
     * Writes one `<IntervalsT>` block. Work cues keep their on-offset; rest
     * cues are stored at `onDuration + offset` so [ZwoParser]'s per-repeat
     * replication restores the exact absolute positions built by the editor.
     */
    private fun intervalsElement(item: StructureItem.Intervals): String {
        val repeat = item.repeat.coerceAtLeast(1)
        val onDur = item.onDurationSeconds.coerceAtLeast(1)
        val offDur = item.offDurationSeconds.coerceAtLeast(1)
        val attrs = buildString {
            append("Repeat=\"$repeat\" ")
            append("OnDuration=\"$onDur\" ")
            append("OffDuration=\"$offDur\" ")
            append("OnPower=\"${item.onPower.toZwo()}\" ")
            append("OffPower=\"${item.offPower.toZwo()}\"")
            item.cadence?.let { append(" Cadence=\"$it\"") }
            item.restingCadence?.let { append(" CadenceResting=\"$it\"") }
        }
        val onCues = item.cues.map { cue ->
            cue.copy(timeOffsetSeconds = cue.timeOffsetSeconds.coerceIn(0, onDur))
        }.sortedBy { it.timeOffsetSeconds }
        val offCues = item.restCues.map { cue ->
            cue.copy(timeOffsetSeconds = (onDur + cue.timeOffsetSeconds.coerceIn(0, offDur)))
        }.sortedBy { it.timeOffsetSeconds }
        val nested = (onCues + offCues).sortedBy { it.timeOffsetSeconds }
        if (nested.isEmpty()) {
            return "        <IntervalsT $attrs/>\n"
        }
        val sb = StringBuilder()
        sb.appendLine("        <IntervalsT $attrs>")
        for (cue in nested) {
            sb.appendLine(
                "            <textevent timeoffset=\"${cue.timeOffsetSeconds}\" " +
                    "message=\"${cue.message.escapeXml()}\"/>"
            )
        }
        sb.appendLine("        </IntervalsT>")
        return sb.toString()
    }

    private fun Float.toZwo(): String = BigDecimal(this.toString()).stripTrailingZeros().toPlainString()

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

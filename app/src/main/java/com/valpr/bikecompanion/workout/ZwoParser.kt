package com.valpr.bikecompanion.workout

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader

/**
 * Parser for Zwift Workout (.zwo) XML files using streaming [XmlPullParser].
 *
 * Implements full support for:
 * - <Warmup>, <Cooldown>, <SteadyState>, <Ramp>, <IntervalsT>, <FreeRide>, <MaxEffort>
 * - Flattening <IntervalsT Repeat="N"> into 2*N sequential On/Off segments
 * - Case-insensitive tag and attribute parsing (e.g. Power vs power, PowerLow vs powerlow)
 * - Scoped <textevent> parsing (segment-relative vs workout-cumulative)
 */
object ZwoParser {

    private val factory: XmlPullParserFactory by lazy {
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
    }

    /**
     * Parses a .zwo file from an [InputStream].
     */
    fun parse(inputStream: InputStream): Workout {
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        return parseXml(parser)
    }

    /**
     * Safely parses a .zwo file from an [InputStream], returning [Result.failure] on malformed XML.
     */
    fun parseSafe(inputStream: InputStream): Result<Workout> = runCatching {
        parse(inputStream)
    }

    /**
     * Parses a .zwo XML content string.
     */
    fun parse(xmlString: String): Workout {
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))
        return parseXml(parser)
    }

    /**
     * Safely parses a .zwo XML content string, returning [Result.failure] on malformed XML.
     */
    fun parseSafe(xmlString: String): Result<Workout> = runCatching {
        parse(xmlString)
    }

    private fun parseXml(parser: XmlPullParser): Workout {
        var name = "Untitled Workout"
        var author = ""
        var description = ""
        var sportType = "bike"
        val tags = mutableListOf<String>()
        val segments = mutableListOf<WorkoutSegment>()
        val textEvents = mutableListOf<WorkoutTextEvent>()

        var accumulatedWorkoutSeconds = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.lowercase()
                when (tagName) {
                    "name" -> name = parser.nextTextSafe()
                    "author" -> author = parser.nextTextSafe()
                    "description" -> description = parser.nextTextSafe()
                    "sporttype" -> sportType = parser.nextTextSafe()
                    "tag" -> {
                        val tagNameAttr = parser.getAttributeValueCaseInsensitive("name")
                        if (!tagNameAttr.isNullOrBlank()) {
                            tags.add(tagNameAttr.trim())
                        }
                    }
                    "workout" -> {
                        parseWorkoutSection(parser, accumulatedWorkoutSeconds, segments, textEvents)
                    }
                }
            }
            eventType = parser.next()
        }

        return Workout(
            name = name,
            author = author,
            description = description,
            sportType = sportType,
            tags = tags,
            segments = segments,
            textEvents = textEvents.sortedBy { it.timeOffsetSeconds }
        )
    }

    private fun parseWorkoutSection(
        parser: XmlPullParser,
        initialTimeOffset: Int,
        segments: MutableList<WorkoutSegment>,
        textEvents: MutableList<WorkoutTextEvent>
    ) {
        var accumulatedTime = initialTimeOffset
        var eventType = parser.next()

        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("workout", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.lowercase()
                val segmentStartOffset = accumulatedTime

                when (tagName) {
                    "warmup" -> {
                        val duration = parser.getAttributeInt("Duration", 0)
                        val powerLow = parser.getAttributeFloat("PowerLow", 0f)
                        val powerHigh = parser.getAttributeFloat("PowerHigh", powerLow)
                        val cadence = parser.getAttributeIntOrNull("Cadence")
                        if (duration > 0) {
                            segments.add(
                                WorkoutSegment.Warmup(
                                    durationSeconds = duration,
                                    powerLow = powerLow,
                                    powerHigh = powerHigh,
                                    targetCadence = cadence
                                )
                            )
                            accumulatedTime += duration
                        }
                        parseNestedTextEvents(parser, segmentStartOffset, textEvents)
                    }
                    "cooldown" -> {
                        val duration = parser.getAttributeInt("Duration", 0)
                        val powerLow = parser.getAttributeFloat("PowerLow", 0f)
                        val powerHigh = parser.getAttributeFloat("PowerHigh", powerLow)
                        val cadence = parser.getAttributeIntOrNull("Cadence")
                        if (duration > 0) {
                            segments.add(
                                WorkoutSegment.Cooldown(
                                    durationSeconds = duration,
                                    powerLow = powerLow,
                                    powerHigh = powerHigh,
                                    targetCadence = cadence
                                )
                            )
                            accumulatedTime += duration
                        }
                        parseNestedTextEvents(parser, segmentStartOffset, textEvents)
                    }
                    "steadystate" -> {
                        val duration = parser.getAttributeInt("Duration", 0)
                        val power = parser.getAttributeFloat("Power", 0f)
                        val cadence = parser.getAttributeIntOrNull("Cadence")
                        if (duration > 0) {
                            segments.add(
                                WorkoutSegment.SteadyState(
                                    durationSeconds = duration,
                                    power = power,
                                    targetCadence = cadence
                                )
                            )
                            accumulatedTime += duration
                        }
                        parseNestedTextEvents(parser, segmentStartOffset, textEvents)
                    }
                    "ramp" -> {
                        val duration = parser.getAttributeInt("Duration", 0)
                        val powerLow = parser.getAttributeFloat("PowerLow", 0f)
                        val powerHigh = parser.getAttributeFloat("PowerHigh", powerLow)
                        val cadence = parser.getAttributeIntOrNull("Cadence")
                        if (duration > 0) {
                            segments.add(
                                WorkoutSegment.Ramp(
                                    durationSeconds = duration,
                                    powerLow = powerLow,
                                    powerHigh = powerHigh,
                                    targetCadence = cadence
                                )
                            )
                            accumulatedTime += duration
                        }
                        parseNestedTextEvents(parser, segmentStartOffset, textEvents)
                    }
                    "intervalst" -> {
                        val repeat = parser.getAttributeInt("Repeat", 1).coerceAtLeast(1)
                        val onDuration = parser.getAttributeInt("OnDuration", 0)
                        val offDuration = parser.getAttributeInt("OffDuration", 0)
                        val onPower = parser.getAttributeFloat("OnPower", 0f)
                        val offPower = parser.getAttributeFloat("OffPower", 0f)
                        val cadence = parser.getAttributeIntOrNull("Cadence")
                        val cadenceResting = parser.getAttributeIntOrNull("CadenceResting")

                        // Collect nested cues once (relative offsets), then distribute per repeat.
                        val nestedCues = mutableListOf<WorkoutTextEvent>()
                        parseNestedTextEvents(parser, 0, nestedCues)
                        val cycleDuration = onDuration + offDuration

                        // Flatten repeats into discrete ON and OFF intervals
                        for (r in 0 until repeat) {
                            val repeatBase = segmentStartOffset + r * cycleDuration
                            for (cue in nestedCues) {
                                textEvents.add(
                                    cue.copy(timeOffsetSeconds = repeatBase + cue.timeOffsetSeconds)
                                )
                            }
                            if (onDuration > 0) {
                                segments.add(
                                    WorkoutSegment.SteadyState(
                                        durationSeconds = onDuration,
                                        power = onPower,
                                        targetCadence = cadence
                                    )
                                )
                                accumulatedTime += onDuration
                            }
                            if (offDuration > 0) {
                                segments.add(
                                    WorkoutSegment.SteadyState(
                                        durationSeconds = offDuration,
                                        power = offPower,
                                        targetCadence = cadenceResting
                                    )
                                )
                                accumulatedTime += offDuration
                            }
                        }
                    }
                    "freeride" -> {
                        val duration = parser.getAttributeInt("Duration", 0)
                        val flatRoad = parser.getAttributeInt("FlatRoad", 0) == 1
                        val cadence = parser.getAttributeIntOrNull("Cadence")
                        if (duration > 0) {
                            segments.add(
                                WorkoutSegment.FreeRide(
                                    durationSeconds = duration,
                                    flatRoad = flatRoad,
                                    targetCadence = cadence
                                )
                            )
                            accumulatedTime += duration
                        }
                        parseNestedTextEvents(parser, segmentStartOffset, textEvents)
                    }
                    "maxeffort" -> {
                        val duration = parser.getAttributeInt("Duration", 0)
                        val cadence = parser.getAttributeIntOrNull("Cadence")
                        if (duration > 0) {
                            segments.add(
                                WorkoutSegment.MaxEffort(
                                    durationSeconds = duration,
                                    targetCadence = cadence
                                )
                            )
                            accumulatedTime += duration
                        }
                        parseNestedTextEvents(parser, segmentStartOffset, textEvents)
                    }
                    "textevent" -> {
                        // Root <textevent> directly under <workout> (cumulative time offset)
                        val timeOffset = parser.getAttributeInt("timeoffset", 0)
                        val message = parser.getAttributeValueCaseInsensitive("message") ?: ""
                        if (message.isNotBlank()) {
                            textEvents.add(WorkoutTextEvent(timeOffsetSeconds = timeOffset, message = message))
                        }
                    }
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }
    }

    /**
     * Consumes child elements within a segment (e.g. nested <textevent>) until reaching
     * the segment's closing tag.
     */
    private fun parseNestedTextEvents(
        parser: XmlPullParser,
        segmentStartOffset: Int,
        textEvents: MutableList<WorkoutTextEvent>
    ) {
        val parentTag = parser.name
        // If it was a self-closing tag (e.g. <SteadyState Duration="120"... />), nothing to parse inside
        if (parser.isEmptyElementTag) return

        var depth = 1
        while (depth > 0) {
            val event = parser.next()
            if (event == XmlPullParser.START_TAG) {
                depth++
                if (parser.name.equals("textevent", ignoreCase = true)) {
                    val timeOffset = parser.getAttributeInt("timeoffset", 0)
                    val message = parser.getAttributeValueCaseInsensitive("message") ?: ""
                    if (message.isNotBlank()) {
                        textEvents.add(
                            WorkoutTextEvent(
                                timeOffsetSeconds = segmentStartOffset + timeOffset,
                                message = message
                            )
                        )
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                depth--
                if (depth == 0 && parser.name.equals(parentTag, ignoreCase = true)) {
                    break
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                break
            }
        }
    }

    private fun XmlPullParser.nextTextSafe(): String = try {
        nextText().trim()
    } catch (_: Exception) {
        ""
    }

    fun XmlPullParser.getAttributeValueCaseInsensitive(name: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).equals(name, ignoreCase = true)) {
                return getAttributeValue(i)
            }
        }
        return null
    }

    fun XmlPullParser.getAttributeFloat(name: String, default: Float = 0f): Float = getAttributeValueCaseInsensitive(name)?.toFloatOrNull() ?: default

    fun XmlPullParser.getAttributeInt(name: String, default: Int = 0): Int = getAttributeValueCaseInsensitive(name)?.toIntOrNull() ?: default

    fun XmlPullParser.getAttributeIntOrNull(name: String): Int? = getAttributeValueCaseInsensitive(name)?.toIntOrNull()
}

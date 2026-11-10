/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.tcx.parser

import com.atrainingtracker.trainingtracker.routes.parsers.stripBomAndLeadingWhitespace
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivity
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivityExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxAuthor
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator
import com.atrainingtracker.trainingtracker.tcx.model.TcxDocument
import com.atrainingtracker.trainingtracker.tcx.model.TcxLap
import com.atrainingtracker.trainingtracker.tcx.model.TcxLapExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxPosition
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpoint
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpointExtensions
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Decoupled streaming XML pull parser producing structured [TcxDocument] DTOs.
 *
 * Enforces:
 * - REQ-DAT-040.3: Decoupled DTO XML Parser Bridge.
 * - REQ-MIG-037 / REQ-MIG-048.3: BOM and leading whitespace tolerance.
 * - REQ-DAT-012 / REQ-DAT-013: att: extensions and bracket notation fallback parsing.
 */
object TcxParser {

    private val BRACKET_REGEX = Regex("""^\[(.*?)\](?:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Parses TCX XML text into a [TcxDocument].
     */
    fun parse(xmlContent: String): Result<TcxDocument> {
        return parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
    }

    /**
     * Parses a TCX XML stream into a [TcxDocument].
     */
    fun parse(inputStream: InputStream): Result<TcxDocument> {
        return try {
            val safeStream = stripBomAndLeadingWhitespace(inputStream)
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser()
            parser.setInput(safeStream, "UTF-8")

            val activities = mutableListOf<TcxActivity>()
            var author: TcxAuthor? = null

            var currentSport = ""
            var currentActivityId = ""
            var currentActivityNotes: String? = null
            var currentActivityCustomName: String? = null
            var currentActivityCustomDesc: String? = null

            val currentLaps = mutableListOf<TcxLap>()
            var currentLapStartTime = ""
            var currentLapTotalTime = 0.0
            var currentLapDistance = 0.0
            var currentLapMaxSpeed: Double? = null
            var currentLapCalories: Int? = null
            var currentLapAvgHr: Int? = null
            var currentLapMaxHr: Int? = null
            var currentLapIntensity: String? = null
            var currentLapCadence: Int? = null
            var currentLapTriggerMethod: String? = null
            var currentLapNotes: String? = null
            var currentLapCustomName: String? = null
            var currentLapCustomDesc: String? = null

            val currentTrackpoints = mutableListOf<TcxTrackpoint>()
            var currentTpTime: String? = null
            var currentTpLat: Double? = null
            var currentTpLng: Double? = null
            var currentTpAlt: Double? = null
            var currentTpDist: Double? = null
            var currentTpHr: Int? = null
            var currentTpCad: Int? = null
            var currentTpSpeed: Double? = null
            var currentTpWatts: Int? = null
            var currentTpRunCadence: Int? = null

            var currentCreator: TcxCreator? = null
            var creatorName = ""
            var creatorUnitId = 0L
            var creatorProductId = 1
            var creatorVersionMajor = 4
            var creatorVersionMinor = 9
            var creatorBuildMajor = 0
            var creatorBuildMinor = 0

            var authorName = ""
            var authorVersionMajor = 4
            var authorVersionMinor = 9
            var authorBuildMajor = 0
            var authorBuildMinor = 0
            var authorType = "Release"
            var authorLangId = "en"
            var authorPartNumber = "000-00000-00"

            var inActivity = false
            var inLap = false
            var inTrackpoint = false
            var inPosition = false
            var inCreator = false
            var inAuthor = false
            var inAttActivityExtension = false
            var inAttLapExtension = false
            var inTpx = false

            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawName = parser.name
                val tag = rawName?.substringAfterLast(':') ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = tag
                        when (tag) {
                            "Activity" -> {
                                inActivity = true
                                var sport = parser.getAttributeValue(null, "Sport")
                                if (sport == null) {
                                    for (i in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(i).equals("Sport", ignoreCase = true)) {
                                            sport = parser.getAttributeValue(i)
                                            break
                                        }
                                    }
                                }
                                currentSport = sport ?: "Unknown"
                                currentActivityId = ""
                                currentActivityNotes = null
                                currentActivityCustomName = null
                                currentActivityCustomDesc = null
                                currentCreator = null
                                currentLaps.clear()
                            }
                            "Lap" -> {
                                inLap = true
                                var startTime = parser.getAttributeValue(null, "StartTime")
                                if (startTime == null) {
                                    for (i in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(i).equals("StartTime", ignoreCase = true)) {
                                            startTime = parser.getAttributeValue(i)
                                            break
                                        }
                                    }
                                }
                                currentLapStartTime = startTime ?: ""
                                currentLapTotalTime = 0.0
                                currentLapDistance = 0.0
                                currentLapMaxSpeed = null
                                currentLapCalories = null
                                currentLapAvgHr = null
                                currentLapMaxHr = null
                                currentLapIntensity = null
                                currentLapCadence = null
                                currentLapTriggerMethod = null
                                currentLapNotes = null
                                currentLapCustomName = null
                                currentLapCustomDesc = null
                                currentTrackpoints.clear()
                            }
                            "Trackpoint" -> {
                                inTrackpoint = true
                                currentTpTime = null
                                currentTpLat = null
                                currentTpLng = null
                                currentTpAlt = null
                                currentTpDist = null
                                currentTpHr = null
                                currentTpCad = null
                                currentTpSpeed = null
                                currentTpWatts = null
                                currentTpRunCadence = null
                            }
                            "Position" -> inPosition = true
                            "Creator" -> inCreator = true
                            "Author" -> inAuthor = true
                            "ActivityExtension" -> inAttActivityExtension = true
                            "LapExtension" -> inAttLapExtension = true
                            "TPX" -> inTpx = true
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            if (inTrackpoint) {
                                when (currentTag) {
                                    "Time" -> currentTpTime = text
                                    "LatitudeDegrees" -> if (inPosition) currentTpLat = text.toDoubleOrNull()
                                    "LongitudeDegrees" -> if (inPosition) currentTpLng = text.toDoubleOrNull()
                                    "AltitudeMeters" -> currentTpAlt = text.toDoubleOrNull()
                                    "DistanceMeters" -> currentTpDist = text.toDoubleOrNull()
                                    "Value" -> text.toIntOrNull()?.let { currentTpHr = it }
                                    "Cadence" -> text.toDoubleOrNull()?.roundToInt()?.let { currentTpCad = it }
                                    "RunCadence" -> text.toDoubleOrNull()?.roundToInt()?.let { currentTpRunCadence = it }
                                    "Watts" -> text.toDoubleOrNull()?.roundToInt()?.let { currentTpWatts = it }
                                    "Speed" -> text.toDoubleOrNull()?.let { currentTpSpeed = it }
                                }
                            } else if (inLap) {
                                if (inAttLapExtension) {
                                    when (currentTag) {
                                        "Name" -> currentLapCustomName = text
                                        "Description" -> currentLapCustomDesc = text
                                    }
                                } else {
                                    when (currentTag) {
                                        "TotalTimeSeconds" -> text.toDoubleOrNull()?.let { currentLapTotalTime = it }
                                        "DistanceMeters" -> text.toDoubleOrNull()?.let { currentLapDistance = it }
                                        "MaximumSpeed" -> currentLapMaxSpeed = text.toDoubleOrNull()
                                        "Calories" -> currentLapCalories = text.toIntOrNull()
                                        "AverageHeartRateBpm" -> {} // Container tag
                                        "MaximumHeartRateBpm" -> {}
                                        "Value" -> {
                                            val hr = text.toIntOrNull()
                                            if (hr != null) {
                                                if (currentLapAvgHr == null) currentLapAvgHr = hr
                                                else if (currentLapMaxHr == null) currentLapMaxHr = hr
                                            }
                                        }
                                        "Intensity" -> currentLapIntensity = text
                                        "Cadence" -> currentLapCadence = text.toDoubleOrNull()?.roundToInt()
                                        "TriggerMethod" -> currentLapTriggerMethod = text
                                        "Notes" -> {
                                            currentLapNotes = text
                                            parseBracketText(text) { n, d ->
                                                if (currentLapCustomName.isNullOrBlank() && n.isNotBlank()) currentLapCustomName = n
                                                if (currentLapCustomDesc.isNullOrBlank() && d != null) currentLapCustomDesc = d
                                            }
                                        }
                                        "Name" -> if (currentLapCustomName.isNullOrBlank()) currentLapCustomName = text
                                        "Description" -> if (currentLapCustomDesc.isNullOrBlank()) currentLapCustomDesc = text
                                    }
                                }
                            } else if (inActivity) {
                                if (inAttActivityExtension) {
                                    when (currentTag) {
                                        "Name" -> currentActivityCustomName = text
                                        "Description" -> currentActivityCustomDesc = text
                                    }
                                } else if (inCreator) {
                                    when (currentTag) {
                                        "Name" -> creatorName = text
                                        "UnitId" -> creatorUnitId = text.toLongOrNull() ?: 0L
                                        "ProductID" -> creatorProductId = text.toIntOrNull() ?: 1
                                        "VersionMajor" -> creatorVersionMajor = text.toIntOrNull() ?: 4
                                        "VersionMinor" -> creatorVersionMinor = text.toIntOrNull() ?: 9
                                        "BuildMajor" -> creatorBuildMajor = text.toIntOrNull() ?: 0
                                        "BuildMinor" -> creatorBuildMinor = text.toIntOrNull() ?: 0
                                    }
                                } else {
                                    when (currentTag) {
                                        "Id" -> currentActivityId = text
                                        "Notes" -> {
                                            currentActivityNotes = text
                                            parseBracketText(text) { n, d ->
                                                if (currentActivityCustomName.isNullOrBlank() && n.isNotBlank()) currentActivityCustomName = n
                                                if (currentActivityCustomDesc.isNullOrBlank() && d != null) currentActivityCustomDesc = d
                                            }
                                        }
                                        "Name" -> if (currentActivityCustomName.isNullOrBlank()) currentActivityCustomName = text
                                        "Description" -> if (currentActivityCustomDesc.isNullOrBlank()) currentActivityCustomDesc = text
                                    }
                                }
                            } else if (inAuthor) {
                                when (currentTag) {
                                    "Name" -> authorName = text
                                    "VersionMajor" -> authorVersionMajor = text.toIntOrNull() ?: 4
                                    "VersionMinor" -> authorVersionMinor = text.toIntOrNull() ?: 9
                                    "BuildMajor" -> authorBuildMajor = text.toIntOrNull() ?: 0
                                    "BuildMinor" -> authorBuildMinor = text.toIntOrNull() ?: 0
                                    "Type" -> authorType = text
                                    "LangID" -> authorLangId = text
                                    "PartNumber" -> authorPartNumber = text
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                        when (tag) {
                            "Position" -> inPosition = false
                            "TPX" -> inTpx = false
                            "ActivityExtension" -> inAttActivityExtension = false
                            "LapExtension" -> inAttLapExtension = false
                            "Trackpoint" -> {
                                if (inTrackpoint) {
                                    val pos = if (currentTpLat != null && currentTpLng != null) {
                                        TcxPosition(currentTpLat!!, currentTpLng!!)
                                    } else null

                                    val ext = if (currentTpSpeed != null || currentTpWatts != null || currentTpRunCadence != null) {
                                        TcxTrackpointExtensions(
                                            speed = currentTpSpeed,
                                            watts = currentTpWatts,
                                            runCadence = currentTpRunCadence
                                        )
                                    } else null

                                    currentTrackpoints.add(
                                        TcxTrackpoint(
                                            time = currentTpTime ?: "",
                                            position = pos,
                                            altitudeMeters = currentTpAlt,
                                            distanceMeters = currentTpDist,
                                            heartRateBpm = currentTpHr,
                                            cadence = currentTpCad,
                                            extensions = ext
                                        )
                                    )
                                    inTrackpoint = false
                                }
                            }
                            "Lap" -> {
                                if (inLap) {
                                    val lapExt = if (currentLapCustomName != null || currentLapCustomDesc != null) {
                                        TcxLapExtensions(
                                            name = currentLapCustomName,
                                            description = currentLapCustomDesc
                                        )
                                    } else null

                                    currentLaps.add(
                                        TcxLap(
                                            startTime = currentLapStartTime,
                                            totalTimeSeconds = currentLapTotalTime,
                                            distanceMeters = currentLapDistance,
                                            maximumSpeed = currentLapMaxSpeed,
                                            calories = currentLapCalories,
                                            averageHeartRateBpm = currentLapAvgHr,
                                            maximumHeartRateBpm = currentLapMaxHr,
                                            intensity = currentLapIntensity,
                                            cadence = currentLapCadence,
                                            triggerMethod = currentLapTriggerMethod,
                                            trackpoints = currentTrackpoints.toList(),
                                            notes = currentLapNotes,
                                            extensions = lapExt
                                        )
                                    )
                                    inLap = false
                                }
                            }
                            "Creator" -> {
                                if (inCreator) {
                                    currentCreator = TcxCreator(
                                        name = creatorName,
                                        unitId = creatorUnitId,
                                        productId = creatorProductId,
                                        versionMajor = creatorVersionMajor,
                                        versionMinor = creatorVersionMinor,
                                        buildMajor = creatorBuildMajor,
                                        buildMinor = creatorBuildMinor
                                    )
                                    inCreator = false
                                }
                            }
                            "Activity" -> {
                                if (inActivity) {
                                    val actExt = if (currentActivityCustomName != null || currentActivityCustomDesc != null) {
                                        TcxActivityExtensions(
                                            customName = currentActivityCustomName,
                                            customDescription = currentActivityCustomDesc
                                        )
                                    } else null

                                    activities.add(
                                        TcxActivity(
                                            sport = currentSport,
                                            id = currentActivityId,
                                            laps = currentLaps.toList(),
                                            notes = currentActivityNotes,
                                            creator = currentCreator,
                                            extensions = actExt
                                        )
                                    )
                                    inActivity = false
                                }
                            }
                            "Author" -> {
                                if (inAuthor) {
                                    author = TcxAuthor(
                                        name = authorName,
                                        versionMajor = authorVersionMajor,
                                        versionMinor = authorVersionMinor,
                                        buildMajor = authorBuildMajor,
                                        buildMinor = authorBuildMinor,
                                        type = authorType,
                                        langId = authorLangId,
                                        partNumber = authorPartNumber
                                    )
                                    inAuthor = false
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            Result.success(
                TcxDocument(
                    activities = activities,
                    author = author
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private inline fun parseBracketText(text: String, onParsed: (name: String, desc: String?) -> Unit) {
        val trimmed = text.trim()
        val match = BRACKET_REGEX.find(trimmed)
        if (match != null) {
            val name = match.groupValues[1].trim()
            val desc = match.groupValues.getOrNull(2)?.trim()
            onParsed(name, if (desc.isNullOrEmpty()) null else desc)
        } else {
            val lines = trimmed.lines()
            if (lines.size > 1) {
                val first = lines.first().trim()
                if (first.length <= 60) {
                    val rem = lines.drop(1).joinToString("\n").trim()
                    onParsed(first, if (rem.isEmpty()) null else rem)
                } else {
                    onParsed("", trimmed)
                }
            } else {
                if (trimmed.length <= 40) {
                    onParsed(trimmed, null)
                } else {
                    onParsed("", trimmed)
                }
            }
        }
    }
}

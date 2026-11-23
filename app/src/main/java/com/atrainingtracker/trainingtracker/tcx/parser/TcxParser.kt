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
import com.atrainingtracker.trainingtracker.tcx.model.TcxCourse
import com.atrainingtracker.trainingtracker.tcx.model.TcxCoursePoint
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
 * Streaming callback interface for [TcxParser.parseStreaming], allowing consumers
 * ([com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParser],
 * [com.atrainingtracker.trainingtracker.routes.TcxCourseParser]) to ingest TCX trackpoints
 * in a single XML pull pass without allocating intermediate trackpoint DTO trees (`REQ-MIG-061.1`).
 */
interface TcxStreamListener {
    fun onActivityStart(activityIndex: Int, sport: String) {}
    fun onActivityEnd(
        activityIndex: Int,
        sport: String,
        id: String,
        notes: String?,
        creator: TcxCreator?,
        extensions: TcxActivityExtensions?
    ) {}

    fun onCourseStart(courseIndex: Int) {}
    fun onCourseEnd(courseIndex: Int, name: String, notes: String?) {}

    fun onLapStart(inCourse: Boolean, ownerIndex: Int, lapIndex: Int, startTime: String) {}
    fun onTrackpoint(
        inCourse: Boolean,
        ownerIndex: Int,
        inLap: Boolean,
        lapIndex: Int,
        time: String,
        lat: Double?,
        lng: Double?,
        altitudeMeters: Double?,
        distanceMeters: Double?,
        heartRateBpm: Int?,
        cadence: Int?,
        speed: Double?,
        watts: Int?,
        runCadence: Int?
    ) {}

    fun onLapEnd(
        inCourse: Boolean,
        ownerIndex: Int,
        lapIndex: Int,
        startTime: String,
        totalTimeSeconds: Double,
        distanceMeters: Double,
        maximumSpeed: Double?,
        calories: Int?,
        averageHeartRateBpm: Int?,
        maximumHeartRateBpm: Int?,
        intensity: String?,
        cadence: Int?,
        triggerMethod: String?,
        notes: String?,
        extensions: TcxLapExtensions?
    ) {}

    fun onCoursePoint(courseIndex: Int, coursePoint: TcxCoursePoint) {}
    fun onAuthor(author: TcxAuthor) {}
}

/**
 * Decoupled streaming XML pull parser producing either single-pass [TcxStreamListener] events
 * (`REQ-MIG-061.1`) or structured [TcxDocument] DTOs (`REQ-DAT-040.3`).
 *
 * Enforces:
 * - REQ-DAT-040.3: Decoupled DTO XML Parser Bridge.
 * - REQ-MIG-037 / REQ-MIG-048.3: BOM and leading whitespace tolerance.
 * - REQ-DAT-012 / REQ-DAT-013: att: extensions and bracket notation fallback parsing.
 * - REQ-MIG-061.1: Zero-intermediate-tree direct streaming consumption.
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
     * Parses a TCX XML stream into a [TcxDocument] by collecting events from [parseStreaming].
     */
    fun parse(inputStream: InputStream): Result<TcxDocument> {
        val activities = mutableListOf<TcxActivity>()
        val courses = mutableListOf<TcxCourse>()
        var parsedAuthor: TcxAuthor? = null

        val currentLaps = mutableListOf<TcxLap>()
        val currentCourseLaps = mutableListOf<TcxLap>()
        val currentCourseTrackpoints = mutableListOf<TcxTrackpoint>()
        val currentCoursePoints = mutableListOf<TcxCoursePoint>()
        val currentTrackpoints = mutableListOf<TcxTrackpoint>()

        val streamResult = parseStreaming(inputStream, object : TcxStreamListener {
            override fun onActivityStart(activityIndex: Int, sport: String) {
                currentLaps.clear()
            }

            override fun onCourseStart(courseIndex: Int) {
                currentCourseLaps.clear()
                currentCourseTrackpoints.clear()
                currentCoursePoints.clear()
            }

            override fun onLapStart(inCourse: Boolean, ownerIndex: Int, lapIndex: Int, startTime: String) {
                currentTrackpoints.clear()
            }

            override fun onTrackpoint(
                inCourse: Boolean,
                ownerIndex: Int,
                inLap: Boolean,
                lapIndex: Int,
                time: String,
                lat: Double?,
                lng: Double?,
                altitudeMeters: Double?,
                distanceMeters: Double?,
                heartRateBpm: Int?,
                cadence: Int?,
                speed: Double?,
                watts: Int?,
                runCadence: Int?
            ) {
                val pos = if (lat != null && lng != null) TcxPosition(lat, lng) else null
                val ext = if (speed != null || watts != null || runCadence != null) {
                    TcxTrackpointExtensions(
                        speed = speed,
                        watts = watts,
                        runCadence = runCadence
                    )
                } else null
                val tp = TcxTrackpoint(
                    time = time,
                    position = pos,
                    altitudeMeters = altitudeMeters,
                    distanceMeters = distanceMeters,
                    heartRateBpm = heartRateBpm,
                    cadence = cadence,
                    extensions = ext
                )
                if (inLap) {
                    currentTrackpoints.add(tp)
                } else if (inCourse) {
                    currentCourseTrackpoints.add(tp)
                }
            }

            override fun onLapEnd(
                inCourse: Boolean,
                ownerIndex: Int,
                lapIndex: Int,
                startTime: String,
                totalTimeSeconds: Double,
                distanceMeters: Double,
                maximumSpeed: Double?,
                calories: Int?,
                averageHeartRateBpm: Int?,
                maximumHeartRateBpm: Int?,
                intensity: String?,
                cadence: Int?,
                triggerMethod: String?,
                notes: String?,
                extensions: TcxLapExtensions?
            ) {
                val lap = TcxLap(
                    startTime = startTime,
                    totalTimeSeconds = totalTimeSeconds,
                    distanceMeters = distanceMeters,
                    maximumSpeed = maximumSpeed,
                    calories = calories,
                    averageHeartRateBpm = averageHeartRateBpm,
                    maximumHeartRateBpm = maximumHeartRateBpm,
                    intensity = intensity,
                    cadence = cadence,
                    triggerMethod = triggerMethod,
                    trackpoints = currentTrackpoints.toList(),
                    notes = notes,
                    extensions = extensions
                )
                if (inCourse) {
                    currentCourseLaps.add(lap)
                } else {
                    currentLaps.add(lap)
                }
            }

            override fun onCoursePoint(courseIndex: Int, coursePoint: TcxCoursePoint) {
                currentCoursePoints.add(coursePoint)
            }

            override fun onCourseEnd(courseIndex: Int, name: String, notes: String?) {
                courses.add(
                    TcxCourse(
                        name = name,
                        laps = currentCourseLaps.toList(),
                        trackpoints = currentCourseTrackpoints.toList(),
                        coursePoints = currentCoursePoints.toList(),
                        notes = notes
                    )
                )
            }

            override fun onActivityEnd(
                activityIndex: Int,
                sport: String,
                id: String,
                notes: String?,
                creator: TcxCreator?,
                extensions: TcxActivityExtensions?
            ) {
                activities.add(
                    TcxActivity(
                        sport = sport,
                        id = id,
                        laps = currentLaps.toList(),
                        notes = notes,
                        creator = creator,
                        extensions = extensions
                    )
                )
            }

            override fun onAuthor(author: TcxAuthor) {
                parsedAuthor = author
            }
        })

        return streamResult.map {
            TcxDocument(
                activities = activities,
                courses = courses,
                author = parsedAuthor
            )
        }
    }

    /**
     * Streams TCX XML text into [listener] without allocating intermediate trackpoint lists.
     */
    fun parseStreaming(xmlContent: String, listener: TcxStreamListener): Result<Unit> {
        return parseStreaming(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)), listener)
    }

    /**
     * Streams a TCX XML [InputStream] into [listener] in a single pull-parser pass (`REQ-MIG-061.1`).
     */
    fun parseStreaming(inputStream: InputStream, listener: TcxStreamListener): Result<Unit> {
        return try {
            val safeStream = stripBomAndLeadingWhitespace(inputStream)
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser()
            parser.setInput(safeStream, "UTF-8")

            var activityIndex = -1
            var courseIndex = -1
            var currentActivityLapIndex = 0
            var currentCourseLapIndex = 0

            var inCourse = false
            var inCoursePoint = false
            var currentCourseName = ""
            var currentCourseNotes: String? = null

            var cpName: String? = null
            var cpTime: String? = null
            var cpLat: Double? = null
            var cpLng: Double? = null
            var cpAlt: Double? = null
            var cpType: String? = null
            var cpNotes: String? = null

            var currentSport = ""
            var currentActivityId = ""
            var currentActivityNotes: String? = null
            var currentActivityCustomName: String? = null
            var currentActivityCustomDesc: String? = null

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
                                activityIndex++
                                currentActivityLapIndex = 0
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
                                listener.onActivityStart(activityIndex, currentSport)
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
                                val ownerIdx = if (inCourse) courseIndex else activityIndex
                                val lapIdx = if (inCourse) currentCourseLapIndex else currentActivityLapIndex
                                listener.onLapStart(inCourse, ownerIdx, lapIdx, currentLapStartTime)
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
                            "Course" -> {
                                inCourse = true
                                courseIndex++
                                currentCourseLapIndex = 0
                                currentCourseName = ""
                                currentCourseNotes = null
                                listener.onCourseStart(courseIndex)
                            }
                            "CoursePoint" -> {
                                inCoursePoint = true
                                cpName = null
                                cpTime = null
                                cpLat = null
                                cpLng = null
                                cpAlt = null
                                cpType = null
                                cpNotes = null
                            }
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
                            } else if (inCoursePoint) {
                                when (currentTag) {
                                    "Name" -> cpName = text
                                    "Time" -> cpTime = text
                                    "LatitudeDegrees" -> if (inPosition) cpLat = text.toDoubleOrNull()
                                    "LongitudeDegrees" -> if (inPosition) cpLng = text.toDoubleOrNull()
                                    "AltitudeMeters" -> cpAlt = text.toDoubleOrNull()
                                    "PointType" -> cpType = text
                                    "Notes" -> cpNotes = text
                                }
                            } else if (inCourse && !inLap) {
                                when (currentTag) {
                                    "Name" -> if (currentCourseName.isEmpty()) currentCourseName = text
                                    "Notes" -> currentCourseNotes = text
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
                            "ActivityExtension" -> inAttActivityExtension = false
                            "LapExtension" -> inAttLapExtension = false
                            "Trackpoint" -> {
                                if (inTrackpoint) {
                                    val ownerIdx = if (inCourse) courseIndex else activityIndex
                                    val lapIdx = if (inCourse) currentCourseLapIndex else currentActivityLapIndex
                                    listener.onTrackpoint(
                                        inCourse = inCourse,
                                        ownerIndex = ownerIdx,
                                        inLap = inLap,
                                        lapIndex = lapIdx,
                                        time = currentTpTime ?: "",
                                        lat = currentTpLat,
                                        lng = currentTpLng,
                                        altitudeMeters = currentTpAlt,
                                        distanceMeters = currentTpDist,
                                        heartRateBpm = currentTpHr,
                                        cadence = currentTpCad,
                                        speed = currentTpSpeed,
                                        watts = currentTpWatts,
                                        runCadence = currentTpRunCadence
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

                                    val ownerIdx = if (inCourse) courseIndex else activityIndex
                                    val lapIdx = if (inCourse) currentCourseLapIndex else currentActivityLapIndex
                                    listener.onLapEnd(
                                        inCourse = inCourse,
                                        ownerIndex = ownerIdx,
                                        lapIndex = lapIdx,
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
                                        notes = currentLapNotes,
                                        extensions = lapExt
                                    )
                                    if (inCourse) currentCourseLapIndex++ else currentActivityLapIndex++
                                    inLap = false
                                }
                            }
                            "CoursePoint" -> {
                                if (inCoursePoint) {
                                    val lat = cpLat
                                    val lng = cpLng
                                    if (lat != null && lng != null) {
                                        listener.onCoursePoint(
                                            courseIndex = courseIndex,
                                            coursePoint = TcxCoursePoint(
                                                name = cpName ?: "",
                                                time = cpTime,
                                                position = TcxPosition(lat, lng),
                                                altitudeMeters = cpAlt,
                                                pointType = cpType,
                                                notes = cpNotes
                                            )
                                        )
                                    }
                                    inCoursePoint = false
                                }
                            }
                            "Course" -> {
                                if (inCourse) {
                                    listener.onCourseEnd(
                                        courseIndex = courseIndex,
                                        name = currentCourseName,
                                        notes = currentCourseNotes
                                    )
                                    inCourse = false
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

                                    listener.onActivityEnd(
                                        activityIndex = activityIndex,
                                        sport = currentSport,
                                        id = currentActivityId,
                                        notes = currentActivityNotes,
                                        creator = currentCreator,
                                        extensions = actExt
                                    )
                                    inActivity = false
                                }
                            }
                            "Author" -> {
                                if (inAuthor) {
                                    listener.onAuthor(
                                        TcxAuthor(
                                            name = authorName,
                                            versionMajor = authorVersionMajor,
                                            versionMinor = authorVersionMinor,
                                            buildMajor = authorBuildMajor,
                                            buildMinor = authorBuildMinor,
                                            type = authorType,
                                            langId = authorLangId,
                                            partNumber = authorPartNumber
                                        )
                                    )
                                    inAuthor = false
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            Result.success(Unit)
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
                onParsed("", trimmed)
            }
        }
    }
}

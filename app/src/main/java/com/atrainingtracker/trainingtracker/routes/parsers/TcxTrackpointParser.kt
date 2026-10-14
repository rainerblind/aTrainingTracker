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

package com.atrainingtracker.trainingtracker.routes.parsers

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Unified streaming XML pull parser for Garmin TCX files (Workouts and Courses).
 *
 * Traceability:
 * - REQ-MIG-037: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36).
 * - TST-MIG-034: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification.
 */
object TcxTrackpointParser {

    fun parse(xmlContent: String): Result<ParsedTcxData> {
        return parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
    }

    fun parse(inputStream: InputStream): Result<ParsedTcxData> {
        return try {
            val safeStream = stripBom(inputStream)
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser()
            parser.setInput(safeStream, "UTF-8")

            val data = ParsedTcxData()
            var currentLap: RawLap? = null

            var inActivity = false
            var inCourse = false
            var inTrack = false
            var inLap = false
            var inTrackpoint = false
            var inCoursePoint = false
            var inPosition = false
            var inCreator = false
            var inAuthor = false

            var currentLat: Double? = null
            var currentLng: Double? = null
            var currentAlt: Double? = null
            var currentDist: Double? = null
            var currentTime: String? = null
            var currentHr: Int? = null
            var currentCad: Int? = null
            var currentWatts: Int? = null
            var currentSpeed: Double? = null

            var cpName: String? = null
            var cpLat: Double? = null
            var cpLng: Double? = null
            var cpAlt: Double? = null
            var cpType: String? = null
            var cpNotes: String? = null
            var cpTime: String? = null

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
                                data.sport = sport
                            }
                            "Course" -> inCourse = true
                            "Track" -> if (inCourse || inLap || inActivity) inTrack = true
                            "Creator" -> inCreator = true
                            "Author" -> inAuthor = true
                            "Lap" -> {
                                inLap = true
                                val rawStartTime = parser.getAttributeValue(null, "StartTime") ?: run {
                                    for (i in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(i).equals("StartTime", ignoreCase = true)) {
                                            return@run parser.getAttributeValue(i)
                                        }
                                    }
                                    null
                                }
                                val lap = RawLap(
                                    lapNr = data.laps.size.toLong(),
                                    startTime = rawStartTime
                                )
                                data.laps.add(lap)
                                currentLap = lap
                            }
                            "Trackpoint" -> {
                                inTrackpoint = true
                                if (currentLap == null) {
                                    val lap = RawLap(lapNr = data.laps.size.toLong())
                                    data.laps.add(lap)
                                    currentLap = lap
                                }
                                currentLat = null
                                currentLng = null
                                currentAlt = null
                                currentDist = null
                                currentTime = null
                                currentHr = null
                                currentCad = null
                                currentWatts = null
                                currentSpeed = null
                            }
                            "CoursePoint" -> {
                                inCoursePoint = true
                                cpName = null
                                cpLat = null
                                cpLng = null
                                cpAlt = null
                                cpType = null
                                cpNotes = null
                                cpTime = null
                            }
                            "Position" -> inPosition = true
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            if (inTrackpoint) {
                                when (currentTag) {
                                    "Time" -> currentTime = text
                                    "LatitudeDegrees" -> if (inPosition) currentLat = text.toDoubleOrNull()
                                    "LongitudeDegrees" -> if (inPosition) currentLng = text.toDoubleOrNull()
                                    "AltitudeMeters" -> currentAlt = text.toDoubleOrNull()
                                    "DistanceMeters" -> currentDist = text.toDoubleOrNull()
                                    "Value" -> {
                                        val hr = text.toIntOrNull()
                                        if (hr != null) currentHr = hr
                                    }
                                    "Cadence" -> {
                                        val cad = text.toIntOrNull()
                                        if (cad != null) currentCad = cad
                                    }
                                    "RunCadence" -> {
                                        val cad = text.toIntOrNull()
                                        if (cad != null && currentCad == null) currentCad = cad
                                    }
                                    "Watts" -> {
                                        val pwr = text.toIntOrNull()
                                        if (pwr != null) currentWatts = pwr
                                    }
                                    "Speed" -> {
                                        val spd = text.toDoubleOrNull()
                                        if (spd != null) currentSpeed = spd
                                    }
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
                            } else if (inLap && currentLap != null) {
                                when (currentTag) {
                                    "TotalTimeSeconds" -> currentLap.totalTimeSeconds = text.toDoubleOrNull()
                                    "DistanceMeters" -> currentLap.distanceMeters = text.toDoubleOrNull()
                                    "MaximumSpeed" -> currentLap.maxSpeed = text.toDoubleOrNull()
                                    "Calories" -> currentLap.calories = text.toIntOrNull()
                                    "Value" -> {
                                        val hr = text.toIntOrNull()
                                        if (hr != null && currentLap.avgHeartRate == null) {
                                            currentLap.avgHeartRate = hr
                                        }
                                    }
                                    "Name" -> if (currentLap.name.isNullOrBlank()) currentLap.name = text
                                    "Notes" -> parseNotesField(text, currentLap, data)
                                    "Description" -> if (currentLap.description.isNullOrBlank()) currentLap.description = text
                                }
                            } else {
                                when (currentTag) {
                                    "Name" -> {
                                        if (data.name.isNullOrBlank() && !inCreator && !inAuthor) {
                                            data.name = text
                                        }
                                    }
                                    "Notes" -> parseNotesField(text, null, data)
                                    "Description" -> {
                                        if (data.notes.isNullOrBlank() && !inCreator && !inAuthor) {
                                            data.notes = text
                                        }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                        when (tag) {
                            "Position" -> inPosition = false
                            "Trackpoint" -> {
                                if (inTrackpoint) {
                                    if (currentLat != null && currentLng != null) {
                                        val pt = RawTrackpoint(
                                            lat = currentLat,
                                            lng = currentLng,
                                            altitude = currentAlt,
                                            distanceMeters = currentDist,
                                            time = currentTime,
                                            hr = currentHr,
                                            cadence = currentCad,
                                            power = currentWatts,
                                            speedMps = currentSpeed
                                        )
                                        currentLap?.trackpoints?.add(pt)
                                    }
                                    inTrackpoint = false
                                }
                            }
                            "CoursePoint" -> {
                                if (inCoursePoint) {
                                    if (cpLat != null && cpLng != null) {
                                        data.coursePoints.add(
                                            RawCoursePoint(
                                                name = cpName ?: "",
                                                lat = cpLat,
                                                lng = cpLng,
                                                altitude = cpAlt,
                                                type = cpType,
                                                notes = cpNotes,
                                                time = cpTime
                                            )
                                        )
                                    }
                                    inCoursePoint = false
                                }
                            }
                            "Lap" -> {
                                inLap = false
                                currentLap = null
                            }
                            "Track" -> inTrack = false
                            "Course" -> inCourse = false
                            "Activity" -> inActivity = false
                            "Creator" -> inCreator = false
                            "Author" -> inAuthor = false
                        }
                    }
                }
                eventType = parser.next()
            }

            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseNotesField(text: String, lap: RawLap?, data: ParsedTcxData) {
        val trimmed = text.trim()
        val bracketMatch = Regex("""^\[(.*?)\](?:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL).find(trimmed)
        if (bracketMatch != null) {
            val extractedName = bracketMatch.groupValues[1].trim()
            val extractedDesc = bracketMatch.groupValues.getOrNull(2)?.trim()
            if (lap != null) {
                if (lap.name.isNullOrBlank() && extractedName.isNotEmpty()) lap.name = extractedName
                if (lap.description.isNullOrBlank() && !extractedDesc.isNullOrEmpty()) lap.description = extractedDesc
            } else {
                if (data.name.isNullOrBlank() && extractedName.isNotEmpty()) data.name = extractedName
                if (data.notes.isNullOrBlank() && !extractedDesc.isNullOrEmpty()) data.notes = extractedDesc
            }
        } else {
            val lines = trimmed.lines()
            if (lines.size > 1) {
                val firstLine = lines.first().trim()
                    if (lap != null) {
                        if (lap.name.isNullOrBlank() && firstLine.length <= 40) {
                            lap.name = firstLine
                            if (lap.description.isNullOrBlank()) {
                                val rem = lines.drop(1).joinToString("\n").trim()
                                if (rem.isNotEmpty()) lap.description = rem
                            }
                        } else if (lap.description.isNullOrBlank()) {
                            lap.description = trimmed
                        }
                    } else {
                        if (data.name.isNullOrBlank() && firstLine.length <= 60) {
                            data.name = firstLine
                            if (data.notes.isNullOrBlank()) {
                                val rem = lines.drop(1).joinToString("\n").trim()
                                if (rem.isNotEmpty()) data.notes = rem
                            }
                        } else if (data.notes.isNullOrBlank()) {
                            data.notes = trimmed
                        }
                    }
            } else {
                if (lap != null) {
                    if (trimmed.length <= 40 && lap.name.isNullOrBlank()) lap.name = trimmed
                    else if (lap.description.isNullOrBlank()) lap.description = trimmed
                } else {
                    if (data.notes.isNullOrBlank()) data.notes = trimmed
                }
            }
        }
    }
}

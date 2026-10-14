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
 * Unified streaming XML pull parser for GPX files (Workouts, Tracks, Routes, and Waypoints).
 *
 * Traceability:
 * - REQ-MIG-037: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36).
 * - TST-MIG-034: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification.
 */
object GpxTrackpointParser {

    fun parse(xmlContent: String): Result<ParsedGpxData> {
        return parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
    }

    fun parse(inputStream: InputStream): Result<ParsedGpxData> {
        return try {
            val safeStream = stripBom(inputStream)
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser()
            parser.setInput(safeStream, "UTF-8")

            val data = ParsedGpxData()
            var currentLap: RawLap? = null

            var inMetadata = false
            var inTrk = false
            var inRte = false
            var inTrackpoint = false
            var inWaypoint = false

            var currentLat: Double? = null
            var currentLng: Double? = null
            var currentAlt: Double? = null
            var currentTime: String? = null
            var currentSpeed: Double? = null
            var currentHr: Int? = null
            var currentCad: Int? = null
            var currentTemp: Double? = null
            var currentPower: Int? = null

            var wptLat: Double? = null
            var wptLng: Double? = null
            var wptAlt: Double? = null
            var wptName: String? = null
            var wptDesc: String? = null
            var wptSym: String? = null
            var wptType: String? = null
            var wptTime: String? = null

            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawName = parser.name
                val tag = rawName?.substringAfterLast(':') ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = tag
                        when (tag) {
                            "metadata" -> inMetadata = true
                            "trk" -> inTrk = true
                            "rte" -> inRte = true
                            "trkseg" -> {
                                val lap = RawLap(lapNr = data.laps.size.toLong())
                                data.laps.add(lap)
                                currentLap = lap
                            }
                            "trkpt", "rtept" -> {
                                inTrackpoint = true
                                if (currentLap == null) {
                                    val lap = RawLap(lapNr = data.laps.size.toLong())
                                    data.laps.add(lap)
                                    currentLap = lap
                                }

                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                    ?: parser.getAttributeValue("", "lat")?.toDoubleOrNull()
                                currentLng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                    ?: parser.getAttributeValue("", "lon")?.toDoubleOrNull()

                                if (currentLat == null || currentLng == null) {
                                    for (i in 0 until parser.attributeCount) {
                                        val attrName = parser.getAttributeName(i)
                                        if (attrName.equals("lat", ignoreCase = true)) {
                                            currentLat = parser.getAttributeValue(i).toDoubleOrNull()
                                        } else if (attrName.equals("lon", ignoreCase = true)) {
                                            currentLng = parser.getAttributeValue(i).toDoubleOrNull()
                                        }
                                    }
                                }

                                currentAlt = null
                                currentTime = null
                                currentSpeed = null
                                currentHr = null
                                currentCad = null
                                currentTemp = null
                                currentPower = null
                            }
                            "wpt" -> {
                                inWaypoint = true
                                wptLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                    ?: parser.getAttributeValue("", "lat")?.toDoubleOrNull()
                                wptLng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                    ?: parser.getAttributeValue("", "lon")?.toDoubleOrNull()

                                if (wptLat == null || wptLng == null) {
                                    for (i in 0 until parser.attributeCount) {
                                        val attrName = parser.getAttributeName(i)
                                        if (attrName.equals("lat", ignoreCase = true)) {
                                            wptLat = parser.getAttributeValue(i).toDoubleOrNull()
                                        } else if (attrName.equals("lon", ignoreCase = true)) {
                                            wptLng = parser.getAttributeValue(i).toDoubleOrNull()
                                        }
                                    }
                                }

                                wptAlt = null
                                wptName = null
                                wptDesc = null
                                wptSym = null
                                wptType = null
                                wptTime = null
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            if (inTrackpoint) {
                                when (currentTag) {
                                    "ele" -> currentAlt = text.toDoubleOrNull()
                                    "time" -> currentTime = text
                                    "speed" -> currentSpeed = text.toDoubleOrNull()
                                    "hr", "heartrate" -> {
                                        val hr = text.toIntOrNull()
                                        if (hr != null) currentHr = hr
                                    }
                                    "cad", "cadence" -> {
                                        val cad = text.toIntOrNull()
                                        if (cad != null) currentCad = cad
                                    }
                                    "atemp", "temp" -> currentTemp = text.toDoubleOrNull()
                                    "power", "watts" -> {
                                        val pwr = text.toIntOrNull()
                                        if (pwr != null) currentPower = pwr
                                    }
                                }
                            } else if (inWaypoint) {
                                when (currentTag) {
                                    "name" -> wptName = text
                                    "desc", "description" -> wptDesc = text
                                    "cmt" -> if (wptDesc.isNullOrEmpty()) wptDesc = text
                                    "sym" -> wptSym = text
                                    "type" -> wptType = text
                                    "ele" -> wptAlt = text.toDoubleOrNull()
                                    "time" -> wptTime = text
                                }
                            } else if (inTrk) {
                                when (currentTag) {
                                    "name" -> {
                                        if (data.trackName.isNullOrBlank()) {
                                            data.trackName = text
                                        }
                                    }
                                    "desc", "description" -> {
                                        if (data.trackDesc.isNullOrBlank()) {
                                            data.trackDesc = text
                                        }
                                    }
                                    "type", "activity", "sport" -> {
                                        if (data.sport.isNullOrBlank()) {
                                            data.sport = text
                                        }
                                    }
                                }
                            } else {
                                when (currentTag) {
                                    "name" -> {
                                        if (data.name.isNullOrBlank()) {
                                            data.name = text
                                        }
                                    }
                                    "desc", "description" -> {
                                        if (data.description.isNullOrBlank()) {
                                            data.description = text
                                        }
                                    }
                                    "type", "activity", "sport" -> {
                                        if (data.sport.isNullOrBlank()) {
                                            data.sport = text
                                        }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                        when (tag) {
                            "trkpt", "rtept" -> {
                                if (inTrackpoint) {
                                    if (currentLat != null && currentLng != null) {
                                        val pt = RawTrackpoint(
                                            lat = currentLat,
                                            lng = currentLng,
                                            altitude = currentAlt,
                                            time = currentTime,
                                            speedMps = currentSpeed,
                                            hr = currentHr,
                                            cadence = currentCad,
                                            temperature = currentTemp,
                                            power = currentPower
                                        )
                                        currentLap?.trackpoints?.add(pt)
                                    }
                                    inTrackpoint = false
                                }
                            }
                            "wpt" -> {
                                if (inWaypoint) {
                                    if (wptLat != null && wptLng != null) {
                                        data.waypoints.add(
                                            RawCoursePoint(
                                                name = wptName ?: "",
                                                lat = wptLat,
                                                lng = wptLng,
                                                altitude = wptAlt,
                                                notes = wptDesc,
                                                sym = wptSym,
                                                type = wptType,
                                                time = wptTime
                                            )
                                        )
                                    }
                                    inWaypoint = false
                                }
                            }
                            "trkseg" -> {
                                currentLap = null
                            }
                            "trk" -> inTrk = false
                            "rte" -> inRte = false
                            "metadata" -> inMetadata = false
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
}

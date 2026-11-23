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
 * Streaming callback interface for [GpxTrackpointParser.parseStreaming], enabling single-pass
 * GPX ingestion without materializing intermediate [RawTrackpoint] trees (`REQ-MIG-061.1`).
 */
interface GpxStreamListener {
    fun onLapStart(lapNr: Long) {}
    fun onTrackpoint(
        lapNr: Long,
        lat: Double,
        lng: Double,
        altitude: Double?,
        time: String?,
        speedMps: Double?,
        hr: Int?,
        cadence: Int?,
        temperature: Double?,
        power: Int?
    ) {}
    fun onLapEnd(lapNr: Long) {}
    fun onWaypoint(waypoint: RawCoursePoint) {}
    fun onDocumentMetadata(
        name: String?,
        description: String?,
        trackName: String?,
        trackDesc: String?,
        sport: String?
    ) {}
}

/**
 * Unified streaming XML pull parser for GPX files (Workouts, Tracks, Routes, and Waypoints).
 *
 * Traceability:
 * - REQ-MIG-037: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36).
 * - REQ-MIG-061.1: Zero-intermediate-tree direct streaming callbacks via [parseStreaming].
 * - TST-MIG-034 / TST-MIG-061.1: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification.
 */
object GpxTrackpointParser {

    fun parse(xmlContent: String): Result<ParsedGpxData> {
        return parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
    }

    fun parse(inputStream: InputStream): Result<ParsedGpxData> {
        val data = ParsedGpxData()
        var currentLap: RawLap? = null

        val result = parseStreaming(inputStream, object : GpxStreamListener {
            override fun onLapStart(lapNr: Long) {
                val lap = RawLap(lapNr = lapNr)
                data.laps.add(lap)
                currentLap = lap
            }

            override fun onTrackpoint(
                lapNr: Long,
                lat: Double,
                lng: Double,
                altitude: Double?,
                time: String?,
                speedMps: Double?,
                hr: Int?,
                cadence: Int?,
                temperature: Double?,
                power: Int?
            ) {
                currentLap?.trackpoints?.add(
                    RawTrackpoint(
                        lat = lat,
                        lng = lng,
                        altitude = altitude,
                        time = time,
                        speedMps = speedMps,
                        hr = hr,
                        cadence = cadence,
                        temperature = temperature,
                        power = power
                    )
                )
            }

            override fun onLapEnd(lapNr: Long) {
                currentLap = null
            }

            override fun onWaypoint(waypoint: RawCoursePoint) {
                data.waypoints.add(waypoint)
            }

            override fun onDocumentMetadata(
                name: String?,
                description: String?,
                trackName: String?,
                trackDesc: String?,
                sport: String?
            ) {
                data.name = name
                data.description = description
                data.trackName = trackName
                data.trackDesc = trackDesc
                data.sport = sport
            }
        })

        return result.map { data }
    }

    fun parseStreaming(xmlContent: String, listener: GpxStreamListener): Result<Unit> {
        return parseStreaming(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)), listener)
    }

    fun parseStreaming(inputStream: InputStream, listener: GpxStreamListener): Result<Unit> {
        return try {
            val safeStream = stripBom(inputStream)
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser()
            parser.setInput(safeStream, "UTF-8")

            var docName: String? = null
            var docDescription: String? = null
            var trackName: String? = null
            var trackDesc: String? = null
            var sport: String? = null

            var nextLapNr = 0L
            var activeLapNr: Long? = null

            var inTrk = false
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
                            "trk" -> inTrk = true
                            "trkseg" -> {
                                val lapNr = nextLapNr++
                                activeLapNr = lapNr
                                listener.onLapStart(lapNr)
                            }
                            "trkpt", "rtept" -> {
                                inTrackpoint = true
                                if (activeLapNr == null) {
                                    val lapNr = nextLapNr++
                                    activeLapNr = lapNr
                                    listener.onLapStart(lapNr)
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
                                    "name" -> if (trackName.isNullOrBlank()) trackName = text
                                    "desc", "description" -> if (trackDesc.isNullOrBlank()) trackDesc = text
                                    "type", "activity", "sport" -> if (sport.isNullOrBlank()) sport = text
                                }
                            } else {
                                when (currentTag) {
                                    "name" -> if (docName.isNullOrBlank()) docName = text
                                    "desc", "description" -> if (docDescription.isNullOrBlank()) docDescription = text
                                    "type", "activity", "sport" -> if (sport.isNullOrBlank()) sport = text
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                        when (tag) {
                            "trkpt", "rtept" -> {
                                if (inTrackpoint) {
                                    val lat = currentLat
                                    val lng = currentLng
                                    val lapNr = activeLapNr
                                    if (lat != null && lng != null && lapNr != null) {
                                        listener.onTrackpoint(
                                            lapNr = lapNr,
                                            lat = lat,
                                            lng = lng,
                                            altitude = currentAlt,
                                            time = currentTime,
                                            speedMps = currentSpeed,
                                            hr = currentHr,
                                            cadence = currentCad,
                                            temperature = currentTemp,
                                            power = currentPower
                                        )
                                    }
                                    inTrackpoint = false
                                }
                            }
                            "wpt" -> {
                                if (inWaypoint) {
                                    val lat = wptLat
                                    val lng = wptLng
                                    if (lat != null && lng != null) {
                                        listener.onWaypoint(
                                            RawCoursePoint(
                                                name = wptName ?: "",
                                                lat = lat,
                                                lng = lng,
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
                                activeLapNr?.let { listener.onLapEnd(it) }
                                activeLapNr = null
                            }
                            "trk" -> inTrk = false
                        }
                    }
                }
                eventType = parser.next()
            }

            activeLapNr?.let { listener.onLapEnd(it) }
            listener.onDocumentMetadata(
                name = docName,
                description = docDescription,
                trackName = trackName,
                trackDesc = trackDesc,
                sport = sport
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

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

/**
 * Immutable DTO representing a raw GPS trackpoint and associated telemetry
 * extracted from TCX or GPX XML files.
 *
 * Traceability:
 * - REQ-MIG-037: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36).
 * - TST-MIG-034: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification.
 */
data class RawTrackpoint(
    val lat: Double,
    val lng: Double,
    val altitude: Double? = null,
    val distanceMeters: Double? = null,
    val time: String? = null,
    val hr: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val speedMps: Double? = null,
    val temperature: Double? = null
)

/**
 * Immutable DTO representing a course point (TCX) or waypoint (GPX).
 */
data class RawCoursePoint(
    val name: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double? = null,
    val type: String? = null,
    val notes: String? = null,
    val time: String? = null,
    val sym: String? = null
)

/**
 * DTO representing an activity lap or track segment (trkseg).
 */
data class RawLap(
    val lapNr: Long,
    var startTime: String? = null,
    var totalTimeSeconds: Double? = null,
    var distanceMeters: Double? = null,
    var maxSpeed: Double? = null,
    var calories: Int? = null,
    var avgHeartRate: Int? = null,
    var name: String? = null,
    var description: String? = null,
    val trackpoints: MutableList<RawTrackpoint> = mutableListOf()
)

/**
 * Aggregate DTO representing the parsed contents of a TCX file (workout activity or course).
 */
data class ParsedTcxData(
    var name: String? = null,
    var notes: String? = null,
    var sport: String? = null,
    var creator: String? = null,
    val laps: MutableList<RawLap> = mutableListOf(),
    val coursePoints: MutableList<RawCoursePoint> = mutableListOf()
)

/**
 * Aggregate DTO representing the parsed contents of a GPX file (workout track or route).
 */
data class ParsedGpxData(
    var name: String? = null,
    var description: String? = null,
    var trackName: String? = null,
    var trackDesc: String? = null,
    var sport: String? = null,
    val laps: MutableList<RawLap> = mutableListOf(),
    val waypoints: MutableList<RawCoursePoint> = mutableListOf()
)

/**
 * Wraps an [java.io.InputStream] with a [java.io.PushbackInputStream] to detect and strip UTF-8 Byte Order Marks (BOM),
 * preventing XmlPullParserException ("PI must not start with xml") when XML files contain a BOM before <?xml.
 */
fun stripBom(stream: java.io.InputStream): java.io.InputStream {
    val pushback = java.io.PushbackInputStream(stream, 4)
    val bom = ByteArray(3)
    val read = pushback.read(bom, 0, 3)
    if (read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
        // UTF-8 BOM detected and consumed (stripped)
    } else if (read > 0) {
        pushback.unread(bom, 0, read)
    }
    return pushback
}


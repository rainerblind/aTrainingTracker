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

package com.atrainingtracker.trainingtracker.routes

import android.content.Context
import android.location.Location
import android.net.Uri
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Streaming parser for Garmin TCX courses (<Courses><Course>) extracting trackpoints,
 * cumulative distance, elevation, and <CoursePoint> elements.
 *
 * Traceability:
 * - REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.
 * - TST-MAP-028: TCX course and course point extraction verification.
 */
object TcxCourseParser {

    /**
     * Parses a TCX Course from an [InputStream].
     */
    fun parse(inputStream: InputStream, defaultExternalId: String = "tcx_course"): Result<RouteImportResult> {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var courseName = ""
            val trackPoints = mutableListOf<PathPoint>()
            val rawCoursePoints = mutableListOf<RouteWaypoint>()

            var inCourse = false
            var inTrack = false
            var inTrackpoint = false
            var inPosition = false
            var inCoursePoint = false

            var currentLat: Double? = null
            var currentLng: Double? = null
            var currentAlt: Double? = null
            var currentDist: Double? = null

            var cpName: String? = null
            var cpLat: Double? = null
            var cpLng: Double? = null
            var cpAlt: Double? = null
            var cpType: String? = null
            var cpNotes: String? = null

            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawName = parser.name
                val tag = rawName?.substringAfterLast(':') ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = tag
                        when (tag) {
                            "Course" -> inCourse = true
                            "Track" -> if (inCourse) inTrack = true
                            "Trackpoint" -> {
                                if (inTrack) {
                                    inTrackpoint = true
                                    currentLat = null
                                    currentLng = null
                                    currentAlt = null
                                    currentDist = null
                                }
                            }
                            "CoursePoint" -> {
                                if (inCourse) {
                                    inCoursePoint = true
                                    cpName = null
                                    cpLat = null
                                    cpLng = null
                                    cpAlt = null
                                    cpType = null
                                    cpNotes = null
                                }
                            }
                            "Position" -> inPosition = true
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            if (inCourse && !inTrack && !inCoursePoint && currentTag == "Name" && courseName.isEmpty()) {
                                courseName = text
                            } else if (inTrackpoint) {
                                when (currentTag) {
                                    "LatitudeDegrees" -> if (inPosition) currentLat = text.toDoubleOrNull()
                                    "LongitudeDegrees" -> if (inPosition) currentLng = text.toDoubleOrNull()
                                    "AltitudeMeters" -> currentAlt = text.toDoubleOrNull()
                                    "DistanceMeters" -> currentDist = text.toDoubleOrNull()
                                }
                            } else if (inCoursePoint) {
                                when (currentTag) {
                                    "Name" -> cpName = text
                                    "LatitudeDegrees" -> if (inPosition) cpLat = text.toDoubleOrNull()
                                    "LongitudeDegrees" -> if (inPosition) cpLng = text.toDoubleOrNull()
                                    "AltitudeMeters" -> cpAlt = text.toDoubleOrNull()
                                    "PointType" -> cpType = text
                                    "Notes" -> cpNotes = text
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
                                        trackPoints.add(
                                            PathPoint(
                                                latLng = LatLng(currentLat, currentLng),
                                                altitude = currentAlt ?: 0.0,
                                                distance = currentDist ?: 0.0
                                            )
                                        )
                                    }
                                    inTrackpoint = false
                                }
                            }
                            "CoursePoint" -> {
                                if (inCoursePoint) {
                                    if (cpLat != null && cpLng != null) {
                                        rawCoursePoints.add(
                                            RouteWaypoint(
                                                latLng = LatLng(cpLat, cpLng),
                                                altitude = cpAlt ?: 0.0,
                                                name = cpName ?: "",
                                                description = cpNotes ?: "",
                                                type = WaypointType.fromTcx(cpType)
                                            )
                                        )
                                    }
                                    inCoursePoint = false
                                }
                            }
                            "Track" -> inTrack = false
                            "Course" -> inCourse = false
                        }
                    }
                }
                eventType = parser.next()
            }

            if (trackPoints.isEmpty()) {
                return Result.failure(IllegalArgumentException("No valid trackpoints found in TCX course"))
            }

            // Calculate / normalize cumulative distances if missing or zero
            val needsDistanceCalc = trackPoints.size > 1 && trackPoints.last().distance <= 0.0
            if (needsDistanceCalc) {
                var totalDist = 0.0
                for (i in 1 until trackPoints.size) {
                    val p1 = trackPoints[i - 1].latLng
                    val p2 = trackPoints[i].latLng
                    val res = FloatArray(1)
                    Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res)
                    totalDist += res[0]
                    trackPoints[i] = trackPoints[i].copy(distance = totalDist)
                }
            }

            // Project waypoints along track polyline
            val projectedWaypoints = WaypointDistanceCalculator.projectWaypoints(rawCoursePoints, trackPoints)

            val summary = RouteSummary(
                id = 0,
                externalId = defaultExternalId,
                name = if (courseName.isNotBlank()) courseName else "TCX Course",
                description = "",
                isSelected = false,
                distance = trackPoints.last().distance,
                elevationGain = calculateElevationGain(trackPoints),
                bSportType = BSportType.UNKNOWN,
                source = RouteSource.LOCAL_GPX
            )

            Result.success(RouteImportResult(summary, trackPoints, projectedWaypoints))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses TCX XML content from a [String].
     */
    fun parse(xmlContent: String, defaultExternalId: String = "tcx_course"): Result<RouteImportResult> {
        return parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)), defaultExternalId)
    }

    /**
     * Parses TCX course from a content [Uri].
     */
    fun parse(context: Context, uri: Uri): Result<RouteImportResult> {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Unable to open input stream for URI: $uri"))
            stream.use { parse(it, uri.lastPathSegment ?: "tcx_course") }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateElevationGain(points: List<PathPoint>): Double {
        var gain = 0.0
        for (i in 1 until points.size) {
            val diff = points[i].altitude - points[i - 1].altitude
            if (diff > 0) gain += diff
        }
        return gain
    }
}

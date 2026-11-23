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
import com.atrainingtracker.trainingtracker.geo.GeoMath
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivityExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxCoursePoint
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator
import com.atrainingtracker.trainingtracker.tcx.parser.TcxParser
import com.atrainingtracker.trainingtracker.tcx.parser.TcxStreamListener
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Streaming parser for Garmin TCX courses (`<Courses><Course>`, with `<Activity>` fallback)
 * consuming [TcxParser.parseStreaming] directly without intermediate trackpoint DTO trees.
 *
 * Traceability:
 * - REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.
 * - REQ-MIG-037 / REQ-MIG-061.1: Single-pass streaming TCX ingestion without `TcxTrackpointParser`.
 * - TST-MAP-028 / TST-MIG-061.1: TCX course and course point extraction verification.
 */
object TcxCourseParser {

    /**
     * Parses a TCX Course from an [InputStream] directly via [TcxParser.parseStreaming].
     */
    fun parse(inputStream: InputStream, defaultExternalId: String = "tcx_course"): Result<RouteImportResult> {
        return try {
            var hasCourse = false
            var courseName: String? = null
            var courseNotes: String? = null
            val courseLapPoints = mutableListOf<PathPoint>()
            val courseDirectPoints = mutableListOf<PathPoint>()
            val rawCoursePoints = mutableListOf<RouteWaypoint>()

            var hasActivity = false
            var activityName: String? = null
            var activityNotes: String? = null
            val activityPoints = mutableListOf<PathPoint>()

            val streamResult = TcxParser.parseStreaming(inputStream, object : TcxStreamListener {
                override fun onCourseStart(courseIndex: Int) {
                    if (courseIndex == 0) {
                        hasCourse = true
                    }
                }

                override fun onActivityStart(activityIndex: Int, sport: String) {
                    if (activityIndex == 0) {
                        hasActivity = true
                    }
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
                    if (lat == null || lng == null || ownerIndex != 0) return
                    val pathPoint = PathPoint(
                        latLng = LatLng(lat, lng),
                        altitude = altitudeMeters ?: 0.0,
                        distance = distanceMeters ?: 0.0
                    )
                    if (inCourse) {
                        if (inLap) {
                            courseLapPoints.add(pathPoint)
                        } else {
                            courseDirectPoints.add(pathPoint)
                        }
                    } else if (inLap) {
                        activityPoints.add(pathPoint)
                    }
                }

                override fun onCoursePoint(courseIndex: Int, coursePoint: TcxCoursePoint) {
                    if (courseIndex != 0) return
                    rawCoursePoints.add(
                        RouteWaypoint(
                            latLng = LatLng(coursePoint.position.latitudeDegrees, coursePoint.position.longitudeDegrees),
                            altitude = coursePoint.altitudeMeters ?: 0.0,
                            name = coursePoint.name,
                            description = coursePoint.notes ?: "",
                            type = WaypointType.fromTcx(coursePoint.pointType)
                        )
                    )
                }

                override fun onCourseEnd(courseIndex: Int, name: String, notes: String?) {
                    if (courseIndex != 0) return
                    courseName = name.takeIf { it.isNotBlank() }
                    courseNotes = notes
                }

                override fun onActivityEnd(
                    activityIndex: Int,
                    sport: String,
                    id: String,
                    notes: String?,
                    creator: TcxCreator?,
                    extensions: TcxActivityExtensions?
                ) {
                    if (activityIndex != 0) return
                    activityName = extensions?.customName
                    activityNotes = extensions?.customDescription ?: stripBracketedPrefix(notes, activityName)
                }
            })

            if (streamResult.isFailure) {
                return Result.failure(streamResult.exceptionOrNull() ?: IllegalArgumentException("Failed to parse TCX course"))
            }

            val trackPoints: MutableList<PathPoint>
            val resolvedName: String?
            val resolvedNotes: String?
            val resolvedCoursePoints: List<RouteWaypoint>

            if (hasCourse) {
                trackPoints = if (courseLapPoints.isNotEmpty()) courseLapPoints else courseDirectPoints
                resolvedName = courseName
                resolvedNotes = courseNotes
                resolvedCoursePoints = rawCoursePoints
            } else if (hasActivity) {
                trackPoints = activityPoints
                resolvedName = activityName
                resolvedNotes = activityNotes
                resolvedCoursePoints = emptyList()
            } else {
                trackPoints = mutableListOf()
                resolvedName = null
                resolvedNotes = null
                resolvedCoursePoints = emptyList()
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
            val projectedWaypoints = WaypointDistanceCalculator.projectWaypoints(resolvedCoursePoints, trackPoints)

            val finalName = resolvedName ?: ""
            val summary = RouteSummary(
                id = 0,
                externalId = defaultExternalId,
                name = if (finalName.isNotBlank()) finalName else "TCX Course",
                description = resolvedNotes ?: "",
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
                ?: return Result.failure(IllegalArgumentException("Unable to open input stream for URI: "))
            stream.use { parse(it, uri.lastPathSegment ?: "tcx_course") }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun stripBracketedPrefix(notes: String?, name: String?): String? {
        val raw = notes?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!name.isNullOrBlank()) {
            val prefix = "[${name.trim()}]"
            if (raw.startsWith(prefix)) {
                return raw.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
            }
        }
        return raw
    }

    private fun calculateElevationGain(points: List<PathPoint>): Double {
        return GeoMath.calculatePathElevationGain(points)
    }
}
